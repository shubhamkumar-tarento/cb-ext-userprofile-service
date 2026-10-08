package com.igot.cb.transactional.redis.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CacheServiceTest {

    @Mock
    private JedisPool jedisPool;

    @Mock
    private JedisPool jedisDataPopulationPool;

    @Mock
    private Jedis jedis;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private CacheService cacheService;

    @BeforeEach
    void setUp() {
        lenient().when(jedisPool.getResource()).thenReturn(jedis);
        lenient().when(jedisDataPopulationPool.getResource()).thenReturn(jedis);
    }

    @Test
    void hget_ReturnsValueAndResetsTTL_WhenFieldExists() {
        when(jedis.hmget("key", "field")).thenReturn(List.of("value"));
        String result = cacheService.hget("key", 0, "field", 100);
        assertEquals("value", result);
        verify(jedis).expire("key", 100);
    }

    @Test
    void hget_ReturnsNull_WhenFieldDoesNotExist() {
        lenient().when(jedis.hmget("key", "field")).thenReturn(Arrays.asList((String) null));
        String result = cacheService.hget("key", 0, "field", 100);
        assertNull(result);
    }

    @Test
    void hset_SetsValueAndTTL() {
        cacheService.hset("key", 0, "field", "value",0);
        verify(jedis).hset("key", "field", "value");
        verify(jedis).expire("key", 84600);
    }

    @Test
    void putCache_SerializesAndSetsValueWithTTL() throws Exception {
        Object obj = Map.of("a", 1);
        when(objectMapper.writeValueAsString(obj)).thenReturn("{\"a\":1}");
        cacheService.putCache("key", obj, 123);
        verify(jedis).set("key", "{\"a\":1}");
        verify(jedis).expire("key", 123);
    }

    @Test
    void putCache_UsesDefaultTTL() throws Exception {
        Object obj = Map.of("a", 1);
        when(objectMapper.writeValueAsString(obj)).thenReturn("{\"a\":1}");
        cacheService.putCache("key", obj);
        verify(jedis).set("key", "{\"a\":1}");
        verify(jedis).expire("key", 84600);
    }

    @Test
    void getCache_ReturnsValue_WhenKeyExists() {
        when(jedis.get("key")).thenReturn("value");
        String result = cacheService.getCache("key");
        assertEquals("value", result);
    }

    @Test
    void getCache_ReturnsNull_OnException() {
        when(jedis.get("key")).thenThrow(new RuntimeException("fail"));
        String result = cacheService.getCache("key");
        assertNull(result);
    }

    @Test
    void getCourseMetadataAsJsonString_ReturnsMap_WhenAllKeysHaveValues() {
        when(jedis.mget("k1", "k2")).thenReturn(List.of("{\"a\":1}", "{\"b\":2}"));
        Map<String, String> result = cacheService.getCourseMetadataAsJsonString(List.of("k1", "k2"));
        assertEquals(2, result.size());
        assertEquals("{\"a\":1}", result.get("k1"));
        assertEquals("{\"b\":2}", result.get("k2"));
    }

    @Test
    void getCourseMetadataAsJsonString_ReturnsEmptyMap_WhenInputListIsNull() {
        Map<String, String> result = cacheService.getCourseMetadataAsJsonString(null);
        assertTrue(result.isEmpty());
    }

    @Test
    void getCourseMetadataAsJsonString_ReturnsEmptyMap_WhenInputListIsEmpty() {
        Map<String, String> result = cacheService.getCourseMetadataAsJsonString(List.of());
        assertTrue(result.isEmpty());
    }

    @Test
    void getCourseMetadataAsJsonString_ReturnsEmptyMap_WhenValuesListIsNull() {
        when(jedis.mget("k1")).thenReturn(null);
        Map<String, String> result = cacheService.getCourseMetadataAsJsonString(List.of("k1"));
        assertTrue(result.isEmpty());
    }

    @Test
    void getCourseMetadataAsJsonString_ReturnsEmptyMap_WhenKeysAndValuesSizeMismatch() {
        when(jedis.mget("k1", "k2")).thenReturn(List.of("{\"a\":1}"));
        Map<String, String> result = cacheService.getCourseMetadataAsJsonString(List.of("k1", "k2"));
        assertTrue(result.isEmpty());
    }

    @Test
    void hget_ReturnsNull_WhenFieldListIsEmpty() {
        when(jedis.hmget("key", "field")).thenReturn(List.of());
        String result = cacheService.hget("key", 0, "field", 100);
        assertNull(result);
    }

    @Test
    void hget_ReturnsNull_OnException() {
        when(jedis.hmget("key", "field")).thenThrow(new RuntimeException("fail"));
        String result = cacheService.hget("key", 0, "field", 100);
        assertNull(result);
    }

    @Test
    void hset_DoesNotThrow_OnException() {
        doThrow(new RuntimeException("fail")).when(jedis).hset("key", "field", "value");
        cacheService.hset("key", 0, "field", "value",0);
        assertNotNull(cacheService);
    }

    @Test
    void putCache_DoesNotThrow_OnException() throws Exception {
        Object obj = Map.of("a", 1);
        when(objectMapper.writeValueAsString(obj)).thenThrow(new RuntimeException("fail"));
        cacheService.putCache("key", obj, 100);
        assertNotNull(cacheService);
    }

    @Test
    void getCourseMetadataAsJsonString_ReturnsEmptyMap_OnException() {
        when(jedis.mget(any(String[].class))).thenThrow(new RuntimeException("fail"));
        Map<String, String> result = cacheService.getCourseMetadataAsJsonString(List.of("k1", "k2"));
        assertTrue(result.isEmpty());
    }

    @Test
    void hget_ReturnsNull_WhenResultIsEmpty() {
        when(jedis.hmget("key", "field")).thenReturn(List.of());
        String result = cacheService.hget("key", 0, "field", 100);
        assertNull(result);
        verify(jedis, never()).expire(anyString(), anyInt());
    }

    @Test
    void hget_ReturnsNull_WhenResultIsNull() {
        when(jedis.hmget("key", "field")).thenReturn(null);
        String result = cacheService.hget("key", 0, "field", 100);
        assertNull(result);
        verify(jedis, never()).expire(anyString(), anyInt());
    }

    @Test
    void getCourseMetadataAsJsonString_ReturnsEmptyMap_WhenValuesListIsEmpty() {
        when(jedis.mget("k1")).thenReturn(List.of());
        Map<String, String> result = cacheService.getCourseMetadataAsJsonString(List.of("k1"));
        assertTrue(result.isEmpty());
    }

    @Test
    void hget_DoesNotResetTTL_WhenTtlIsZeroOrLess() {
        when(jedis.hmget("key", "field")).thenReturn(List.of("value"));
        String result = cacheService.hget("key", 0, "field", 0);
        assertEquals("value", result);
        verify(jedis, never()).expire(anyString(), anyInt());
    }

    @Test
    void hset_UsesProvidedTTL_WhenTtlIsPositive() {
        cacheService.hset("key", 0, "field", "value", 500);
        verify(jedis).hset("key", "field", "value");
        verify(jedis).expire("key", 500);
    }

    @Test
    void getCourseMetadataAsJsonString_SkipsNullValues_ButKeepsNonNullOnes() {
        when(jedis.mget("k1", "k2")).thenReturn(Arrays.asList(null, "{\"b\":2}"));
        Map<String, String> result = cacheService.getCourseMetadataAsJsonString(List.of("k1", "k2"));
        assertEquals(1, result.size());
        assertEquals("{\"b\":2}", result.get("k2"));
        assertNull(result.get("k1"));
    }

    @Test
    void removeCache_DeletesKey() {
        cacheService.removeCache("key");
        verify(jedis).del("key");
    }

    @Test
    void removeCache_DoesNotThrow_OnException() {
        doThrow(new RuntimeException("fail")).when(jedis).del("key");
        cacheService.removeCache("key");
        assertNotNull(cacheService);
    }

    @Test
    void hgetList_ReturnsValuesForAllKeys() {
        when(jedis.hmget("k1", "k1")).thenReturn(List.of("v1"));
        when(jedis.hmget("k2", "k2")).thenReturn(List.of("v2"));
        List<Object> result = cacheService.hget(List.of("k1", "k2"));
        assertEquals(2, result.size());
        assertEquals("v1", result.get(0));
        assertEquals("v2", result.get(1));
    }

    @Test
    void hgetList_ReturnsEmptyList_OnException() {
        when(jedis.hmget(anyString(), anyString())).thenThrow(new RuntimeException("fail"));
        List<Object> result = cacheService.hget(List.of("k1"));
        assertTrue(result.isEmpty());
    }
}