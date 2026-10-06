package com.igot.cb.masterdata.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.igot.cb.authentication.util.AccessTokenValidator;
import com.igot.cb.transactional.cassandrautils.CassandraOperation;
import com.igot.cb.transactional.redis.cache.CacheService;
import com.igot.cb.util.ApiResponse;
import com.igot.cb.util.Constants;
import com.igot.cb.util.ProjectUtil;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Author: mahesh.vakkund
 */
@Service
@SuppressWarnings("unchecked")
@RequiredArgsConstructor
public class MasterDataServiceImpl implements MasterDataService {

    public static final Logger logger = LoggerFactory.getLogger(MasterDataServiceImpl.class);

    private final AccessTokenValidator accessTokenValidator;

    private final CassandraOperation cassandraOperation;

    private final CacheService redisCacheMgr;

    /**
     * Retrieves a list of all institutions from the master data.
     *
     * @param authToken The authentication token for authorizing the request
     * @return ApiResponse containing the list of institutions if successful,
     *         or an error response if the operation fails
     */
    @Override
    public ApiResponse getInstitutionsList(String authToken) {
        logger.info("MasterDataServiceImpl::getInstitutionsList started");
        ApiResponse response = ProjectUtil.createDefaultResponse(Constants.API_GET_STATE_LIST);
        String userId = accessTokenValidator.fetchUserIdFromAccessToken(authToken);
        if (StringUtils.isEmpty(userId)) {
            updateErrorDetails(response, Constants.USER_ID_DOESNT_EXIST, HttpStatus.BAD_REQUEST);
            return response;
        }
        try {
            Map<String, Object> institutionsMap = getInstitutionsFromCache();
            if (!MapUtils.isEmpty(institutionsMap)) {
                response.getResult().put(Constants.INSTITUTION_LIST, institutionsMap);
                logger.info("MasterDataServiceImpl::getInstitutionsList completed successfully with cached data");
                return response;
            }
            institutionsMap = getInstitutionsFromDatabase();
            if (!MapUtils.isEmpty(institutionsMap)) {
                response.getResult().put(Constants.INSTITUTION_LIST, institutionsMap);
                redisCacheMgr.putCache(Constants.INSTITUTION_LIST, institutionsMap);
                logger.info("MasterDataServiceImpl::getInstitutionsList completed successfully");
            } else {
                response.getResult().put(Constants.INSTITUTION_LIST, List.of());
                logger.info("MasterDataServiceImpl::getInstitutionsList - No institution data found");
            }
        } catch (Exception e) {
            logger.error("Error processing institutions data: {}", e.getMessage(), e);
            updateErrorDetails(response, "Failed to process institutions data: " + e.getMessage(),
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }
        return response;
    }


    /**
     * Retrieves a list of all degrees from the master data.
     *
     * @param authToken The authentication token for authorizing the request
     * @return ApiResponse containing the list of degrees if successful,
     *         or an error response if the operation fails
     */
    @Override
    public ApiResponse getDegreesList(String authToken) {
        logger.info("MasterDataServiceImpl::getDegreesList started");
        ApiResponse response = ProjectUtil.createDefaultResponse(Constants.API_GET_DEGREE_LIST);
        String userId = accessTokenValidator.fetchUserIdFromAccessToken(authToken);
        if (StringUtils.isEmpty(userId)) {
            updateErrorDetails(response, Constants.USER_ID_DOESNT_EXIST, HttpStatus.BAD_REQUEST);
            return response;
        }
        try {
            Map<String, Object> degreesMap = getDegreesFromCache();
            if (!MapUtils.isEmpty(degreesMap)) {
                response.getResult().put(Constants.DEGREES_LIST, degreesMap);
                logger.info("MasterDataServiceImpl::getDegreesList completed successfully with cached data");
                return response;
            }
            degreesMap = getDegreesFromDatabase();
            if (!MapUtils.isEmpty(degreesMap)) {
                response.getResult().put(Constants.DEGREES_LIST, degreesMap);
                redisCacheMgr.putCache(Constants.DEGREES_LIST, degreesMap);
                logger.info("MasterDataServiceImpl::getDegreesList completed successfully");
            } else {
                response.getResult().put(Constants.DEGREES_LIST, List.of());
                logger.info("MasterDataServiceImpl::getDegreesList - No degrees data found");
            }
        } catch (Exception e) {
            logger.error("Error processing degrees data: {}", e.getMessage(), e);
            updateErrorDetails(response, "Failed to process degrees data: " + e.getMessage(),
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }
        return response;
    }

    /**
     * Retrieves institutions from the cache.
     *
     * @return Map of institutions if found, empty map otherwise
     */
    public Map<String, Object> getInstitutionsFromCache() {
        try {
            String institutionsJson = redisCacheMgr.getCache(Constants.INSTITUTION_LIST);
            if (!StringUtils.isEmpty(institutionsJson)) {
                return new ObjectMapper().readValue(institutionsJson, new TypeReference<Map<String, Object>>() {});
            }
        } catch (Exception e) {
            logger.error("Error retrieving institutions from cache: {}", e.getMessage(), e);
        }
        return Map.of();
    }

    /**
     * Retrieves institutions from the database.
     *
     * @return Map of institutions if found, empty map otherwise
     */
    public Map<String, Object> getInstitutionsFromDatabase() {
        try {
            Map<String, Object> properties = new HashMap<>();
            properties.put(Constants.ID, Constants.INSTITUTIONS_CONFIG);
            List<String> fields = new ArrayList<>();
            fields.add(Constants.FIELD_KEY);
            List<Map<String, Object>> rawData = cassandraOperation.getRecordsByPropertiesByKey(
                    Constants.KEYSPACE_SUNBIRD, Constants.SYSTEM_SETTINGS, properties, fields, Constants.ID);
            if (rawData != null && !rawData.isEmpty()) {
                String jsonString = (String) rawData.get(0).get(Constants.FIELD_KEY);
                if (!StringUtils.isEmpty(jsonString)) {
                    return new ObjectMapper().readValue(jsonString, new TypeReference<Map<String, Object>>() {});
                }
            }
        } catch (Exception e) {
            logger.error("Error retrieving institutions from database: {}", e.getMessage(), e);
        }
        return Map.of();
    }

    /**
     * Retrieves degrees from the cache.
     *
     * @return Map of degrees if found, empty map otherwise
     */
    public Map<String, Object> getDegreesFromCache() {
        try {
            String degreesJson = redisCacheMgr.getCache(Constants.DEGREES_LIST);
            if (!StringUtils.isEmpty(degreesJson)) {
                return new ObjectMapper().readValue(degreesJson, new TypeReference<Map<String, Object>>() {});
            }
        } catch (Exception e) {
            logger.error("Error retrieving degrees from cache: {}", e.getMessage(), e);
        }
        return Map.of();
    }

    /**
     * Retrieves degrees from the database.
     *
     * @return Map of degrees if found, empty map otherwise
     */
    public Map<String, Object> getDegreesFromDatabase() {
        try {
            Map<String, Object> properties = new HashMap<>();
            properties.put(Constants.ID, Constants.DEGREES_CONFIG);
            List<String> fields = new ArrayList<>();
            fields.add(Constants.FIELD_KEY);
            List<Map<String, Object>> rawData = cassandraOperation.getRecordsByPropertiesByKey(
                    Constants.KEYSPACE_SUNBIRD, Constants.SYSTEM_SETTINGS, properties, fields, Constants.ID);
            if (rawData != null && !rawData.isEmpty()) {
                String jsonString = (String) rawData.get(0).get(Constants.FIELD_KEY);
                if (!StringUtils.isEmpty(jsonString)) {
                    return new ObjectMapper().readValue(jsonString, new TypeReference<Map<String, Object>>() {});
                }
            }
        } catch (Exception e) {
            logger.error("Error retrieving degrees from database: {}", e.getMessage(), e);
        }
        return Map.of();
    }

    /**
     * Updates the error details in the ApiResponse object.
     *
     * @param response     The ApiResponse object to update
     * @param errorMessage The error message to set
     * @param status       The HTTP status code to set
     */
    public void updateErrorDetails(ApiResponse response, String errorMessage, HttpStatus status) {
        response.getParams().setErrMsg(errorMessage);
        response.getParams().setStatus(Constants.FAILED);
        response.setResponseCode(status);
    }

    /**
     * Updates the institution list in the master data.
     *
     * @param authToken    The authentication token for authorizing the request
     * @param requestBody  The request body containing the institution name to add
     * @return ApiResponse containing the updated institution list if successful,
     *         or an error response if the operation fails
     */
    @Override
    public ApiResponse updateInstitutionList(String authToken, Map<String, Object> requestBody) {
        logger.info("MasterDataServiceImpl::updateInstitutionList started");
        ApiResponse response = ProjectUtil.createDefaultResponse(Constants.API_UPDATE_INSTITUTION_LIST);
        String userId = accessTokenValidator.fetchUserIdFromAccessToken(authToken);
        if (StringUtils.isEmpty(userId)) {
            updateErrorDetails(response, Constants.USER_ID_DOESNT_EXIST, HttpStatus.BAD_REQUEST);
            return response;
        }
        if (!validateInstitutionRequest(requestBody, response)) {
            return response;
        }
        try {
            processInstitutionUpdate(requestBody, response);
        } catch (Exception e) {
            logger.error("Error updating institution: {}", e.getMessage(), e);
            updateErrorDetails(response, "Failed to update institution: " + e.getMessage(),
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }
        return response;
    }



    /**
     * Validates the institution request body.
     *
     * @param requestBody The request body to validate
     * @param response    The ApiResponse object to update with error details if validation fails
     * @return true if the request body is valid, false otherwise
     */
    private boolean validateInstitutionRequest(Map<String, Object> requestBody, ApiResponse response) {
        if (MapUtils.isEmpty(requestBody) || !requestBody.containsKey(Constants.INSTITUTE_NAME) ||
                requestBody.get(Constants.INSTITUTE_NAME) == null) {
            updateErrorDetails(response, "Institution name is required", HttpStatus.BAD_REQUEST);
            return false;
        }
        return true;
    }

    /**
     * Processes the institution update request.
     *
     * @param requestBody The request body containing the institution name to add
     * @param response    The ApiResponse object to update with the result
     */
    protected void processInstitutionUpdate(Map<String, Object> requestBody, ApiResponse response) {
        String institutionName = (String) requestBody.get(Constants.INSTITUTE_NAME);
        Map<String, Object> institutionsMap = retrieveInstitutionsData(response);
        if (response.getResponseCode() != HttpStatus.OK) {
            return;
        }
        List<String> institutionList = getInstitutionList(institutionsMap, response);
        if (response.getResponseCode() != HttpStatus.OK) {
            return;
        }
        if (updateOrAddInstitution(institutionList, institutionName)) {
            try {
                saveInstitutionChangesToDatabaseAndCache(institutionsMap);
                response.getResult().put(Constants.RESPONSE, "Institution added successfully : " + institutionName);
                response.setResponseCode(HttpStatus.CREATED);
                logger.info("MasterDataServiceImpl::updateInstitutionList completed successfully");
            } catch (Exception e) {
                updateErrorDetails(response, "Failed to update institution: " + e.getMessage(),
                        HttpStatus.INTERNAL_SERVER_ERROR);
                logger.error("Error saving institution changes: {}", e.getMessage(), e);
            }
        } else {
            response.getResult().put(Constants.RESPONSE, "Institution already exists");
            response.setResponseCode(HttpStatus.OK);
        }
    }

    /**
     * Retrieves institutions data from cache or database.
     *
     * @param response The ApiResponse object to update with error details if retrieval fails
     * @return Map containing institutions data, or empty map if error occurred
     */
    private Map<String, Object> retrieveInstitutionsData(ApiResponse response) {
        Map<String, Object> institutionsMap = getInstitutionsFromCache();
        if (MapUtils.isEmpty(institutionsMap)) {
            institutionsMap = getInstitutionsFromDatabase();
            if (MapUtils.isEmpty(institutionsMap)) {
                updateErrorDetails(response, "No institutions data found", HttpStatus.NOT_FOUND);
            }
        }
        return institutionsMap;
    }

    /**
     * Extracts institution list from institutions map and validates it.
     *
     * @param institutionsMap Map containing institutions data
     * @param response The ApiResponse object to update with error details if validation fails
     * @return List of institutions, or null if error occurred
     */
    private List<String> getInstitutionList(Map<String, Object> institutionsMap, ApiResponse response) {
        List<String> institutionList = (List<String>) institutionsMap.get(Constants.INSTITUTIONS);
        if (CollectionUtils.isEmpty(institutionList)) {
            logger.error("Invalid institutions data: institutions list is null or not a valid list");
            updateErrorDetails(response, "Invalid institutions data format", HttpStatus.INTERNAL_SERVER_ERROR);
            return Collections.emptyList();
        }
        return institutionList;
    }

    /**
     * Updates or adds an institution to the list of institutions.
     *
     * @param institutions    The list of institutions to update
     * @param institutionName The institution name to add
     * @return true if the institution was added, false if it already existed
     */
    private boolean updateOrAddInstitution(List<String> institutions, String institutionName) {
        boolean exists = institutions.stream()
                .anyMatch(institution -> institution.equals(institutionName));
        if (!exists) {
            institutions.add(institutionName);
            Collections.sort(institutions);
            return true;
        }
        return false;
    }


    /**
     * Saves the updated institution list to the database and cache.
     *
     * @param institutionsMap The map containing the updated institution list
     * @throws JsonProcessingException If there is an error during JSON processing
     */
    public void saveInstitutionChangesToDatabaseAndCache(Map<String, Object> institutionsMap)
            throws JsonProcessingException {
        Map<String, Object> updateMap = new HashMap<>();
        String jsonString = new ObjectMapper().writeValueAsString(institutionsMap);
        updateMap.put(Constants.FIELD_KEY, jsonString);
        updateMap.put(Constants.ID, Constants.INSTITUTIONS_CONFIG);
        cassandraOperation.updateRecord(Constants.KEYSPACE_SUNBIRD, Constants.SYSTEM_SETTINGS, updateMap);
        redisCacheMgr.putCache(Constants.INSTITUTION_LIST, institutionsMap);
    }

    /**
     * Retrieves the list of degrees from the master data.
     *
     * @param authToken The authentication token for authorizing the request
     * @return ApiResponse containing the list of degrees if successful,
     *         or an error response if the operation fails
     */
    @Override
    public ApiResponse updateDegreesList(String authToken, Map<String, Object> requestBody) {
        logger.info("MasterDataServiceImpl::updateDegreesList started");
        ApiResponse response = ProjectUtil.createDefaultResponse(Constants.API_UPDATE_DEGREE_LIST);
        String userId = accessTokenValidator.fetchUserIdFromAccessToken(authToken);
        if (StringUtils.isEmpty(userId)) {
            updateErrorDetails(response, Constants.USER_ID_DOESNT_EXIST, HttpStatus.BAD_REQUEST);
            return response;
        }
        if (!validateDegreeRequest(requestBody, response)) {
            return response;
        }
        try {
            processDegreeUpdate(requestBody, response);
        } catch (Exception e) {
            logger.error("Error updating degree: {}", e.getMessage(), e);
            updateErrorDetails(response, "Failed to update degree: " + e.getMessage(),
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }
        return response;
    }

    /**
     * Validates the degree request body.
     *
     * @param requestBody The request body to validate
     * @param response    The ApiResponse object to update with error details if validation fails
     * @return true if the request body is valid, false otherwise
     */
    protected boolean validateDegreeRequest(Map<String, Object> requestBody, ApiResponse response) {
        if (MapUtils.isEmpty(requestBody) ||
                !requestBody.containsKey(Constants.DEGREE_NAME)) {
            updateErrorDetails(response, "Degree name is required", HttpStatus.BAD_REQUEST);
            return false;
        }
        return true;
    }

    /**
     * Processes the degree update request.
     *
     * @param requestBody The request body containing the degree name to add
     * @param response    The ApiResponse object to update with the result
     */
    protected void processDegreeUpdate(Map<String, Object> requestBody, ApiResponse response) {
        String degreeName = (String) requestBody.get(Constants.DEGREE_NAME);
        Map<String, Object> degreesMap = retrieveDegreesData(response);
        if (response.getResponseCode() != HttpStatus.OK) {
            return;
        }
        List<String> degreesList = getDegreesList(degreesMap, response);
        if (response.getResponseCode() != HttpStatus.OK) {
            return;
        }
        if (updateOrAddDegree(degreesList, degreeName)) {
            try {
                saveDegreeChangesToDatabaseAndCache(degreesMap);
                response.getResult().put(Constants.RESPONSE, "Degree added successfully : " + degreeName);
                response.setResponseCode(HttpStatus.CREATED);
                logger.info("MasterDataServiceImpl::updateDegreesList completed successfully");
            } catch (Exception e) {
                updateErrorDetails(response, "Failed to update degree: " + e.getMessage(),
                        HttpStatus.INTERNAL_SERVER_ERROR);
                logger.error("Error saving degree changes: {}", e.getMessage(), e);
            }
        } else {
            response.getResult().put(Constants.RESPONSE, "Degree already exists");
            response.setResponseCode(HttpStatus.OK);
        }
    }

    /**
     * Retrieves degrees data from cache or database.
     *
     * @param response The ApiResponse object to update with error details if retrieval fails
     * @return Map containing degrees data, or empty map if error occurred
     */
    protected Map<String, Object> retrieveDegreesData(ApiResponse response) {
        Map<String, Object> degreesMap = getDegreesFromCache();
        if (MapUtils.isEmpty(degreesMap)) {
            degreesMap = getDegreesFromDatabase();
            if (MapUtils.isEmpty(degreesMap)) {
                updateErrorDetails(response, "No degrees data found", HttpStatus.NOT_FOUND);
            }
        }
        return degreesMap;
    }

    /**
     * Extracts degree list from degrees map and validates it.
     *
     * @param degreesMap Map containing degrees data
     * @param response The ApiResponse object to update with error details if validation fails
     * @return List of degrees, or null if error occurred
     */
    protected List<String> getDegreesList(Map<String, Object> degreesMap, ApiResponse response) {
        List<String> degreesList = (List<String>) degreesMap.get(Constants.DEGREES);
        if (CollectionUtils.isEmpty(degreesList)) {
            logger.error("Invalid degrees data: degrees list is null or not a valid list");
            updateErrorDetails(response, "Invalid degrees data format", HttpStatus.INTERNAL_SERVER_ERROR);
            return Collections.emptyList();
        }
        return degreesList;
    }

    /**
     * Saves the updated degree list to the database and cache.
     *
     * @param degreesMap The map containing the updated degree list
     * @throws JsonProcessingException If there is an error during JSON processing
     */
    public void saveDegreeChangesToDatabaseAndCache(Map<String, Object> degreesMap)
            throws JsonProcessingException {
        Map<String, Object> updateMap = new HashMap<>();
        String jsonString = new ObjectMapper().writeValueAsString(degreesMap);
        updateMap.put(Constants.FIELD_KEY, jsonString);
        updateMap.put(Constants.ID, Constants.DEGREES_CONFIG);
        cassandraOperation.updateRecord(Constants.KEYSPACE_SUNBIRD, Constants.SYSTEM_SETTINGS, updateMap);
        redisCacheMgr.putCache(Constants.DEGREES_LIST, degreesMap);
    }

    /**
     * Updates or adds a degree to the list of degrees.
     *
     * @param degrees    The list of degrees to update
     * @param degreeName The degree name to add
     * @return true if the degree was added, false if it already existed
     */
    protected boolean updateOrAddDegree(List<String> degrees, String degreeName) {
        boolean exists = degrees.stream()
                .anyMatch(degree -> degree.equals(degreeName));
        if (!exists) {
            degrees.add(degreeName);
            Collections.sort(degrees);
            return true;
        }
        return false;
    }

}