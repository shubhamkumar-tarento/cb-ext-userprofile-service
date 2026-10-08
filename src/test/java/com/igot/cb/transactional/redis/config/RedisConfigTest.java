package com.igot.cb.transactional.redis.config;

import com.igot.cb.transactional.elasticsearch.dto.SearchResult;
import com.igot.cb.util.CbServerProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import redis.clients.jedis.JedisPool;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RedisConfigTest {

    @Mock
    private CbServerProperties cbProperties;

    @Test
    void jedisPool_ReturnsConfiguredJedisPool() {
        when(cbProperties.getRedisHostName()).thenReturn("localhost");
        when(cbProperties.getRedisPort()).thenReturn("6379");
        when(cbProperties.getRedisMaxIdle()).thenReturn(5);
        when(cbProperties.getRedisMaxTotal()).thenReturn(10);
        when(cbProperties.getRedisMinIdle()).thenReturn(1);
        when(cbProperties.getRedisTestOnBorrow()).thenReturn(true);
        when(cbProperties.getRedisTestOnReturn()).thenReturn(false);
        when(cbProperties.getRedisTestWhileIdle()).thenReturn(true);
        when(cbProperties.getRedisMinEvictableIdleTimeMillis()).thenReturn(60000L);
        when(cbProperties.getRedisNumTestsPerEvictionRun()).thenReturn(3);
        when(cbProperties.getRedisBlockWhenExhausted()).thenReturn(true);

        RedisConfig redisConfig = new RedisConfig(cbProperties);

        JedisPool pool = redisConfig.jedisPool();
        assertNotNull(pool);
    }

    @Test
    void jedisDataPopulationPool_ReturnsConfiguredJedisPool() {
        when(cbProperties.getRedisDataHostName()).thenReturn("localhost");
        when(cbProperties.getRedisDataPort()).thenReturn("6380");
        when(cbProperties.getRedisMaxIdle()).thenReturn(2);
        when(cbProperties.getRedisMaxTotal()).thenReturn(4);
        when(cbProperties.getRedisMinIdle()).thenReturn(1);
        when(cbProperties.getRedisTestOnBorrow()).thenReturn(false);
        when(cbProperties.getRedisTestOnReturn()).thenReturn(true);
        when(cbProperties.getRedisTestWhileIdle()).thenReturn(false);
        when(cbProperties.getRedisMinEvictableIdleTimeMillis()).thenReturn(120000L);
        when(cbProperties.getRedisNumTestsPerEvictionRun()).thenReturn(2);
        when(cbProperties.getRedisBlockWhenExhausted()).thenReturn(false);

        RedisConfig redisConfig = new RedisConfig(cbProperties);

        JedisPool pool = redisConfig.jedisDataPopulationPool();
        assertNotNull(pool);
    }

    @Test
    void searchResultRedisTemplate_ReturnsConfiguredRedisTemplate() {
        when(cbProperties.getRedisHostName()).thenReturn("localhost");
        when(cbProperties.getRedisPort()).thenReturn("6379");

        RedisConfig redisConfig = new RedisConfig(cbProperties);

        RedisTemplate<String, SearchResult> template = redisConfig.searchResultRedisTemplate();

        assertNotNull(template);
        assertNotNull(template.getConnectionFactory());
        assertNotNull(template.getKeySerializer());
        assertNotNull(template.getValueSerializer());
    }
}