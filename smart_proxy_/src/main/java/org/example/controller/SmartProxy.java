package org.example.controller;


import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.github.cdimascio.dotenv.Dotenv;
import org.example.repo.CacheDatabase;
import org.example.repo.RedisDB;
import org.example.models.DwResponse;
import org.example.repo.ResponseCache;
import org.example.service.Forwarder;
import org.example.service.RoundRobinForwarder;
import redis.clients.jedis.JedisPooled;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;



public class SmartProxy implements Proxy {
    private static final Logger logger = Logger.getLogger(SmartProxy.class.getName());

    private final Dotenv dotenv;
    private final Forwarder forwarder;


    public SmartProxy(){
        // Init dotenv
        dotenv = Dotenv.configure().filename(".env").load();

        // Init DW
        List<String> dataWarehouses = Stream.iterate(0, i -> dotenv.get("DW_" + i) != null, i -> i + 1)
                .map(i -> dotenv.get("DW_" + i))
                .toList();

        if (dataWarehouses.isEmpty()) {
            logger.severe("No DW in .env");
            System.exit(1);
        }

        // Init Forwarder
        this.forwarder = new RoundRobinForwarder(dataWarehouses);
    }

    /*
        Main method to start smart proxy
    */
    public void run() {
        JedisPooled clientsDb;
        ResponseCache cache;
        try {
            clientsDb = initClientsDB();
            cache = initCache();
        } catch (Exception e) {
            logger.severe("Can't connect to Redis: " + e.getMessage());
            System.exit(1);
            return;
        }

        // Transmitting request to DW
        this.initProxyServer(clientsDb, this.forwarder, cache);
    }

    private CacheDatabase redisConfig(int db) {
        return new RedisDB(
                dotenv.get("REDIS_ADDRESS", "localhost"),
                Integer.parseInt(dotenv.get("REDIS_PORT", "6379")),
                dotenv.get("REDIS_PASSWORD", ""),
                db);
    }

    private JedisPooled initClientsDB() {
        int db = Integer.parseInt(dotenv.get("REDIS_CLIENT_DB", "0"));
        return redisConfig(db).connect();
    }

    private ResponseCache initCache() {
        int db = Integer.parseInt(dotenv.get("REDIS_REQUEST_DB", "1"));
        long ttl = Long.parseLong(dotenv.get("CACHE_TTL_SECONDS", "30"));
        return new ResponseCache(redisConfig(db).connect(), ttl);
    }


    private void initProxyServer(JedisPooled clientsDb, Forwarder forwarder, ResponseCache cache) {
        try {
            int smartProxyPort = Integer.parseInt(dotenv.get("SMART_PROXY_PORT", "8080"));
            HttpServer server = HttpServer.create(new InetSocketAddress(smartProxyPort), 0);

            server.createContext("/", exchange -> {
                String clientIP = exchange.getRemoteAddress().getAddress().getHostAddress();
                try {
                    clientsDb.setex("client:" + clientIP, 300, String.valueOf(System.currentTimeMillis()));
                    logger.info("Подключение клиента " + clientIP + " зафиксировано");
                } catch (Exception e) {
                    logger.log(Level.WARNING, "Ошибка сохранения клиента " + clientIP, e);
                }

                String method = exchange.getRequestMethod();
                String accept = exchange.getRequestHeaders().getFirst("Accept");
                String key = exchange.getRequestURI() + "|" + accept;

                DwResponse result = null;
                boolean fromCache = false;

                if (method.equals("GET")) {
                    result = cache.get(key);
                    fromCache = (result != null);
                }

                if (result == null) {
                    result = forwarder.forward(exchange);

                    if (method.equals("GET") && result.status() == 200) {
                        cache.put(key, result);
                    } else if (!method.equals("GET")) {
                        cache.clear();
                    }
                }

                exchange.getResponseHeaders().set("X-Cache", fromCache ? "HIT" : "MISS");
                sendToClient(exchange, result);
            });

            server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
            logger.info("Smart Proxy запущен на порту :" + smartProxyPort);
            server.start();

        } catch (IOException  e) {
            logger.log(Level.SEVERE, "Ошибка запуска HTTP сервера", e);
            System.exit(1);
        }
    }

    private void sendToClient(HttpExchange exchange, DwResponse response) throws IOException {
        try (exchange) {
            if (response.contentType() != null && !response.contentType().isEmpty()) {
                exchange.getResponseHeaders().set("Content-Type", response.contentType());
            }
            byte[] body = response.body().getBytes(StandardCharsets.UTF_8);
            if (body.length == 0) {
                exchange.sendResponseHeaders(response.status(), -1);
                return;
            }
            exchange.sendResponseHeaders(response.status(), body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
        }
    }
}