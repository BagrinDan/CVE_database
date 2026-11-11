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
        Main method to start service
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

        this.initProxyServer(clientsDb, this.forwarder, cache);
    }

    private CacheDatabase redisConfig(int db) {
        return new RedisDB(
                dotenv.get("REDIS_ADDRESS", "localhost"),
                Integer.parseInt(dotenv.get("REDIS_PORT", "6379")),
                dotenv.get("REDIS_PASSWORD", ""),
                db);
    }

    /*
        Init Redis db0 and db1.
        1. db0 - to cache clients connections
        3. db1 - to cache GET requests
     */
    private JedisPooled initClientsDB() {
        int db = Integer.parseInt(dotenv.get("REDIS_CLIENT_DB", "0"));
        return redisConfig(db).connect();
    }

    private ResponseCache initCache() {
        int db = Integer.parseInt(dotenv.get("REDIS_REQUEST_DB", "1"));
        long ttl = Long.parseLong(dotenv.get("CACHE_TTL_SECONDS", "30"));
        return new ResponseCache(redisConfig(db).connect(), ttl);
    }

    /*
        Init proxy server
     */
    private void initProxyServer(JedisPooled clientsDb, Forwarder forwarder, ResponseCache cache) {
        try {
            int smartProxyPort = Integer.parseInt(dotenv.get("SMART_PROXY_PORT", "8080"));
            HttpServer server = HttpServer.create(new InetSocketAddress(smartProxyPort), 0);

            server.createContext("/", exchange -> handleRequest(exchange, clientsDb, forwarder, cache));

            server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
            logger.info("Smart Proxy запущен на порту :" + smartProxyPort);
            server.start();

        } catch (IOException e) {
            logger.log(Level.SEVERE, "Ошибка запуска HTTP сервера", e);
            System.exit(1);
        }
    }

    private void handleRequest(HttpExchange exchange, JedisPooled clientsDb,
                               Forwarder forwarder, ResponseCache cache) {
        try {
            trackClient(clientsDb, exchange);

            String method = exchange.getRequestMethod();
            String key = cacheKey(exchange);

            DwResponse result = method.equals("GET") ? cache.get(key) : null;
            boolean fromCache = (result != null);

            if (!fromCache) {
                result = forwarder.forward(exchange);
                updateCache(cache, method, key, result);
            }

            exchange.getResponseHeaders().set("X-Cache", fromCache ? "HIT" : "MISS");
            sendToClient(exchange, result);
        } catch (Exception e){
            logger.log(Level.SEVERE, "ERROR | HandleRequest", e);
            // Fără asta clientul rămâne agățat la nesfârșit
            sendToClient(exchange, new DwResponse(500, "text/plain", "Smart proxy error: " + e));
        }
    }

    /*
        Caching client connections
    */
    private void trackClient(JedisPooled clientsDb, HttpExchange exchange) {
        String clientIP = exchange.getRemoteAddress().getAddress().getHostAddress();
        try {
            clientsDb.setex("client:" + clientIP, 300, String.valueOf(System.currentTimeMillis()));
            logger.info("Подключение клиента " + clientIP + " зафиксировано");
        } catch (Exception e) {
            logger.log(Level.WARNING, "Ошибка сохранения клиента " + clientIP, e);
        }
    }

    /*
        Caching Get Request
     */
    private String cacheKey(HttpExchange exchange) {
        String accept = exchange.getRequestHeaders().getFirst("Accept");
        return exchange.getRequestURI() + "|" + accept;
    }

    private void updateCache(ResponseCache cache, String method, String key, DwResponse result) {
        if (method.equals("GET")) {
            if (result.status() == 200) {
                cache.put(key, result);
            }
        } else {
            cache.clear();
        }
    }

    private void sendToClient(HttpExchange exchange, DwResponse response) {
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
        } catch (IOException e){
            logger.info("ERROR | sendToClient: {}");
        }
    }
}