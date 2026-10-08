package com.igot.cb.transactional.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import java.util.HashMap;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.slf4j.Logger;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
public class RequestHandlerServiceImplTest {

    @Mock
    private RestTemplate restTemplate;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private RequestHandlerServiceImpl requestHandlerServiceImpl;

    @Mock
    private Logger mockLogger;

    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        try {
            java.lang.reflect.Field logField = RequestHandlerServiceImpl.class.getDeclaredField("log");
            logField.setAccessible(true);
            logField.set(requestHandlerServiceImpl, mockLogger);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException("Failed to inject mock logger", e);
        }
    }

    @Test
    public void testFetchResultUsingPost_successfulResponse() throws Exception {
        String uri = "http://example.com/api";
        Object request = new Object();
        Map<String, String> headersValues = new HashMap<>();
        headersValues.put("Authorization", "Bearer token");
        Map<String, Object> expectedResponse = new HashMap<>();
        expectedResponse.put("key", "value");
        when(restTemplate.postForObject(anyString(), any(HttpEntity.class), eq(Map.class)))
                .thenReturn(expectedResponse);
        Map<String, Object> response = requestHandlerServiceImpl.fetchResultUsingPost(uri, request, headersValues);
        assertNotNull(response);
        assertEquals(expectedResponse, response);
    }

    @Test
    public void testFetchResultUsingPost_httpClientErrorException() throws Exception {
        String uri = "http://example.com/api";
        Object request = new Object();
        Map<String, String> headersValues = new HashMap<>();
        String errorJson = "{\"error\":\"Unauthorized\"}";
        HttpClientErrorException exception = HttpClientErrorException.create(
                HttpStatus.UNAUTHORIZED,
                "Unauthorized",
                HttpHeaders.EMPTY,
                errorJson.getBytes(),
                null
        );
        when(restTemplate.postForObject(anyString(), any(HttpEntity.class), eq(Map.class)))
                .thenThrow(exception);
        requestHandlerServiceImpl.fetchResultUsingPost(uri, request, headersValues);
        verify(restTemplate).postForObject(eq(uri), any(HttpEntity.class), eq(Map.class));
    }

    protected Map<String, Object> handleHttpClientError(HttpClientErrorException hce) {
        try {
            return new ObjectMapper().readValue(hce.getResponseBodyAsString(),
                    new TypeReference<HashMap<String, Object>>() {});
        } catch (Exception e) {
            return null;
        }
    }

    @Test
    public void testFetchResultUsingPost_jsonProcessingException() throws Exception {
        String uri = "http://example.com/api";
        Object request = new Object();  // Your actual request object
        Map<String, String> headersValues = new HashMap<>();
        headersValues.put("Authorization", "Bearer token");
        Map<String, Object> expectedResponse = new HashMap<>();
        expectedResponse.put("key", "value");
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Object> entity = new HttpEntity<>(request, headers);
        when(restTemplate.postForObject(uri, entity, Map.class)).thenReturn(expectedResponse);
        when(objectMapper.writeValueAsString(request)).thenThrow(JsonProcessingException.class);
        Map<String, Object> response = requestHandlerServiceImpl.fetchResultUsingPost(uri, request, headersValues);
        assertNull(response);  // Expect null as the response will not be properly serialized due to the exception
    }

    @Test
    public void testFetchUsingGetWithHeadersProfile_successfulResponse() {
        String uri = "http://example.com/api";
        Map<String, String> headersValues = new HashMap<>();
        headersValues.put("Authorization", "Bearer token");
        Map<String, Object> expectedResponse = new HashMap<>();
        expectedResponse.put("key", "value");
        ResponseEntity<Map> responseEntity = new ResponseEntity<>(expectedResponse, HttpStatus.OK);
        when(restTemplate.exchange(eq(uri), eq(HttpMethod.GET), any(HttpEntity.class), eq(Map.class)))
                .thenReturn(responseEntity);
        Object response = requestHandlerServiceImpl.fetchUsingGetWithHeadersProfile(uri, headersValues);
        assertNotNull(response);
        assertEquals(expectedResponse, response);
        verify(restTemplate).exchange(eq(uri), eq(HttpMethod.GET), argThat(entity -> {
            HttpHeaders headers = entity.getHeaders();
            return headers.get("Authorization").contains("Bearer token");
        }), eq(Map.class));
    }

    @Test
    public void testFetchUsingGetWithHeadersProfile_httpClientErrorException() {
        String uri = "http://example.com/api";
        Map<String, String> headersValues = new HashMap<>();
        String errorJson = "{\"error\":\"Not Found\"}";
        HttpClientErrorException exception = HttpClientErrorException.create(
                HttpStatus.NOT_FOUND,
                "Not Found",
                HttpHeaders.EMPTY,
                errorJson.getBytes(),
                null
        );
        when(restTemplate.exchange(eq(uri), eq(HttpMethod.GET), any(HttpEntity.class), eq(Map.class)))
                .thenThrow(exception);
        Object response = requestHandlerServiceImpl.fetchUsingGetWithHeadersProfile(uri, headersValues);
        assertNotNull(response);
        Map<String, Object> responseMap = (Map<String, Object>) response;
        assertEquals("Not Found", responseMap.get("error"));
    }

    @Test
    public void testFetchUsingGetWithHeadersProfile_generalException() {
        String uri = "http://example.com/api";
        Map<String, String> headersValues = new HashMap<>();
        when(restTemplate.exchange(eq(uri), eq(HttpMethod.GET), any(HttpEntity.class), eq(Map.class)))
                .thenThrow(new RuntimeException("Connection error"));
        Object response = requestHandlerServiceImpl.fetchUsingGetWithHeadersProfile(uri, headersValues);
        assertNull(response);
        verify(restTemplate).exchange(eq(uri), eq(HttpMethod.GET), any(HttpEntity.class), eq(Map.class));
    }

    @Test
    public void testFetchUsingGetWithHeadersProfile_nullHeaders() {
        String uri = "http://example.com/api";
        Map<String, Object> expectedResponse = new HashMap<>();
        expectedResponse.put("key", "value");
        ResponseEntity<Map> responseEntity = new ResponseEntity<>(expectedResponse, HttpStatus.OK);
        when(restTemplate.exchange(eq(uri), eq(HttpMethod.GET), any(HttpEntity.class), eq(Map.class)))
                .thenReturn(responseEntity);
        Object response = requestHandlerServiceImpl.fetchUsingGetWithHeadersProfile(uri, null);
        assertNotNull(response);
        assertEquals(expectedResponse, response);
        verify(restTemplate).exchange(eq(uri), eq(HttpMethod.GET), argThat(entity ->
                entity.getHeaders() != null && entity.getBody() == null
        ), eq(Map.class));
    }

    @Test
    public void testFetchResultUsingPost_debugLogging() throws Exception {
        String uri = "http://example.com/api";
        Object request = new Object();
        Map<String, String> headersValues = new HashMap<>();
        headersValues.put("Authorization", "Bearer token");

        // Enable debug logging
        when(mockLogger.isDebugEnabled()).thenReturn(true);

        // Mock RestTemplate
        when(restTemplate.postForObject(anyString(), any(HttpEntity.class), eq(Map.class)))
                .thenReturn(new HashMap<>()); // normal response

        Map<String, Object> response = requestHandlerServiceImpl.fetchResultUsingPost(uri, request, headersValues);

        assertNotNull(response);
        verify(mockLogger, atLeastOnce()).debug(anyString());
    }

    @Test
    public void testFetchUsingGetWithHeadersProfile_successfulResponse_debugLogging() {
        String uri = "http://example.com/api";
        Map<String, String> headersValues = new HashMap<>();
        headersValues.put("Authorization", "Bearer token");

        Map<String, Object> expectedResponse = new HashMap<>();
        expectedResponse.put("key", "value");

        ResponseEntity<Map> responseEntity = new ResponseEntity<>(expectedResponse, HttpStatus.OK);

        when(mockLogger.isDebugEnabled()).thenReturn(true);

        when(restTemplate.exchange(eq(uri), eq(HttpMethod.GET), any(HttpEntity.class), eq(Map.class)))
                .thenReturn(responseEntity);

        Object response = requestHandlerServiceImpl.fetchUsingGetWithHeadersProfile(uri, headersValues);

        assertNotNull(response);
        assertEquals(expectedResponse, response);

        verify(restTemplate).exchange(eq(uri), eq(HttpMethod.GET), argThat(entity -> {
            HttpHeaders headers = entity.getHeaders();
            return headers.get("Authorization").contains("Bearer token");
        }), eq(Map.class));

        verify(mockLogger, atLeastOnce()).debug(anyString());
    }

    /**
     * A request object whose getter throws at serialization time, forcing Jackson's
     * real ObjectMapper (used internally by the production method) to raise a
     * JsonMappingException (a JsonProcessingException) while building the debug log.
     */
    static class BadRequest {
        public String getValue() {
            throw new RuntimeException("boom");
        }
    }

    @Test
    public void testFetchResultUsingPost_realJsonProcessingExceptionDuringDebugLogging() throws Exception {
        String uri = "http://example.com/api";
        BadRequest request = new BadRequest();
        Map<String, String> headersValues = new HashMap<>();

        when(mockLogger.isDebugEnabled()).thenReturn(true);

        Map<String, Object> response = requestHandlerServiceImpl.fetchResultUsingPost(uri, request, headersValues);

        assertNull(response);
        verify(mockLogger, atLeastOnce()).error(anyString());
        verifyNoInteractions(restTemplate);
    }

    @Test
    public void testFetchResultUsingPost_httpClientErrorException_unparseableErrorBody() throws Exception {
        String uri = "http://example.com/api";
        Object request = new Object();
        Map<String, String> headersValues = new HashMap<>();
        String invalidJson = "not-a-json-body";
        HttpClientErrorException exception = HttpClientErrorException.create(
                HttpStatus.BAD_REQUEST,
                "Bad Request",
                HttpHeaders.EMPTY,
                invalidJson.getBytes(),
                null
        );
        when(restTemplate.postForObject(anyString(), any(HttpEntity.class), eq(Map.class)))
                .thenThrow(exception);

        Map<String, Object> response = requestHandlerServiceImpl.fetchResultUsingPost(uri, request, headersValues);

        assertNull(response);
        verify(mockLogger, atLeastOnce()).warn(eq("Failed to parse error response body"), any(Exception.class));
    }

    @Test
    public void testFetchUsingGetWithHeadersProfile_httpClientErrorException_unparseableErrorBody() {
        String uri = "http://example.com/api";
        Map<String, String> headersValues = new HashMap<>();
        String invalidJson = "not-a-json-body";
        HttpClientErrorException exception = HttpClientErrorException.create(
                HttpStatus.BAD_REQUEST,
                "Bad Request",
                HttpHeaders.EMPTY,
                invalidJson.getBytes(),
                null
        );
        when(restTemplate.exchange(eq(uri), eq(HttpMethod.GET), any(HttpEntity.class), eq(Map.class)))
                .thenThrow(exception);

        Object response = requestHandlerServiceImpl.fetchUsingGetWithHeadersProfile(uri, headersValues);

        assertNull(response);
        verify(mockLogger, atLeastOnce()).warn(eq("Failed to parse error response body"), any(Exception.class));
    }

}
