package org.example.repo;

import org.example.models.DwResponse;
import redis.clients.jedis.JedisPooled;

import java.util.Map;


public class ResponseCache {
    private final JedisPooled redis;
    private final long ttlSeconds;

    public ResponseCache(JedisPooled redis, long ttlSeconds) {
        this.redis = redis;
        this.ttlSeconds = ttlSeconds;
    }

    public DwResponse get(String key){
        Map<String, String> data = redis.hgetAll(key);

        if(data.isEmpty()){
            return null;
        }

        return new DwResponse(
                Integer.parseInt(data.get("status")),
                data.get("content-type"),
                data.get("body"));
    }

    public void put(String key, DwResponse response){
        String contentType = response.contentType() == null ? "" : response.contentType();
        redis.hset(key, Map.of(
                "status", String.valueOf(response.status()),
                "contentType", contentType,
                "body", response.body()));
        redis.expire(key, ttlSeconds);
    }
    public void clear() {
        redis.flushDB();
    }
}
