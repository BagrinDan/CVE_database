package org.example.controller;


import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.github.cdimascio.dotenv.Dotenv;
import org.example.repo.CacheDatabase;
import org.example.repo.RedisDB;
import org.example.models.DwResponse;
import org.example.repo.ResponseCache;
import org.example.service.RoundRobinForwarder;
import redis.clients.jedis.JedisPooled;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;



public class SmartProxy implements Proxy {
    private static final Logger logger = Logger.getLogger(SmartProxy.class.getName());

    private Exception redisError;
    private Dotenv dotenv = null;
    List<String> dataWarehouses;

    public SmartProxy(){
        // Init dotenv
        dotenv = Dotenv.configure().filename(".env").load();

        // Init DW
        this.dataWarehouses = Stream.iterate(0, i -> dotenv.get("DW_" + i) != null, i -> i + 1)
                .map(i -> dotenv.get("DW_" + i))
                .toList();

        if (this.dataWarehouses.isEmpty()) {
            logger.severe("No DW in .env");
            System.exit(1);
        }
    }

    /*
        Run smart proxy
    */
    public void run() {
        // Redis
        CacheDatabase redis = initRedisDB();

        if (redis == null) {
            logger.severe("Cant connect to redis. " + (redisError != null ? redisError.getMessage() : ""));
            System.exit(1);
        }

        // Caching
        int cacheDbNumber = Integer.parseInt(dotenv.get("REDIS_CACHE_DB", "1"));
        long ttl = Long.parseLong(dotenv.get("CACHE_TTL_SECONDS", "30"));

        RedisDB cacheDb = new RedisDB(redis.getAddress(), redis.getPort(), redis.getPassword(), cacheDbNumber);
        ResponseCache cache = new ResponseCache(cacheDb.initClient(), ttl);

        RoundRobinForwarder forwarder = new RoundRobinForwarder(this.dataWarehouses);

        this.initProxyServer(redis, forwarder, cache);
    }

    /*
        Init Redis Client
    */
    private CacheDatabase initRedisDB() {
        String address = dotenv.get("REDIS_ADDRESS", System.getenv("CAN'T READ ENV"));
        int port = Integer.parseInt(dotenv.get("REDIS_PORT", System.getenv("404")));
        String password = dotenv.get("REDIS_PASSWORD", System.getenv("CAN'T READ ENV"));
        int db = Integer.parseInt(dotenv.get("REDIS_DB", System.getenv("404")));
        logger.info(String.format("Read from env: Address: %s, password: %s, db: %d", address, password, db));

        CacheDatabase redisdb = new RedisDB(address, port, password, db);

        try (JedisPooled client = redisdb.initClient()) {
            String pingResult = client.ping();
            if ("PONG".equalsIgnoreCase(pingResult)) {
                return redisdb;
            } else {
                this.redisError = new RuntimeException("Redis ping returned: " + pingResult);
                return null;
            }
        } catch (Exception e) {
            this.redisError = e;
            return null;
        }
    }

    private void initProxyServer(CacheDatabase redis, RoundRobinForwarder forwarder, ResponseCache cache){
        try {
            int smartProxyPort = Integer.parseInt(dotenv.get("SMART_PROXY_PORT", System.getenv("CAN'T READ ENV")));
            HttpServer server = HttpServer.create(new InetSocketAddress(smartProxyPort), 0);

            JedisPooled redisClient = redis.initClient();

            server.createContext("/", exchange -> {
                String clientIP = exchange.getRemoteAddress().getAddress().getHostAddress();
                try {
                    redisClient.setex("client:" + clientIP, 300, String.valueOf(System.currentTimeMillis()));
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

                // Не нашли в кэше (или это не GET): идём на DW
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

            server.setExecutor(null);
            logger.info("Smart Proxy запущен на порту :" + smartProxyPort);
            server.start();

        } catch (IOException e) {
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