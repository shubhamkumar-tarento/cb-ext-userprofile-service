package com.igot.cb.masterdata.service;

import com.igot.cb.util.ApiResponse;
import com.igot.cb.util.CbServerProperties;
import com.igot.cb.util.Constants;
import com.igot.cb.util.ProjectUtil;
import org.apache.commons.collections4.MapUtils;
import java.util.Collections;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ValidationService {

    private static final String INVALID_REQUEST = "Invalid request";
    private static final int MAX_DESCRIPTION_LENGTH = 255;

    private final CbServerProperties cbServerProperties;

    public boolean validateSearchRequest(ApiResponse apiResponse, Map<String, Object> requestBody) {

        try {
            if (MapUtils.isEmpty(requestBody)) {
                ProjectUtil.errorResponse(apiResponse, INVALID_REQUEST, HttpStatus.BAD_REQUEST);
                return false;
            }
            Map<String, Object> searchRequest = (Map<String, Object>) requestBody.get(Constants.REQUEST);
            if (MapUtils.isEmpty(searchRequest)) {
                searchRequest = new HashMap<>();
                requestBody.put(Constants.REQUEST, searchRequest);
            }
            if (!validatePagination(apiResponse, searchRequest)) {
                return false;
            }
            if (!validateSortBy(apiResponse, searchRequest)) {
                return false;
            }
            String keyword = searchRequest.get(Constants.SEARCH_STRING) != null
                    ? searchRequest.get(Constants.SEARCH_STRING).toString()
                    : null;

            return !StringUtils.isNotBlank(keyword) || validateSearchString(keyword, apiResponse);

        } catch (Exception ex) {
            ProjectUtil.errorResponse(apiResponse, "Invalid request parameters", HttpStatus.BAD_REQUEST);
            return false;
        }
    }

    private boolean validatePagination(ApiResponse apiResponse, Map<String, Object> searchRequest) {
        Object pageObj = searchRequest.get(Constants.PAGE_NUMBER);
        if (ObjectUtils.isNotEmpty(pageObj)) {
            if (!(pageObj instanceof Number)) {
                ProjectUtil.errorResponse(apiResponse, "pageNumber must be numeric", HttpStatus.BAD_REQUEST);
                return false;
            }
            int pageNumber = ((Number) pageObj).intValue();
            if (pageNumber < 0) {
                ProjectUtil.errorResponse(apiResponse, "Invalid pageNumber", HttpStatus.BAD_REQUEST);
                return false;
            }
        }
        Object sizeObj = searchRequest.get(Constants.PAGE_SIZE);
        if (ObjectUtils.isNotEmpty(sizeObj)) {
            if (!(sizeObj instanceof Number)) {
                ProjectUtil.errorResponse(apiResponse, "pageSize must be numeric", HttpStatus.BAD_REQUEST);
                return false;
            }
            int pageSize = ((Number) sizeObj).intValue();
            if (pageSize <= 0) {
                ProjectUtil.errorResponse(apiResponse, "Invalid pageSize", HttpStatus.BAD_REQUEST);
                return false;
            }
        }
        return true;
    }

    private boolean validateSortBy(ApiResponse apiResponse, Map<String, Object> searchRequest) {
        String sortBy = String.valueOf(searchRequest.getOrDefault(Constants.SORT_BY, "")).trim();
        if (!sortBy.isEmpty() && !cbServerProperties.getMasterDataAllowedSortByFields().contains(sortBy)) {
            ProjectUtil.errorResponse(apiResponse, "sortBy field is not allowed", HttpStatus.BAD_REQUEST);
            return false;
        }
        return true;
    }

    public boolean validateSearchString(String keyword, ApiResponse apiResponse) {
        int minSearchLength = Integer.parseInt(cbServerProperties.getMasterDataSearchStringMinLength().trim());
        int maxSearchLength = Integer.parseInt(cbServerProperties.getMasterDataSearchStringMaxLength().trim());
        if (StringUtils.isEmpty(keyword)) {
            return true;
        }
        keyword = keyword.trim();
        // Length validation
        if (keyword.length() < minSearchLength) {
            ProjectUtil.errorResponse(apiResponse,
                    "searchString is too short, Minimum " + minSearchLength + " characters are required.",
                    HttpStatus.BAD_REQUEST);
            return false;
        }
        if (keyword.length() > maxSearchLength) {
            ProjectUtil.errorResponse(apiResponse,
                    "searchString is too long, Maximum " + maxSearchLength + " characters allowed.",
                    HttpStatus.BAD_REQUEST);
            return false;
        }
        //Reject invalid characters such as "??", "@#", etc.
        if (!keyword.matches(cbServerProperties.getMasterDataSearchStringRegex())) {
            ProjectUtil.errorResponse(apiResponse,
                    "searchString contains invalid characters.",
                    HttpStatus.BAD_REQUEST);
            return false;
        }
        return true;
    }

    public boolean upsertDegreeValidation(ApiResponse apiResponse, Map<String, Object> requestBody) {
        Map<String, Object> requestMap = extractRequestMap(apiResponse, requestBody);
        if (MapUtils.isEmpty(requestMap)) {
            return false;
        }
        Object id = requestMap.get(Constants.ID);
        if (ObjectUtils.isNotEmpty(id)) {
            return validateIdAndStatus(apiResponse, id, requestMap.get(Constants.STATUS));
        }
        return validateDegreeNameAndDescription(apiResponse, requestMap);
    }

    private boolean validateDegreeNameAndDescription(ApiResponse apiResponse, Map<String, Object> requestMap) {
        // -------- Validate degree name --------
        String name = extractTrimmedName(requestMap);
        if (StringUtils.isEmpty(name)) {
            ProjectUtil.errorResponse(apiResponse, "Degree name cannot be empty", HttpStatus.BAD_REQUEST);
            return false;
        }
        String degreeRegex = cbServerProperties.getDegreeNameRegex();
        if (StringUtils.isNotBlank(degreeRegex) && !name.matches(degreeRegex)) {
            ProjectUtil.errorResponse(apiResponse, "Degree name contains invalid characters", HttpStatus.BAD_REQUEST);
            return false;
        }
        // -------- Validate degree description (optional) --------
        return validateDescriptionLength(apiResponse, requestMap, "Degree description cannot exceed 255 characters");
    }

    public boolean upsertInstituteValidation(ApiResponse apiResponse, Map<String, Object> requestBody) {
        Map<String, Object> requestMap = extractRequestMap(apiResponse, requestBody);
        if (MapUtils.isEmpty(requestMap)) {
            return false;
        }
        Object id = requestMap.get(Constants.ID);
        if (ObjectUtils.isNotEmpty(id)) {
            return validateIdAndStatus(apiResponse, id, requestMap.get(Constants.STATUS));
        }
        return validateInstituteNameAndDescription(apiResponse, requestMap);
    }

    private boolean validateInstituteNameAndDescription(ApiResponse apiResponse, Map<String, Object> requestMap) {
        // -------- Validate institute name --------
        String name = extractTrimmedName(requestMap);
        if (StringUtils.isEmpty(name)) {
            ProjectUtil.errorResponse(apiResponse, "Institute name cannot be empty", HttpStatus.BAD_REQUEST);
            return false;
        }
        String instituteRegex = cbServerProperties.getInstituteNameRegex();
        if (StringUtils.isNotBlank(instituteRegex) && !name.matches(instituteRegex)) {
            ProjectUtil.errorResponse(apiResponse, "Institute name contains invalid characters", HttpStatus.BAD_REQUEST);
            return false;
        }
        // -------- Validate institute description (optional) --------
        return validateDescriptionLength(apiResponse, requestMap, "Institute description cannot exceed 255 characters");
    }

    /**
     * Validates the top-level requestBody and extracts the nested "request" map.
     * Returns null (after setting the error response) when invalid.
     */
    private Map<String, Object> extractRequestMap(ApiResponse apiResponse, Map<String, Object> requestBody) {
        if (MapUtils.isEmpty(requestBody)) {
            ProjectUtil.errorResponse(apiResponse, INVALID_REQUEST, HttpStatus.BAD_REQUEST);
            return Collections.emptyMap();
        }
        Object reqObj = requestBody.get(Constants.REQUEST);
        if (!(reqObj instanceof Map) || MapUtils.isEmpty((Map<?, ?>) reqObj)) {
            ProjectUtil.errorResponse(apiResponse, INVALID_REQUEST, HttpStatus.BAD_REQUEST);
            return Collections.emptyMap();
        }
        return (Map<String, Object>) reqObj;
    }

    private boolean validateIdAndStatus(ApiResponse apiResponse, Object id, Object statusObj) {
        if (!(id instanceof Number)) {
            ProjectUtil.errorResponse(apiResponse, "ID must be a numeric value", HttpStatus.BAD_REQUEST);
            return false;
        }
        if (ObjectUtils.isNotEmpty(statusObj)) {
            int status;
            try {
                status = (statusObj instanceof Number n) ? n.intValue() : Integer.parseInt(statusObj.toString());
            } catch (NumberFormatException ex) {
                ProjectUtil.errorResponse(apiResponse, "Status must be a valid number", HttpStatus.BAD_REQUEST);
                return false;
            }
            if (status != 0 && status != 1) {
                ProjectUtil.errorResponse(apiResponse, "Status must be 0 or 1", HttpStatus.BAD_REQUEST);
                return false;
            }
        }
        return true;
    }

    private String extractTrimmedName(Map<String, Object> requestMap) {
        Object nameObj = requestMap.get(Constants.NAME);
        return nameObj == null ? "" : String.valueOf(nameObj).trim();
    }

    private boolean validateDescriptionLength(ApiResponse apiResponse, Map<String, Object> requestMap, String errorMessage) {
        Object descObj = requestMap.get(Constants.DESCRIPTION);
        if (ObjectUtils.isNotEmpty(descObj)) {
            String description = String.valueOf(descObj);
            if (description.length() > MAX_DESCRIPTION_LENGTH) {
                ProjectUtil.errorResponse(apiResponse, errorMessage, HttpStatus.BAD_REQUEST);
                return false;
            }
        }
        return true;
    }
}