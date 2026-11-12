package org.example.repo;


import redis.clients.jedis.DefaultJedisClientConfig;
import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.JedisClientConfig;
import redis.clients.jedis.JedisPooled;

import java.util.logging.Logger;


public class RedisDB implements CacheDatabase{
    private static final Logger logger = Logger.getLogger(RedisDB.class.getName());

    private final String address;
    private final int port;
    private final String password;
    private final int db;

    public RedisDB(String address, int port, String password, int db) {
        this.address = address;
        this.port = port;
        this.password = password;
        this.db = db;
    }

    public JedisPooled initClient() {
        logger.info(String.format("Address: %s, port: %d, password: %s, db: %d", address, port, password, db));

        String host = "localhost";

        if (address != null && address.contains(":")) {
            String[] parts = address.split(":");
            host = parts[0];
        } else if (address != null && !address.isEmpty()) {
            host = address;
        }

        JedisClientConfig config = DefaultJedisClientConfig.builder()
                .password((password != null && !password.isEmpty()) ? password : null)
                .database(db)
                .build();

        return new JedisPooled(new HostAndPort(host, port), config);
    }

    public JedisPooled connect() {
        JedisPooled client = initClient();
        try {
            String pong = client.ping();
            if (!"PONG".equalsIgnoreCase(pong)) {
                throw new IllegalStateException("Redis ping returned: " + pong);
            }
            return client;
        } catch (RuntimeException e) {
            client.close();      // не оставляем висящий клиент
            throw e;
        }
    }

    // Getters
    public String getAddress() {
        return address;
    }

    public int getPort() {
        return port;
    }

    public String getPassword() {
        return password;
    }
}