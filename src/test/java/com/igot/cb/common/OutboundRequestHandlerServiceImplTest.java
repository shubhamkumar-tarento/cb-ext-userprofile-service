package com.igot.cb.common;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.*;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OutboundRequestHandlerServiceImplTest {

    @InjectMocks
    private OutboundRequestHandlerServiceImpl service;

    @Mock
    private RestTemplate restTemplate;

    private static ch.qos.logback.classic.Level originalLevel;

    @BeforeAll
    static void enableDebugLogging() {
        ch.qos.logback.classic.Logger logger =
                (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(OutboundRequestHandlerServiceImpl.class);
        originalLevel = logger.getLevel();
        logger.setLevel(ch.qos.logback.classic.Level.DEBUG);
    }

    @AfterAll
    static void restoreLogLevel() {
        ch.qos.logback.classic.Logger logger =
                (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(OutboundRequestHandlerServiceImpl.class);
        logger.setLevel(originalLevel);
    }

    @Test
    void testFetchUsingGetWithHeadersProfile_Success() {
        String uri = "http://example.com";
        Map<String, String> headers = Map.of("Authorization", "Bearer token");
        Map<String, Object> responseMap = Map.of("key", "value");

        ResponseEntity<Map> response = new ResponseEntity<>(responseMap, HttpStatus.OK);
        when(restTemplate.exchange(eq(uri), eq(HttpMethod.GET), any(HttpEntity.class), eq(Map.class)))
                .thenReturn(response);

        Object result = service.fetchUsingGetWithHeadersProfile(uri, headers);

        assertTrue(result instanceof Map);
        assertEquals("value", ((Map<?, ?>) result).get("key"));
    }

    @Test
    void testFetchUsingGetWithHeadersProfile_EmptyHeaders() {
        String uri = "http://example.com";
        ResponseEntity<Map> response = new ResponseEntity<>(Map.of(), HttpStatus.OK);

        when(restTemplate.exchange(eq(uri), eq(HttpMethod.GET), any(HttpEntity.class), eq(Map.class)))
                .thenReturn(response);

        Object result = service.fetchUsingGetWithHeadersProfile(uri, null);

        assertTrue(result instanceof Map);
    }

    @Test
    void testFetchUsingGetWithHeadersProfile_HttpClientErrorException() {
        String uri = "http://example.com";
        String errorJson = "{\"error\": \"Unauthorized\"}";
        HttpClientErrorException exception = HttpClientErrorException.create(
                HttpStatus.UNAUTHORIZED, "Unauthorized", HttpHeaders.EMPTY, errorJson.getBytes(), null);

        when(restTemplate.exchange(eq(uri), eq(HttpMethod.GET), any(HttpEntity.class), eq(Map.class)))
                .thenThrow(exception);

        Object result = service.fetchUsingGetWithHeadersProfile(uri, Map.of());

        assertTrue(result instanceof Map);
        assertEquals("Unauthorized", ((Map<?, ?>) result).get("error"));
    }

    @Test
    void testFetchUsingGetWithHeadersProfile_GenericException() {
        String uri = "http://example.com";

        when(restTemplate.exchange(eq(uri), eq(HttpMethod.GET), any(HttpEntity.class), eq(Map.class)))
                .thenThrow(new RuntimeException("Unexpected error"));

        Object result = service.fetchUsingGetWithHeadersProfile(uri, Map.of());

        assertNull(result);
    }

    @Test
    void testFetchResultUsingPatch_Success() {
        String uri = "http://example.com";
        Map<String, String> headers = Map.of("Authorization", "Bearer token");
        Map<String, Object> responseMap = Map.of("result", "ok");

        when(restTemplate.patchForObject(eq(uri), any(HttpEntity.class), eq(Map.class)))
                .thenReturn(responseMap);

        Map<String, Object> result = service.fetchResultUsingPatch(uri, Map.of("req", "data"), headers);

        assertEquals("ok", result.get("result"));
    }

    @Test
    void testFetchResultUsingPatch_HttpClientErrorException() {
        String uri = "http://example.com";
        String errorJson = "{\"error\": \"Invalid request\"}";

        HttpClientErrorException exception = HttpClientErrorException.create(
                HttpStatus.BAD_REQUEST, "Bad Request", HttpHeaders.EMPTY, errorJson.getBytes(), null);

        when(restTemplate.patchForObject(eq(uri), any(HttpEntity.class), eq(Map.class)))
                .thenThrow(exception);

        Map<String, Object> result = service.fetchResultUsingPatch(uri, Map.of(), Map.of());

        assertEquals("Invalid request", result.get("error"));
    }

    @Test
    void testFetchResultUsingPatch_NullResponse() {
        String uri = "http://example.com";
        when(restTemplate.patchForObject(eq(uri), any(HttpEntity.class), eq(Map.class)))
                .thenReturn(null);

        Map<String, Object> result = service.fetchResultUsingPatch(uri, Map.of(), Map.of());

        assertTrue(result.isEmpty());
    }

    @Test
    void testFetchResultUsingPatch_LogDetailsCoverage() {
        String uri = "http://example.com";
        Map<String, Object> request = Map.of("name", "test");
        Map<String, Object> response = Map.of("status", "ok");

        when(restTemplate.patchForObject(eq(uri), any(HttpEntity.class), eq(Map.class)))
                .thenReturn(response);

        // Call the method to trigger logDetails
        Map<String, Object> result = service.fetchResultUsingPatch(uri, request, Map.of());

        assertEquals("ok", result.get("status"));
    }

    @Test
    void testFetchUsingGetWithHeadersProfile_DebugEnabled_Success() {
        String uri = "http://example.com";
        Map<String, String> headers = Map.of("Authorization", "Bearer token");
        Map<String, Object> responseMap = Map.of("key", "value");

        ResponseEntity<Map> response = new ResponseEntity<>(responseMap, HttpStatus.OK);
        when(restTemplate.exchange(eq(uri), eq(HttpMethod.GET), any(HttpEntity.class), eq(Map.class)))
                .thenReturn(response);

        Object result = service.fetchUsingGetWithHeadersProfile(uri, headers);

        assertTrue(result instanceof Map);
        assertEquals("value", ((Map<?, ?>) result).get("key"));
    }

    @Test
    void testFetchUsingGetWithHeadersProfile_HttpClientErrorException_MalformedBody() {
        String uri = "http://example.com";
        String malformedJson = "not a json body";
        HttpClientErrorException exception = HttpClientErrorException.create(
                HttpStatus.UNAUTHORIZED, "Unauthorized", HttpHeaders.EMPTY, malformedJson.getBytes(), null);

        when(restTemplate.exchange(eq(uri), eq(HttpMethod.GET), any(HttpEntity.class), eq(Map.class)))
                .thenThrow(exception);

        Object result = service.fetchUsingGetWithHeadersProfile(uri, Map.of());

        assertNull(result);
    }

    @Test
    void testFetchResultUsingPatch_DebugEnabled_Success() {
        String uri = "http://example.com";
        Map<String, String> headers = Map.of("Authorization", "Bearer token");
        Map<String, Object> responseMap = Map.of("result", "ok");

        when(restTemplate.patchForObject(eq(uri), any(HttpEntity.class), eq(Map.class)))
                .thenReturn(responseMap);

        Map<String, Object> result = service.fetchResultUsingPatch(uri, Map.of("req", "data"), headers);

        assertEquals("ok", result.get("result"));
    }

    @Test
    void testFetchResultUsingPatch_DebugEnabled_LogDetailsSerializationFailure() {
        String uri = "http://example.com";
        BadRequest badRequest = new BadRequest();
        Map<String, Object> responseMap = Map.of("result", "ok");

        when(restTemplate.patchForObject(eq(uri), any(HttpEntity.class), eq(Map.class)))
                .thenReturn(responseMap);

        Map<String, Object> result = service.fetchResultUsingPatch(uri, badRequest, Map.of());

        assertEquals("ok", result.get("result"));
    }

    /**
     * A request object whose getter throws at serialization time, forcing Jackson's
     * real ObjectMapper (used internally by the production method's debug logging)
     * to raise a JsonProcessingException, without the infinite-recursion
     * StackOverflowError a truly self-referential Map would trigger.
     */
    static class BadRequest {
        public String getValue() {
            throw new RuntimeException("boom");
        }
    }

    @Test
    void testFetchResultUsingPatch_HttpClientErrorException_MalformedBody() {
        String uri = "http://example.com";
        String malformedJson = "not a json body";

        HttpClientErrorException exception = HttpClientErrorException.create(
                HttpStatus.BAD_REQUEST, "Bad Request", HttpHeaders.EMPTY, malformedJson.getBytes(), null);

        when(restTemplate.patchForObject(eq(uri), any(HttpEntity.class), eq(Map.class)))
                .thenThrow(exception);

        Map<String, Object> result = service.fetchResultUsingPatch(uri, Map.of(), Map.of());

        assertTrue(result.isEmpty());
    }

    @Test
    void testFetchResultUsingPost_Success() {
        String uri = "http://example.com";
        Map<String, String> headers = Map.of("Authorization", "Bearer token");
        Map<String, Object> responseMap = Map.of("result", "ok");

        when(restTemplate.postForObject(eq(uri), any(HttpEntity.class), eq(Map.class)))
                .thenReturn(responseMap);

        Map<String, Object> result = service.fetchResultUsingPost(uri, Map.of("req", "data"), headers);

        assertEquals("ok", result.get("result"));
    }

    @Test
    void testFetchResultUsingPost_DebugEnabled_Success() {
        String uri = "http://example.com";
        Map<String, Object> responseMap = Map.of("result", "ok");

        when(restTemplate.postForObject(eq(uri), any(HttpEntity.class), eq(Map.class)))
                .thenReturn(responseMap);

        Map<String, Object> result = service.fetchResultUsingPost(uri, Map.of("req", "data"), Map.of());

        assertEquals("ok", result.get("result"));
    }

    @Test
    void testFetchResultUsingPost_DebugEnabled_RequestSerializationFailure() {
        String uri = "http://example.com";
        BadRequest badRequest = new BadRequest();

        Map<String, Object> result = service.fetchResultUsingPost(uri, badRequest, Map.of());

        assertTrue(result.isEmpty());
    }

    @Test
    void testFetchResultUsingPost_HttpStatusCodeException_WithParsableBody() {
        String uri = "http://example.com";
        String errorJson = "{\"error\": \"Invalid request\"}";

        HttpClientErrorException exception = HttpClientErrorException.create(
                HttpStatus.BAD_REQUEST, "Bad Request", HttpHeaders.EMPTY, errorJson.getBytes(), null);

        when(restTemplate.postForObject(eq(uri), any(HttpEntity.class), eq(Map.class)))
                .thenThrow(exception);

        Map<String, Object> result = service.fetchResultUsingPost(uri, Map.of("req", "data"), Map.of());

        assertEquals("Invalid request", result.get("error"));
    }

    @Test
    void testFetchResultUsingPost_HttpStatusCodeException_BlankBody() {
        String uri = "http://example.com";

        HttpClientErrorException exception = HttpClientErrorException.create(
                HttpStatus.INTERNAL_SERVER_ERROR, "Server Error", HttpHeaders.EMPTY, new byte[0], null);

        when(restTemplate.postForObject(eq(uri), any(HttpEntity.class), eq(Map.class)))
                .thenThrow(exception);

        Map<String, Object> result = service.fetchResultUsingPost(uri, Map.of("req", "data"), Map.of());

        assertTrue(result.isEmpty());
    }

    @Test
    void testFetchResultUsingPost_HttpStatusCodeException_MalformedBody() {
        String uri = "http://example.com";
        String malformedJson = "not a json body";

        HttpClientErrorException exception = HttpClientErrorException.create(
                HttpStatus.BAD_REQUEST, "Bad Request", HttpHeaders.EMPTY, malformedJson.getBytes(), null);

        when(restTemplate.postForObject(eq(uri), any(HttpEntity.class), eq(Map.class)))
                .thenThrow(exception);

        Map<String, Object> result = service.fetchResultUsingPost(uri, Map.of("req", "data"), Map.of());

        assertTrue(result.isEmpty());
    }

    @Test
    void testFetchResultUsingPost_GenericException() {
        String uri = "http://example.com";

        when(restTemplate.postForObject(eq(uri), any(HttpEntity.class), eq(Map.class)))
                .thenThrow(new RuntimeException("Unexpected error"));

        Map<String, Object> result = service.fetchResultUsingPost(uri, Map.of("req", "data"), Map.of());

        assertTrue(result.isEmpty());
    }
}
