package org.example.repo;

import redis.clients.jedis.JedisPooled;


public interface CacheDatabase {
    JedisPooled initClient();
    JedisPooled connect();
}
