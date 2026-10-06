package com.igot.cb.common;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.igot.cb.util.Constants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class OutboundRequestHandlerServiceImpl {

    private final RestTemplate restTemplate;

    public Object fetchUsingGetWithHeadersProfile(String uri, Map<String, String> headersValues) {
        ObjectMapper mapper = new ObjectMapper();
        mapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
        Map<String, Object> response = null;
        try {
            if (log.isDebugEnabled()) {
                StringBuilder str = new StringBuilder(this.getClass().getCanonicalName())
                        .append(Constants.FETCH_RESULT_CONSTANT).append(System.lineSeparator());
                str.append(Constants.URI_CONSTANT).append(uri).append(System.lineSeparator());
                log.debug(str.toString());
            }
            HttpHeaders headers = new HttpHeaders();
            if (!CollectionUtils.isEmpty(headersValues)) {
                headersValues.forEach(headers::set);
            }
            HttpEntity<Object> entity = new HttpEntity<>(headers);
            response = restTemplate.exchange(uri, HttpMethod.GET, entity, Map.class).getBody();
        } catch (HttpClientErrorException e) {
            try {
                response = (new ObjectMapper()).readValue(e.getResponseBodyAsString(),
                        new TypeReference<HashMap<String, Object>>() {
                        });
            } catch (Exception e1) {
                log.warn("Failed to parse error response body", e1);
            }
            log.error("Error received: {}", e.getResponseBodyAsString(), e);
        } catch (Exception e) {
            log.error(e.getMessage());
            try {
                log.warn("Error Response: " + mapper.writeValueAsString(response));
            } catch (Exception e1) {
                log.warn("Failed to serialize error response for logging", e1);
            }
        }
        return response;
    }

    public Map<String, Object> fetchResultUsingPatch(String uri, Object request, Map<String, String> headersValues) {
        Map<String, Object> response = null;
        try {
            HttpHeaders headers = new HttpHeaders();
            if (!CollectionUtils.isEmpty(headersValues)) {
                headersValues.forEach(headers::set);
            }
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Object> entity = new HttpEntity<>(request, headers);
            if (log.isDebugEnabled()) {
                logDetails(uri, request);
            }
            response = restTemplate.patchForObject(uri, entity, Map.class);
            if (log.isDebugEnabled()) {
                logDetails(uri, response);
            }
        } catch (HttpClientErrorException e) {
            try {
                response = (new ObjectMapper()).readValue(e.getResponseBodyAsString(),
                        new TypeReference<HashMap<String, Object>>() {
                        });
            } catch (Exception e1) {
                log.warn("Failed to parse error response body", e1);
            }
            log.error("Error received: {}", e.getResponseBodyAsString(), e);
        }
        if (response == null) {
            return Collections.emptyMap();
        }
        return response;
    }

    private void logDetails(String uri, Object objectDetails) {
        try {
            StringBuilder str = new StringBuilder(this.getClass().getCanonicalName()).append(".fetchResult")
                    .append(System.lineSeparator());
            str.append("URI: ").append(uri).append(System.lineSeparator());
            str.append("Request/Response: ").append((new ObjectMapper()).writeValueAsString(objectDetails))
                    .append(System.lineSeparator());
            log.debug(str.toString());
        } catch (JsonProcessingException je) {
            log.warn("Failed to serialize details for logging", je);
        }
    }

    public Map<String, Object> fetchResultUsingPost(String uri, Object request, Map<String, String> headerValues) {

        ObjectMapper mapper = new ObjectMapper().configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (!CollectionUtils.isEmpty(headerValues)) {
            headerValues.forEach(headers::set);
        }
        HttpEntity<Object> entity = new HttpEntity<>(request, headers);
        try {
            if (log.isDebugEnabled()) log.debug("Calling POST API | URI: {} | Request: {}", uri, mapper.writeValueAsString(request));
            Map<String, Object> response = restTemplate.postForObject(uri, entity, Map.class);

            if (log.isDebugEnabled()) {
                log.debug("Response: {}", mapper.writeValueAsString(response));
            }
            return response;
        } catch (HttpStatusCodeException ex) {
            log.error("HTTP error while calling URI={}", uri, ex);
            try {
                if (StringUtils.isNotBlank(ex.getResponseBodyAsString())) {
                    return mapper.readValue(ex.getResponseBodyAsString(), new TypeReference<Map<String, Object>>() {});
                }
            } catch (Exception parseEx) {
                log.warn("Failed to parse error response body", parseEx);
            }

        } catch (JsonProcessingException ex) {
            log.error("JSON processing error while calling URI={}", uri, ex);

        } catch (Exception ex) {
            log.error("Unexpected error while calling URI={}", uri, ex);
        }

        return Map.of();
    }
}
