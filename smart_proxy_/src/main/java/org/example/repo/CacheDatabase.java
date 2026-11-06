package org.example.repo;

import redis.clients.jedis.JedisPooled;


public interface CacheDatabase extends BaseDatabase {
    JedisPooled initClient();
}
