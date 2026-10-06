package com.igot.cb.transactional.redis.config;

import com.igot.cb.transactional.elasticsearch.dto.SearchResult;
import com.igot.cb.util.CbServerProperties;
import com.igot.cb.util.Constants;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.RedisTemplate;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

@Configuration
@EnableCaching
@RequiredArgsConstructor
public class RedisConfig {

    private final CbServerProperties cbProperties;

    @Bean
    public JedisPool jedisPool() {
        final JedisPoolConfig poolConfig = buildPoolConfig();
        return new JedisPool(poolConfig, cbProperties.getRedisHostName(),
                Integer.parseInt(cbProperties.getRedisPort()));
    }

    @Bean
    public JedisPool jedisDataPopulationPool() {
        final JedisPoolConfig poolConfig = buildPoolConfig();
        return new JedisPool(poolConfig, cbProperties.getRedisDataHostName(),
                Integer.parseInt(cbProperties.getRedisDataPort()));
    }

    private JedisPoolConfig buildPoolConfig() {
        final JedisPoolConfig poolConfig = new JedisPoolConfig();
        poolConfig.setMaxIdle(cbProperties.getRedisMaxIdle());
        poolConfig.setMaxTotal(cbProperties.getRedisMaxTotal());
        poolConfig.setMinIdle(cbProperties.getRedisMinIdle());
        poolConfig.setTestOnBorrow(cbProperties.getRedisTestOnBorrow());
        poolConfig.setTestOnReturn(cbProperties.getRedisTestOnReturn());
        poolConfig.setTestWhileIdle(cbProperties.getRedisTestWhileIdle());
        poolConfig.setMinEvictableIdleTime(java.time.Duration.ofMillis(cbProperties.getRedisMinEvictableIdleTimeMillis()));
        poolConfig.setTimeBetweenEvictionRuns(java.time.Duration.ofMillis(cbProperties.getRedisNumTestsPerEvictionRun()));
        poolConfig.setNumTestsPerEvictionRun(cbProperties.getRedisNumTestsPerEvictionRun());
        poolConfig.setBlockWhenExhausted(cbProperties.getRedisBlockWhenExhausted());
        return poolConfig;
    }

    @Bean(name = Constants.SEARCH_RESULT_REDIS_TEMPLATE)
    public RedisTemplate<String, SearchResult> searchResultRedisTemplate() {
        org.springframework.data.redis.connection.RedisStandaloneConfiguration redisStandaloneConfiguration =
                new org.springframework.data.redis.connection.RedisStandaloneConfiguration(cbProperties.getRedisHostName(),
                        Integer.parseInt(cbProperties.getRedisPort()));
        org.springframework.data.redis.connection.jedis.JedisConnectionFactory jedisConnectionFactory =
                new org.springframework.data.redis.connection.jedis.JedisConnectionFactory(redisStandaloneConfiguration);
        jedisConnectionFactory.afterPropertiesSet();
        RedisTemplate<String, SearchResult> template = new RedisTemplate<>();
        template.setConnectionFactory(jedisConnectionFactory);
        template.setKeySerializer(new org.springframework.data.redis.serializer.StringRedisSerializer());
        template.setValueSerializer(new org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer<>(SearchResult.class));
        template.afterPropertiesSet();
        return template;
    }
}
