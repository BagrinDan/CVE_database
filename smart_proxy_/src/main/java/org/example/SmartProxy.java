package org.example;


import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import io.github.cdimascio.dotenv.Dotenv;
import redis.clients.jedis.JedisPooled;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;


public class SmartProxy {
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
        RedisDB redisdb = initRedisDB();

        if (redisdb == null) {
            logger.severe("Cant connect to redis. " + (redisError != null ? redisError.getMessage() : ""));
            System.exit(1);
        }

        RoundRobinForwarder forwarder = new RoundRobinForwarder(this.dataWarehouses);
        HttpHandler response = forwarder::forward;

        this.initProxyServer(redisdb, response);
    }

    /*
        Init Redis Client
    */
    private RedisDB initRedisDB() {
        String address = dotenv.get("REDIS_ADDRESS", System.getenv("CAN'T READ ENV"));
        int port = Integer.parseInt(dotenv.get("REDIS_PORT", System.getenv("404")));
        String password = dotenv.get("REDIS_PASSWORD", System.getenv("CAN'T READ ENV"));
        int db = Integer.parseInt(dotenv.get("REDIS_DB", System.getenv("404")));
        logger.info(String.format("Read from env: Address: %s, password: %s, db: %d", address, password, db));

        RedisDB redisdb = new RedisDB(address, port, password, db);

        try (JedisPooled client = redisdb.initRedisClient()) {
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

    /*

    */
    private void initProxyServer(RedisDB redisdb, HttpHandler response){
        try {
            int smartProxyPort = Integer.parseInt(dotenv.get("SMART_PROXY_PORT", System.getenv("CAN'T READ ENV")));
            HttpServer server = HttpServer.create(new InetSocketAddress(smartProxyPort), 0);

            JedisPooled redisClient = redisdb.initRedisClient();

            server.createContext("/", exchange -> {
                String clientIP = exchange.getRemoteAddress().getAddress().getHostAddress();
                try {
                    redisClient.setex("client:" + clientIP, 300, String.valueOf(System.currentTimeMillis()));
                    logger.info("Подключение клиента " + clientIP + " зафиксировано");
                } catch (Exception e) {
                    logger.log(Level.WARNING, "Ошибка сохранения клиента " + clientIP, e);
                }

                response.handle(exchange);
            });

            server.setExecutor(null);
            logger.info("Smart Proxy запущен на порту :" + smartProxyPort);
            server.start();

        } catch (IOException e) {
            logger.log(Level.SEVERE, "Ошибка запуска HTTP сервера", e);
            System.exit(1);
        }
    }
}