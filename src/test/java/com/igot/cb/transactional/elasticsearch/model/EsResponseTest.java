package com.igot.cb.transactional.elasticsearch.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class EsResponseTest {

    @Test
    void testNoArgsConstructorAndSettersGetters() {
        EsResponse response = new EsResponse();
        Instant now = Instant.now();
        response.setSuccess(true);
        response.setMessage("ok");
        response.setErrorCode("E001");
        response.setDocumentId("doc1");
        response.setCount(5L);
        response.setData("payload");
        response.setTimestamp(now);

        assertTrue(response.isSuccess());
        assertEquals("ok", response.getMessage());
        assertEquals("E001", response.getErrorCode());
        assertEquals("doc1", response.getDocumentId());
        assertEquals(5L, response.getCount());
        assertEquals("payload", response.getData());
        assertEquals(now, response.getTimestamp());
    }

    @Test
    void testAllArgsConstructor() {
        Instant now = Instant.now();
        EsResponse response = new EsResponse(true, "msg", "code", "docId", 10L, "data", now);
        assertTrue(response.isSuccess());
        assertEquals("msg", response.getMessage());
        assertEquals("code", response.getErrorCode());
        assertEquals("docId", response.getDocumentId());
        assertEquals(10L, response.getCount());
        assertEquals("data", response.getData());
        assertEquals(now, response.getTimestamp());
    }

    @Test
    void testBuilderWithDefaultTimestamp() {
        EsResponse response = EsResponse.builder()
                .success(false)
                .message("err")
                .errorCode("E002")
                .documentId("doc2")
                .count(1L)
                .data(null)
                .build();

        assertFalse(response.isSuccess());
        assertEquals("err", response.getMessage());
        assertNotNull(response.getTimestamp());
    }

    @Test
    void testBuilderWithExplicitTimestamp() {
        Instant ts = Instant.parse("2020-01-01T00:00:00Z");
        EsResponse response = EsResponse.builder()
                .success(true)
                .timestamp(ts)
                .build();

        assertEquals(ts, response.getTimestamp());
    }

    @Test
    void testEqualsHashCodeToString() {
        Instant now = Instant.now();
        EsResponse response1 = new EsResponse(true, "msg", "code", "docId", 10L, "data", now);
        EsResponse response2 = new EsResponse(true, "msg", "code", "docId", 10L, "data", now);

        assertEquals(response1, response2);
        assertEquals(response1.hashCode(), response2.hashCode());
        assertNotNull(response1.toString());
        assertNotEquals(response1, null);
        assertNotEquals(response1, new Object());
    }
}
