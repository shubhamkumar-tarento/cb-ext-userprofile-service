package com.igot.cb.transactional.redis.cache;

import java.util.*;

import com.igot.cb.util.CbServerProperties;


import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

@Service
@Slf4j
@RequiredArgsConstructor
public class CacheService {

    private static int cacheTtl = 84600;

    private final JedisPool jedisPool;

    private final JedisPool jedisDataPopulationPool;

    private final CbServerProperties serverProperties;

    private final ObjectMapper objectMapper;

    private static final Logger logger = LoggerFactory.getLogger(CacheService.class);


    public String hget(String key, int index, String field, int ttlInSeconds) {
        try (Jedis jedis = jedisDataPopulationPool.getResource()) {
            jedis.select(index);
            List<String> result = jedis.hmget(key, field);
            String value = CollectionUtils.isEmpty(result) ? null : result.get(0);
            if (value != null) { // only reset TTL when a real value exists
                if (ttlInSeconds > 0) {
                    jedis.expire(key, ttlInSeconds);
                }
                return value;
            }
            return null;
        } catch (Exception e) {
            logger.error("Error in hget: ", e);
            return null;
        }
    }

    public void hset(String key, int index, String field, String value, int ttlInSeconds) {
        try (Jedis jedis = jedisDataPopulationPool.getResource()) {
            jedis.select(index);
            jedis.hset(key, field, value);
            int expiry = (ttlInSeconds > 0) ? ttlInSeconds : cacheTtl;
            jedis.expire(key, expiry);
        } catch (Exception e) {
            logger.error("Error in hset: ", e);
        }
    }

    public void putCache(String key, Object object, int ttl) {
        try (Jedis jedis = jedisPool.getResource()) {
            String data = objectMapper.writeValueAsString(object);
            jedis.set(key, data);
            jedis.expire(key, ttl);
            logger.debug("Cache_key_value {} is saved in redis", key);
        } catch (Exception e) {
            logger.error("Error in putCache", e);
        }
    }

    public void putCache(String key, Object object) {
        putCache(key, object, cacheTtl);
    }

    public String getCache(String key) {
        try (Jedis jedis = jedisPool.getResource()) {
            return jedis.get(key);
        } catch (Exception e) {
            logger.error("Error while reading the cache",e);
            return null;
        }
    }

    public Map<String, String> getCourseMetadataAsJsonString(List<String> courseIds) {
        if (courseIds == null || courseIds.isEmpty())
            return Map.of();
        List<String> keys = new ArrayList<>(courseIds);
        Map<String, String> result = new LinkedHashMap<>();
        try (Jedis jedis = jedisPool.getResource()) {
            String[] keyArray = keys.toArray(new String[0]);
            List<String> values = jedis.mget(keyArray);
            if (values == null || values.isEmpty()) {
                return result;
            }
            if (keys.size() != values.size()) {
                log.error("Failed to get the course details from Redis Cache. KeySize: {}, Value retrieved: {}", keys.size(), values.size());
                return result;
            }
            for (int i = 0; i < keys.size(); i++) {
                String json = values.get(i);
                if (json != null) {
                    result.put(keys.get(i), json);
                }
            }
        } catch (Exception e) {
            log.error("Error in getCourseMetadataAsJsonString: ", e);
        }
        return result;
    }

    public void removeCache(String key) {
        try (Jedis jedis = jedisPool.getResource()) {
            jedis.del(key);
            logger.debug("Cache key {} removed from redis", key);
        } catch (Exception e) {
            logger.error("Error removing cache key {}", key, e);
        }
    }

    public List<Object> hget(List<String> keys) {
        List<Object> resultList = new ArrayList<>();
        try (Jedis jedis = jedisDataPopulationPool.getResource()) {
            // Default index is 0, no need to select
            for (String key : keys) {
                List<String> result = jedis.hmget(key, key);
                String value = CollectionUtils.isEmpty(result) ? null : result.get(0);
                resultList.add(value);
            }
        } catch (Exception e) {
            logger.error("Error in hget: ", e);
        }
        return resultList;
    }
}
