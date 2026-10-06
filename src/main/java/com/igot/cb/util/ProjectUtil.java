package com.igot.cb.util;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.igot.cb.exceptions.CustomException;
import com.igot.cb.exceptions.ResponseCode;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * This class will contains all the common utility methods.
 *
 * @author Karthikeyan R
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class ProjectUtil {

    private static final PropertiesCache propertiesCache = PropertiesCache.getInstance();

    private final ObjectMapper mapper;

    TypeReference<List<Map<String, Object>>> listOfMapType = new TypeReference<List<Map<String, Object>>>() {
    };

    TypeReference<Map<String, Object>> mapType = new TypeReference<Map<String, Object>>() {
    };

    /**
     * This method will create and return server exception to caller.
     *
     * @param responseCode ResponseCode
     * @return ProjectCommonException
     */
    public static CustomException createServerError(ResponseCode responseCode) {
        return new CustomException(responseCode.getErrorCode(), responseCode.getErrorMessage(),
                ResponseCode.SERVER_ERROR.getStatusCode());
    }

    public static CustomException createClientException(ResponseCode responseCode) {
        return new CustomException(responseCode.getErrorCode(), responseCode.getErrorMessage(),
                ResponseCode.CLIENT_ERROR.getStatusCode());
    }

    public static ApiResponse createDefaultResponse(String api) {
        ApiResponse response = new ApiResponse();
        response.setId(api);
        response.setVer(Constants.API_VERSION_1);
        response.setParams(new ApiRespParam(UUID.randomUUID().toString()));
        response.getParams().setStatus(Constants.SUCCESS);
        response.setResponseCode(HttpStatus.OK);
        response.setTs(java.time.LocalDateTime.now().toString());
        return response;
    }

    public static void errorResponse(ApiResponse response, String errorMessage, HttpStatus httpStatus) {
        response.setResponseCode(httpStatus);
        response.getParams().setErrMsg(errorMessage);
        response.getParams().setStatus(Constants.FAILED);
    }

    public List<Map<String, Object>> parseListOfMap(String json) throws IOException {
        return mapper.readValue(json, listOfMapType);
    }

    public Map<String, Object> parseMap(String json) throws IOException {
        return mapper.readValue(json, mapType);
    }

    public String convertToString(Object object) {
        try {
            return mapper.writeValueAsString(object);
        } catch (IOException e) {
            log.error("Error converting object to string: {}", e.getMessage(), e);
            return null;
        }
    }

    public static String getConfigValue(String key) {
        if (StringUtils.isNotBlank(System.getenv(key))) {
            return System.getenv(key);
        }
        return propertiesCache.readProperty(key);
    }

}
