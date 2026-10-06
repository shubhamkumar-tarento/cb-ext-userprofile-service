package com.igot.cb.profile.service;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.igot.cb.authentication.util.AccessTokenValidator;
import com.igot.cb.common.KafkaEventPublisher;
import com.igot.cb.exceptions.CustomException;
import com.igot.cb.profile.model.CompetencyAcquiredEvent;
import com.igot.cb.profile.model.CompetencyEventWrapper;
import com.igot.cb.transactional.cassandrautils.CassandraOperation;
import com.igot.cb.transactional.elasticsearch.dto.SearchCriteria;
import com.igot.cb.transactional.elasticsearch.dto.SearchResult;
import com.igot.cb.transactional.elasticsearch.service.EsClientService;
import com.igot.cb.transactional.redis.cache.CacheService;
import com.igot.cb.util.ApiResponse;
import com.igot.cb.util.CbServerProperties;
import com.igot.cb.util.Constants;
import com.igot.cb.util.ProjectUtil;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class AchievementServiceImpl implements AchievementService{

    private List<String> requiredFields;

    private final AccessTokenValidator accessTokenValidator;

    private final CbServerProperties cbServerProperties;

    private final CassandraOperation cassandraOperation;

    private final EsClientService esClientService;

    private final ObjectMapper objectMapper;

    private final RedisTemplate<String, SearchResult> redisTemplate;

    private static final String FIELD_REASON = "reason";

    private static final String FIELD_LEARNER_ID = "learnerId";

    private static final String ACHIEVEMENT_CACHE_PREFIX = "user:achievement";

    private static final String ERR_INVALID_ACCESS_TOKEN = "Invalid or missing access token";

    private static final String DATE_FORMAT_PATTERN = "yyyy-MM-dd'T'HH:mm:ss.SSSZ";

    private final CacheService cacheService;

    private final KafkaEventPublisher kafkaEventPublisher ;

    /**
     * Inner class to represent competency delta
     * Contains lists of unchanged, added, and removed competencies
     */
    private static class CompetencyDelta {
        List<Map<String, String>> unchanged = List.of();
        List<Map<String, String>> added = List.of();
        List<Map<String, String>> removed = List.of();
    }

    @PostConstruct
    private void initRequiredFields() {
        requiredFields = Arrays.asList(cbServerProperties.getRequiredFieldsProperty().split(","));
    }

    @Override
    public ApiResponse createLearnerAchievement(Map<String, Object> request, String userToken, String orgId) {
        ApiResponse response = ProjectUtil.createDefaultResponse("api.learnerAchievement.create");

        Map<String, Object> requestData = (Map<String, Object>) request.get(Constants.REQUEST);
        String userId = accessTokenValidator.fetchUserIdFromAccessToken(userToken);
        if (StringUtils.isEmpty(userId)) {
            ProjectUtil.errorResponse(response, "User Id not Found", HttpStatus.BAD_REQUEST);
            return response;
        }
        String validationError = validateRequetData(requestData);
        if (StringUtils.isNotBlank(validationError)) {
            ProjectUtil.errorResponse(response, validationError, HttpStatus.BAD_REQUEST);
            return response;
        }
        String id = UUID.randomUUID().toString();
        Map<String, Object> achievementRecord = formAchievementRecord(orgId, userId, requestData, id);

        // Save into Cassandra
        if (!saveAchievementToCassandra(achievementRecord)) {
            ProjectUtil.errorResponse(response,
                    "Failed to save learner achievement info",
                    HttpStatus.INTERNAL_SERVER_ERROR);
            return response;
        }
        if (cbServerProperties.isRequireEs()) {
            Map<String, Object> esRecord = new HashMap<>(achievementRecord);
            Map<String, Object> map = objectMapper.convertValue(esRecord, Map.class);
            esClientService.addDocument(Constants.LEARNER_ACHIEVEMENT_INDEX, Constants.INDEX_TYPE, id, map, cbServerProperties.getAchievementEsRequiredFieldsMappingPath());
            // Refresh search cache for this user after creation
            refreshAchievementSearchCacheForUser(userId);
        }
        // Cache record
        cacheService.putCache(
                buildCacheKey(ACHIEVEMENT_CACHE_PREFIX, userId, (String) requestData.get(Constants.CONTEXT_TYPE), id),
                achievementRecord, cbServerProperties.getAchievementCacheTtl()
        );
        // Publish competency event for creation
        publishCompetencyEvent(userId, id, (String) requestData.get(Constants.CONTEXT_TYPE),
                (Map<String, Object>) requestData.get(Constants.CONTEXT_DATA), "");
        response.setResponseCode(HttpStatus.OK);
        response.setResponse(achievementRecord);
        fetchAndCacheUserAchievements(userId);
        return response;
    }

    private Map<String, Object> formAchievementRecord(String orgId, String userId, Map<String, Object> requestData, String id) {
        Map<String, Object> achievementRecord = new HashMap<>();
        achievementRecord.put(Constants.USER_ID_RQST, userId);
        achievementRecord.put(Constants.CONTEXT_TYPE, requestData.get(Constants.CONTEXT_TYPE));
        achievementRecord.put(Constants.ID, id);
        achievementRecord.put(Constants.ORG_ID, orgId);
        achievementRecord.put(Constants.SOURCE, requestData.get(Constants.SOURCE));
        achievementRecord.put(Constants.CONTEXT_DATA, requestData.get(Constants.CONTEXT_DATA));
        achievementRecord.put(Constants.STATUS, Constants.APPROVED);
        achievementRecord.put(Constants.CREATED_ON, Instant.now());
        return achievementRecord;
    }

    @Override
    public ApiResponse updateLearnerAchievement(Map<String, Object> request, String userToken, String orgId) {
        ApiResponse response = ProjectUtil.createDefaultResponse("api.learnerAchievement.update");
        Map<String, Object> requestData = (Map<String, Object>) request.get(Constants.REQUEST);
        String validateMessage = validateRequetData(requestData);
        if (StringUtils.isNotBlank(validateMessage)) {
            ProjectUtil.errorResponse(response, validateMessage, HttpStatus.BAD_REQUEST);
            return response;
        }
        String userId = accessTokenValidator.fetchUserIdFromAccessToken(userToken);
        if (StringUtils.isEmpty(userId)) {
            ProjectUtil.errorResponse(response, "User Id not Found", HttpStatus.BAD_REQUEST);
            return response;
        }

        String id = (String) requestData.get(Constants.ID);
        String contextType = (String) requestData.get(Constants.CONTEXT_TYPE);
        Map<String, Object> newContextData = (Map<String, Object>) requestData.get(Constants.CONTEXT_DATA);

        ApiResponse validationResponse = validateUpdateRequest(response, id, contextType, newContextData);
        if (validationResponse != null) {
            return validationResponse;
        }

        Map<String, Object> existingRecord = getAchievementFromCassandra(userId, contextType, id);
        if (MapUtils.isEmpty(existingRecord)) {
            ProjectUtil.errorResponse(response, "Achievement records not found", HttpStatus.NOT_FOUND);
            return response;
        }
        boolean isUrlChanged = hasUrlChanges(existingRecord, newContextData);
        java.time.Instant updateOnTimestamp = java.time.Instant.now();

        // Compute delta-based comparison for competencies
        CompetencyDelta competencyDelta = computeCompetencyDelta(existingRecord, newContextData);

        updateExistingRecord(existingRecord, requestData, userId, updateOnTimestamp);
        boolean isSaved = saveAchievementToCassandra(existingRecord);
        if (!isSaved) {
            ProjectUtil.errorResponse(response, "Failed to update learner achievement", HttpStatus.INTERNAL_SERVER_ERROR);
            return response;
        }
        if (cbServerProperties.isRequireEs()) {
            updateAchievementInElasticsearch(id, existingRecord, userId, updateOnTimestamp);
        }
        cacheService.putCache(buildCacheKey(ACHIEVEMENT_CACHE_PREFIX, userId, contextType, id), existingRecord,cbServerProperties.getAchievementCacheTtl());

        // Publish competency update event only if there are actual changes (added or removed)
        if (hasCompetencyChanges(competencyDelta) || isUrlChanged) {
            publishCompetencyDeltaEvent(userId, id, contextType, competencyDelta, Constants.UPDATE, isUrlChanged, existingRecord);
        }
        response.setResponseCode(HttpStatus.OK);
        response.setResponse(existingRecord);
        fetchAndCacheUserAchievements(userId);
        return response;
    }

    @Override
    public ApiResponse readLearnerAchievement(String achievementId, String userToken, String contextType) {
        ApiResponse response = ProjectUtil.createDefaultResponse("api.learnerAchievement.read");
        String userId = accessTokenValidator.fetchUserIdFromAccessToken(userToken);
        if (StringUtils.isBlank(userId)) {
            ProjectUtil.errorResponse(response, "UserId not Found", HttpStatus.BAD_REQUEST);
            return response;
        }
        if (StringUtils.isBlank(achievementId)) {
            ProjectUtil.errorResponse(response, "achievementId is mandatory", HttpStatus.BAD_REQUEST);
            return response;
        }
        Map<String, Object> achievement = null;
        if (StringUtils.isNotBlank(contextType)) {
            String cacheKey = buildCacheKey(ACHIEVEMENT_CACHE_PREFIX, userId, contextType, achievementId);
            achievement = getAchievementFromCache(cacheKey);
        }
        if (MapUtils.isEmpty(achievement)) {
            achievement = getAndCacheAchievementFromCassandra(userId, contextType, achievementId);
        }
        if (MapUtils.isEmpty(achievement)) {
            ProjectUtil.errorResponse(response, "Achievement not found", HttpStatus.NOT_FOUND);
            return response;
        }

        response.setResponseCode(HttpStatus.OK);
        response.setResponse(achievement);
        return response;
    }

    @Override
    public ApiResponse deleteLearnerAchievement(Map<String, Object> request, String userToken) {
        ApiResponse response = ProjectUtil.createDefaultResponse("api.learnerAchievement.delete");
        if (request == null || !(request.get(Constants.REQUEST) instanceof Map)) {
            ProjectUtil.errorResponse(response, "Missing or invalid 'request' object in payload", HttpStatus.BAD_REQUEST);
            return response;
        }
        Map<String, Object> reqMap = (Map<String, Object>) request.get(Constants.REQUEST);
        String achievementId = (String) reqMap.get(Constants.ID);
        String contextType = (String) reqMap.get(Constants.CONTEXT_TYPE);
        if (StringUtils.isBlank(achievementId) || StringUtils.isBlank(contextType)) {
            ProjectUtil.errorResponse(response, "achievementId and contextType are mandatory", HttpStatus.BAD_REQUEST);
            return response;
        }
        String userId = accessTokenValidator.fetchUserIdFromAccessToken(userToken);
        if (StringUtils.isBlank(userId)) {
            ProjectUtil.errorResponse(response, "UserId not Found", HttpStatus.BAD_REQUEST);
            return response;
        }

        // Fetch the achievement record before deletion to get competency details for Kafka event
        Map<String, Object> existingRecord = getAchievementFromCassandra(userId, contextType, achievementId);

        // Delete from Cassandra
        Map<String, Object> compositeKey = new HashMap<>();
        compositeKey.put(Constants.ID, achievementId);
        compositeKey.put(Constants.USER_ID_RQST, userId);
        compositeKey.put(Constants.CONTEXT_TYPE, contextType);
        Map<String, Object> cassandraResponse = cassandraOperation.deleteRecordByCompositeKey(
                Constants.KEYSPACE_SUNBIRD,
                Constants.LEARNER_ACHIEVEMENT_TABLE,
                compositeKey
        );
        if (!Constants.SUCCESS.equals(cassandraResponse.get(Constants.RESPONSE))) {
            ProjectUtil.errorResponse(response, "Failed to delete achievement record", HttpStatus.INTERNAL_SERVER_ERROR);
            return response;
        }
        // Remove from cache
        String cacheKey = buildCacheKey(ACHIEVEMENT_CACHE_PREFIX, userId, contextType, achievementId);
        cacheService.removeCache(cacheKey);
        // Remove from ES
        if (cbServerProperties.isRequireEs()) {
            try {
                esClientService.deleteDocument(achievementId, Constants.LEARNER_ACHIEVEMENT_INDEX);
                // Refresh search cache for this user after deletion
                refreshAchievementSearchCacheForUser(userId);
            } catch (Exception e) {
                log.warn("Failed to delete achievement from ES for id {}", achievementId, e);
            }
        }

        // Publish competency delete event only if competencies_v6 is present and non-empty
        Map<String, Object> contextData = extractContextData(existingRecord);
        Object competenciesObj = contextData.get(Constants.COMPETENCIES_V6);

        // Check if competencies_v6 exists and is a non-empty List
        if (competenciesObj instanceof List && CollectionUtils.isNotEmpty((List<?>) competenciesObj)) {
            publishCompetencyEvent(userId, achievementId, contextType, contextData, Constants.DELETE);
        }

        response.setResponseCode(HttpStatus.OK);
        response.getResult().put("message", "Achievement deleted successfully");
        // Refresh user achievements cache after deletion
        fetchAndCacheUserAchievements(userId);
        return response;
    }

    @Override
    public ApiResponse statusUpdateLearnerAchievement(Map<String, Object> request, String authToken) {
        log.info("AchievementService::statusUpdateLearnerAchievement");
        ApiResponse response = ProjectUtil.createDefaultResponse(Constants.API_ACHIEVEMENT_STATUS_UPDATE);
        try {
            String userIdFromToken = accessTokenValidator.fetchUserIdFromAccessToken(authToken);
            if (StringUtils.isBlank(userIdFromToken)) {
                ProjectUtil.errorResponse(response, ERR_INVALID_ACCESS_TOKEN, HttpStatus.UNAUTHORIZED);
                return response;
            }
            if (!validateStatusUpdateRequest(request, response)) {
                return response;
            }
            Map<String, Object> reqMap = (Map<String, Object>) request.get(Constants.REQUEST);
            Map<String, Object> compositeKey = new HashMap<>();
            compositeKey.put(Constants.ID, reqMap.get(Constants.ID));
            compositeKey.put(Constants.USER_ID_LOWER, reqMap.get(FIELD_LEARNER_ID));
            compositeKey.put(Constants.FIELD_CONTEXT_TYPE, reqMap.get(Constants.CONTEXT_TYPE_KEY));
            List<Map<String, Object>> records = cassandraOperation.getAllRecordsByPrimaryKey(
                Constants.KEYSPACE_SUNBIRD,
                Constants.LEARNER_ACHIEVEMENT_TABLE,
                compositeKey,
                null,
                Constants.CASSANDRA_FETCH_LIMIT
            );
            if (CollectionUtils.isEmpty(records)) {
                ProjectUtil.errorResponse(response, "Achievement record not found for update", HttpStatus.NOT_FOUND);
                return response;
            }
            // Additional validation: status must be PENDING
            String currentStatus = String.valueOf(records.get(0).get(Constants.STATUS));
            if (!Constants.PENDING.equalsIgnoreCase(currentStatus)) {
                ProjectUtil.errorResponse(response, "Achievement status must be 'PENDING' to update. Current status: " + currentStatus, HttpStatus.BAD_REQUEST);
                return response;
            }
            Map<String, Object> updateAttributes = new HashMap<>();
            updateAttributes.put(Constants.STATUS, reqMap.get(Constants.STATUS));
            updateAttributes.put(FIELD_REASON, reqMap.get(FIELD_REASON));
            updateAttributes.put(Constants.FIELD_APPROVED_BY, userIdFromToken);
            // Store approvedon as date (yyyy-MM-dd) for Cassandra
            java.time.Instant approvedOnTimestamp = java.time.Instant.now();
            updateAttributes.put(Constants.FIELD_APPROVED_ON, approvedOnTimestamp);
            Map<String, Object> cassandraResponse = cassandraOperation.updateRecordByCompositeKey(
                Constants.KEYSPACE_SUNBIRD,
                Constants.LEARNER_ACHIEVEMENT_TABLE,
                updateAttributes,
                compositeKey
            );
            if (!Constants.SUCCESS.equals(cassandraResponse.get(Constants.RESPONSE))) {
                ProjectUtil.errorResponse(response, String.valueOf(cassandraResponse.get(Constants.ERROR_MESSAGE)), HttpStatus.INTERNAL_SERVER_ERROR);
                return response;
            }
            updateAchievementInES(records, reqMap, userIdFromToken, approvedOnTimestamp);
            response.getResult().put("message", "Achievement status updated successfully");
        } catch (Exception e) {
            log.error("Exception in statusUpdateLearnerAchievement", e);
            ProjectUtil.errorResponse(response, "Exception occurred: " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
        return response;
    }

    private void updateAchievementInES(List<Map<String, Object>> records, Map<String, Object> reqMap, String userIdFromToken, java.time.Instant approvedOnTimestamp) {
        try {
            Map<String, Object> esUpdateMap = buildEsUpdateMapFromRecord(records);
            Map<String, Object> esDoc = esClientService.readDocument(Constants.LEARNER_ACHIEVEMENT_INDEX, reqMap.get(Constants.ID).toString());

            String createdOnFormatted = resolveCreatedOnFormatted(esDoc, records);
            String updatedOn = (String) esDoc.get(Constants.UPDATED_ON);
            if (StringUtils.isNotBlank(updatedOn)) {
                esUpdateMap.put(Constants.UPDATED_ON, updatedOn);
            }
            esUpdateMap.put(Constants.CREATED_ON, createdOnFormatted);
            esUpdateMap.put(Constants.STATUS, reqMap.get(Constants.STATUS));
            esUpdateMap.put(FIELD_REASON, reqMap.get(FIELD_REASON));
            esUpdateMap.put(Constants.FIELD_APPROVED_BY_ES, userIdFromToken);
            esUpdateMap.put(Constants.FIELD_APPROVED_ON_ES, approvedOnTimestamp);
            esClientService.updateDocument(
                Constants.LEARNER_ACHIEVEMENT_INDEX,
                null,
                String.valueOf(reqMap.get(Constants.ID)),
                esUpdateMap,
                cbServerProperties.getAchievementEsRequiredFieldsMappingPath()
            );
        } catch (Exception e) {
            log.error("Exception while updating achievement in ES", e);
        }
    }

    /**
     * Builds the ES update map from the latest Cassandra record, normalizing date fields
     * to strings and parsing the contextData field appropriately.
     */
    private Map<String, Object> buildEsUpdateMapFromRecord(List<Map<String, Object>> records) {
        Map<String, Object> esUpdateMap = new HashMap<>();
        if (CollectionUtils.isEmpty(records)) {
            return esUpdateMap;
        }
        Map<String, Object> dbRecord = records.get(0);
        for (Map.Entry<String, Object> entry : dbRecord.entrySet()) {
            Object value = entry.getValue();
            if (value instanceof java.time.LocalDate || value instanceof java.time.LocalDateTime) {
                esUpdateMap.put(entry.getKey(), value.toString());
            } else if ("contextdata".equalsIgnoreCase(entry.getKey()) && value != null) {
                esUpdateMap.put(entry.getKey(), parseContextDataForEs(value));
            } else {
                esUpdateMap.put(entry.getKey(), value);
            }
        }
        return esUpdateMap;
    }

    /**
     * Parses the contextData value (String JSON or Map) into a Map suitable for ES indexing.
     * Extracted out of the per-entry loop above to keep a single try block per method (S1141).
     */
    private Map<String, Object> parseContextDataForEs(Object value) {
        if (value instanceof String contextDataJson) {
            try {
                return objectMapper.readValue(contextDataJson, Map.class);
            } catch (Exception ex) {
                log.warn("Failed to parse contextData string to Map for ES. Storing as empty object.", ex);
                return new HashMap<>();
            }
        } else if (value instanceof Map<?, ?> contextDataMap) {
            return (Map<String, Object>) contextDataMap;
        }
        return new HashMap<>();
    }

    /**
     * Resolves the createdOn value to use for ES, preferring the existing ES document's value,
     * and falling back to the Cassandra record's createdOn (formatting LocalDate when needed).
     */
    private String resolveCreatedOnFormatted(Map<String, Object> esDoc, List<Map<String, Object>> records) {
        if (MapUtils.isNotEmpty(esDoc) && esDoc.get(Constants.CREATED_ON) instanceof String createdOnStr) {
            return createdOnStr;
        }
        // fallback to existingRecord if ES not found
        Object createdOnObj = records.get(0).get(Constants.CREATED_ON);
        if (createdOnObj instanceof String createdOnStr) {
            return createdOnStr;
        } else if (createdOnObj instanceof LocalDate createdOnDate) {
            return createdOnDate
                    .atStartOfDay(ZoneId.of("UTC"))
                    .format(DateTimeFormatter.ofPattern(DATE_FORMAT_PATTERN));
        }
        return null;
    }

    private boolean validateStatusUpdateRequest(Map<String, Object> request, ApiResponse response) {
        if (MapUtils.isEmpty(request) || !(request.get(Constants.REQUEST) instanceof Map) || MapUtils.isEmpty((Map<?, ?>) request.get(Constants.REQUEST))) {
            ProjectUtil.errorResponse(response, "Missing or invalid 'request' object in payload", HttpStatus.BAD_REQUEST);
            return false;
        }
        Map<String, Object> reqMap = (Map<String, Object>) request.get(Constants.REQUEST);
        for (String field : requiredFields) {
            if (!reqMap.containsKey(field) || reqMap.get(field) == null) {
                ProjectUtil.errorResponse(response, "Missing required field: " + field, HttpStatus.BAD_REQUEST);
                return false;
            }
        }
        String statusValue = String.valueOf(reqMap.get(Constants.STATUS));
        if (!Constants.APPROVED_KEY.equalsIgnoreCase(statusValue) && !Constants.REJECTED.equalsIgnoreCase(statusValue)) {
            ProjectUtil.errorResponse(response, "Invalid status value. Allowed values are 'Approved' or 'Reject'", HttpStatus.BAD_REQUEST);
            return false;
        }
        return true;
    }

    @Override
    public ApiResponse searchLearnerAchievements(SearchCriteria searchCriteria, String authToken) {
        log.info("AchievementService::searchLearnerAchievements");
        ApiResponse response = ProjectUtil.createDefaultResponse(Constants.API_ACHIEVEMENT_SEARCH);
        String cacheKey = generateRedisJwtTokenKey(searchCriteria);
        log.info(cacheKey);
        SearchResult searchResult = redisTemplate.opsForValue().get(cacheKey);
        if (searchResult != null) {
            log.info("DiscussionServiceImpl::searchDiscussion:  search result fetched from redis");
            response.getResult().put(Constants.SEARCH_RESULTS, searchResult);
            return response;
        }
        String searchString = searchCriteria.getSearchString();
        if (searchString != null && !searchString.isEmpty() && searchString.length() < 3) {
            ProjectUtil.errorResponse(response, Constants.MINIMUM_CHARACTERS_NEEDED, HttpStatus.BAD_REQUEST);
            return response;
        }
        try {
            log.info("DiscussionServiceImpl::searchDiscussion:  search result fetched from es");
            if (MapUtils.isEmpty(searchCriteria.getFilterCriteriaMap())) {
                searchCriteria.setFilterCriteriaMap(new HashMap<>());
            }
            searchResult = esClientService.searchDocuments(Constants.LEARNER_ACHIEVEMENT_INDEX, searchCriteria);
            if (CollectionUtils.isEmpty(searchResult.getData())) {
                ProjectUtil.errorResponse(response, Constants.NO_DATA_FOUND, HttpStatus.OK);
                response.getResult().put(Constants.SEARCH_RESULTS, searchResult);
                return response;
            }
            List<Map<String, Object>> achievemnets = searchResult.getData();
            searchResult.setUserDetails(fetchUsernamesFromSearchData(achievemnets));
            searchResult.setData(achievemnets);
            redisTemplate.opsForValue().set(cacheKey, searchResult, cbServerProperties.getSearchResultRedisTtl(), TimeUnit.SECONDS);
            response.getResult().put(Constants.SEARCH_RESULTS, searchResult);
            return response;
        } catch (Exception e) {
            log.error("error while searching discussion : {} .", e.getMessage(), e);
            ProjectUtil.errorResponse(response, e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
            return response;
        }
    }

    /**
     * Extracts unique userIds from search result data and fetches usernames for each.
     * @param data List of search result maps (each representing a record)
     * @return Map of userId to username
     */
    private Map<String, String> fetchUsernamesFromSearchData(List<Map<String, Object>> data) {
        Set<String> uniqueUserIds = new HashSet<>();
        for (Map<String, Object> item : data) {
            Object userIdObj = item.get(Constants.USER_ID);
            if (StringUtils.isEmpty((String) userIdObj)) userIdObj = item.get(Constants.USER_ID_LOWER);
            if (userIdObj instanceof String userIdStr && StringUtils.isNotBlank(userIdStr)) {
                uniqueUserIds.add(userIdStr);
            }
        }
        // Fetch user details (replace with actual Redis/Cassandra logic)
        List<Object> userDetailsList = fetchUserDetails(new ArrayList<>(uniqueUserIds));
        Map<String, String> userIdToUsername = new HashMap<>();
        for (Object user : userDetailsList) {
            if (user instanceof Map<?, ?> userMap) {
                Object idObj = userMap.get(Constants.USER_ID_KEY);
                Object nameObj = userMap.get(Constants.FIRST_NAME_KEY);
                if (idObj instanceof String idStr && nameObj instanceof String nameStr) {
                    userIdToUsername.put(idStr, nameStr);
                }
            }
        }
        return userIdToUsername;
    }

    private List<Object> fetchUserDetails(List<String> userIds) {
        // Prepare Redis keys (assuming prefix is needed)
        List<String> redisKeys = userIds.stream()
            .map(id -> Constants.USER_PREFIX + id)
            .toList();
        // Fetch values for all keys from Redis
        List<Object> redisResults = cacheService.hget(redisKeys); // Use your cacheService
        // Build userDetailsMap from redis results
        Map<String, Object> userDetailsMap = redisResults.stream()
                .filter(Objects::nonNull)
                .map(user -> (Map<String, Object>) user)
                .filter(user -> user.get(Constants.USER_ID_KEY) != null)
                .collect(Collectors.toMap(
                        user -> user.get(Constants.USER_ID_KEY).toString(),
                        user -> user,
                        (u1, u2) -> u1));
        // Find missing userIds
        List<String> missingUserIds = userIds.stream()
                .filter(id -> !userDetailsMap.containsKey(id))
                .toList();
        // Fetch from Cassandra if missing
        if (!missingUserIds.isEmpty()) {
            List<Object> cassandraResults = fetchUserFromPrimary(missingUserIds);
            userDetailsMap.putAll(cassandraResults.stream()
                    .map(user -> (Map<String, Object>) user)
                    .filter(user -> user.get(Constants.USER_ID_KEY) != null)
                    .collect(Collectors.toMap(
                            user -> user.get(Constants.USER_ID_KEY).toString(),
                            user -> user,
                            (u1, u2) -> u1)));
        }
        return new ArrayList<>(userDetailsMap.values());
    }

    public List<Object> fetchUserFromPrimary(List<String> userIds) {
        log.info("AchievementServiceImpl::fetchUserFromPrimary: Fetching user data from Cassandra");
        Map<String, Object> propertyMap = new HashMap<>();
        propertyMap.put(Constants.ID, userIds);
        List<Map<String, Object>> userInfoList = cassandraOperation.getRecordsByPropertiesWithoutFiltering(
                Constants.KEYSPACE_SUNBIRD, Constants.USER_TABLE, propertyMap,
                Arrays.asList(Constants.FIRST_NAME, Constants.ID), null);
        return userInfoList.stream()
                .map(userInfo -> {
                    Map<String, Object> userMap = new HashMap<>();
                    String userId = (String) userInfo.get(Constants.ID);
                    String userName = (String) userInfo.get(Constants.FIRST_NAME_CAMEL_CASE);
                    userMap.put(Constants.USER_ID_KEY, userId);
                    userMap.put(Constants.FIRST_NAME_KEY, userName);
                    return userMap;
                })
                .collect(Collectors.toList());
    }

    public String generateRedisJwtTokenKey(Object requestPayload) {
        if (requestPayload != null) {
            try {
                String reqJsonString = objectMapper.writeValueAsString(requestPayload);
                return JWT.create()
                        .withClaim(Constants.REQUEST, reqJsonString)
                        .sign(Algorithm.HMAC256(cbServerProperties.getAchievementJwtSecretKey()));
            } catch (JsonProcessingException e) {
                log.error("Error occurred while converting json object to json string", e);
            }
        }
        return "";
    }

    private String validateRequetData(Map<String, Object> requestData) {
        //  check that every field in the whole request body is an allowed field
        String allowedFieldsError = validateAllowedFields(requestData);
        if (StringUtils.isNotBlank(allowedFieldsError)) {
            return allowedFieldsError;
        }

        String requestContextType = (String) requestData.get(Constants.CONTEXT_TYPE);
        String[] configuredContextType = cbServerProperties.getContextType();
        if (StringUtils.isBlank(requestContextType)) {
            return "contextType is missing in request";
        }

        boolean isValid = Arrays.stream(configuredContextType)
                .anyMatch(ct -> ct.equalsIgnoreCase(requestContextType));
        if (!isValid) {
            return "Invalid contextType. Allowed values: " + String.join(",", configuredContextType);
        }
        Map<String, Object> contextData =
                (Map<String, Object>) requestData.get(Constants.CONTEXT_DATA);

        if (MapUtils.isEmpty(contextData)|| contextData.isEmpty()) {
            return "contextData is missing in request";
        }
        String requiredFieldsConfig = cbServerProperties.getAchievementsMandatoryFields();
        String[] mandatoryFields = requiredFieldsConfig.split(",");

        //  Validate mandatory fields
        for (String field : mandatoryFields) {
            Object value = contextData.get(field.trim());
            if (value == null) {
                return field + " is mandatory and missing";
            }
            if (value instanceof String valueStr && StringUtils.isBlank(valueStr)) {
                return field + " is mandatory and cannot be empty";
            }
        }
        return null;
    }

    private boolean saveAchievementToCassandra(Map<String, Object> achievementRecord) {
        try {
            Map<String, Object> query = new HashMap<>(achievementRecord);
            String contextDataJson = objectMapper.writeValueAsString(
                    achievementRecord.get(Constants.CONTEXT_DATA)
            );
            query.put(Constants.CONTEXT_DATA, contextDataJson);

            ApiResponse insertResponse = (ApiResponse) cassandraOperation.insertRecord(
                    Constants.KEYSPACE_SUNBIRD,
                    Constants.LEARNER_ACHIEVEMENT_TABLE,
                    query
            );
            return Constants.SUCCESS.equalsIgnoreCase(
                    (String) insertResponse.get(Constants.RESPONSE)
            );
        } catch (Exception e) {
            log.error("Failed to insert learner achievement", e);
            return false;
        }
    }

    private String buildCacheKey(String prefix, String userId, String contextType, String id) {
        return String.join(":", prefix, contextType, userId, id);
    }

    private Map<String, Object> getAchievementFromCassandra(String userId, String contextType, String id) {
        try {
            Map<String, Object> propertyMap = new HashMap<>();
            propertyMap.put(Constants.USER_ID_RQST, userId);
            propertyMap.put(Constants.CONTEXT_TYPE, contextType);
            propertyMap.put(Constants.ID, id);
            List<String> fields = new ArrayList<>();

            List<Map<String, Object>> result =
                    cassandraOperation.getRecordsByPropertiesByKey(
                            Constants.KEYSPACE_SUNBIRD,
                            Constants.LEARNER_ACHIEVEMENT_TABLE,
                            propertyMap,
                            fields,
                            Constants.USERID_KEY
                    );
            if (result == null || result.isEmpty()) {
                return Collections.emptyMap();
            }
            Map<String, Object> achievementRecord = result.get(0);

            // Convert contextdata JSON string back to Map
            Object contextDataObj = achievementRecord.get(Constants.CONTEXT_DATA);
            if (contextDataObj instanceof String contextDataJson) {
                Map<String, Object> contextData =
                        objectMapper.readValue(contextDataJson, Map.class);
                achievementRecord.put(Constants.CONTEXT_DATA, contextData);
            }
            return achievementRecord;

        } catch (Exception e) {
            log.error("Failed to fetch learner achievement for userId={}, contextType={}, id={}",
                    userId, contextType, id, e);
            return Collections.emptyMap();
        }
    }

    private Map<String, Object> getAchievementFromCache(String cacheKey) {
        String cachedJson = cacheService.getCache(cacheKey);
        if (StringUtils.isNotBlank(cachedJson)) {
            try {
                log.info("reading from cache {}", cacheKey);
                return objectMapper.readValue(cachedJson, new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {
                });
            } catch (Exception e) {
                log.error("Failed to deserialize cached achievement for key {}", cacheKey, e);
            }
        }
        return Collections.emptyMap();
    }

    private Map<String, Object> getAndCacheAchievementFromCassandra(String userId, String contextType, String achievementId) {
        Map<String, Object> achievement = getAchievementFromCassandra(userId, contextType, achievementId);
        if (MapUtils.isNotEmpty(achievement) && StringUtils.isNotBlank(contextType)) {
            try {
                String achievementJson = objectMapper.writeValueAsString(achievement);
                cacheService.putCache(buildCacheKey(ACHIEVEMENT_CACHE_PREFIX, userId, contextType, achievementId), achievementJson,cbServerProperties.getAchievementCacheTtl());
            } catch (Exception e) {
                log.error("Failed to serialize achievement for caching", e);
            }
        }
        return achievement;
    }

    /**
     * Invalidate search cache for first five pages for a given userId after ES update.
     * This will generate the search payload for each page, generate the cache key, and delete it from Redis.
     * @param userId the userId for which to invalidate the cache
     */

    // Utility method to build a default SearchCriteria for a user and page, matching the search API structure
    private SearchCriteria buildDefaultSearchCriteriaForUser(String userId, int pageNumber) {
        SearchCriteria searchCriteria = new SearchCriteria();
        HashMap<String, Object> filterCriteriaMap = new HashMap<>();
        filterCriteriaMap.put(Constants.USER_ID, userId);
        searchCriteria.setFilterCriteriaMap(filterCriteriaMap);
        searchCriteria.setRequestedFields(null); // Set to null, not empty list
        searchCriteria.setPageNumber(pageNumber);
        searchCriteria.setPageSize(Constants.DEFAULT_PAGE_SIZE);
        searchCriteria.setOrderBy(Constants.DEFAULT_ORDER_BY);
        searchCriteria.setOrderDirection(Constants.DEFAULT_ORDER_DIRECTION);
        searchCriteria.setFacets(new ArrayList<>(Arrays.asList(Constants.STATUS))); // Use ArrayList, not singleton
        searchCriteria.setSearchString(null);
        searchCriteria.setQuery(null);
        searchCriteria.setStartsWith(null);
        searchCriteria.setStartsWithField(null);
        return searchCriteria;
    }

    private void refreshAchievementSearchCacheForUser(String userId) {
        try {
            for (int pageNumber = 0; pageNumber < Constants.ACHIEVEMENT_SEARCH_CACHE_PAGES; pageNumber++) {
                SearchCriteria searchCriteria = buildDefaultSearchCriteriaForUser(userId, pageNumber);
                String cacheKey = generateRedisJwtTokenKey(searchCriteria);
                SearchResult searchResult = esClientService.searchDocuments(Constants.LEARNER_ACHIEVEMENT_INDEX, searchCriteria);
                if (CollectionUtils.isEmpty(searchResult.getData())) {
                    continue;
                }
                List<Map<String, Object>> achievements = searchResult.getData();
                searchResult.setUserDetails(fetchUsernamesFromSearchData(achievements));
                searchResult.setData(achievements);
                redisTemplate.opsForValue().set(cacheKey, searchResult, cbServerProperties.getSearchResultRedisTtl(), TimeUnit.SECONDS);
                log.info("Refreshed achievement search cache for userId: {} page: {} key: {}", userId, pageNumber, cacheKey);
            }
        } catch (Exception e) {
            log.error("Exception while refreshing achievement search cache for userId: {}", userId, e);
            throw new CustomException("error refreshing achievement search cache",
                    "Failed to refresh achievement search cache for userId: " + userId,
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @Override
    public ApiResponse getUserAchievements(String authToken, String id) {
        log.info("AchievementService::getUserAchievements");
        ApiResponse response = ProjectUtil.createDefaultResponse(Constants.API_ACHIEVEMENT_LIST);
        String userId = accessTokenValidator.fetchUserIdFromAccessToken(authToken);
        if (StringUtils.isBlank(userId)) {
            ProjectUtil.errorResponse(response, ERR_INVALID_ACCESS_TOKEN, HttpStatus.UNAUTHORIZED);
            return response;
        }
        if (StringUtils.isNotBlank(id)) {
            userId = id;
        }
        try {
            String cacheKey = Constants.ACHIEVEMENTS_REDIS_KEY + userId;
            String cachedJson = cacheService.getCache(cacheKey);
            if (StringUtils.isNotBlank(cachedJson)) {
                log.info("AchievementServiceImpl::getUserAchievements: fetched from redis");
                Map<String, Object> cachedSearchResults = objectMapper.readValue(cachedJson, Map.class);
                response.getResult().put(Constants.SEARCH_RESULTS, cachedSearchResults);
                response.setResponseCode(HttpStatus.OK);
                return response;
            } else {
                Map<String, Object> searchResults = fetchAndCacheUserAchievements(userId);
                response.getResult().put(Constants.SEARCH_RESULTS, searchResults);
                response.setResponseCode(HttpStatus.OK);
            }
        } catch (Exception e) {
            log.error("Exception while fetching user achievements for userId: {}", userId, e);
            ProjectUtil.errorResponse(response, "Failed to fetch achievements: " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
        return response;
    }


    private Map<String, Object> fetchAndCacheUserAchievements(String userId) {

        Map<String, Object> propertyMap = Map.of(
                Constants.USER_ID_LOWER, userId,
                Constants.FIELD_CONTEXT_TYPE, Constants.ACHIEVEMENTS
        );

        List<Map<String, Object>> achievements =
                cassandraOperation.getRecordsByPropertiesWithoutFiltering(
                        Constants.KEYSPACE_SUNBIRD,
                        Constants.LEARNER_ACHIEVEMENT_TABLE,
                        propertyMap,
                        null,
                        cbServerProperties.getCassandraFetchLimit()
                );

        List<Map<String, Object>> processedAchievements =
                Optional.ofNullable(achievements)
                        .orElse(List.of())
                        .stream()
                        .map(this::processAchievement)
                        .sorted(Comparator.comparing(
                                achievement -> parseCreatedOn(achievement.get(Constants.CREATED_ON)),
                                Comparator.nullsLast(Comparator.reverseOrder())
                        ))
                        .toList();

        Map<String, Object> searchResults = Map.of(
                Constants.DATA, processedAchievements,
                Constants.TOTAL_COUNT, processedAchievements.size()
        );

        cacheService.putCache(Constants.ACHIEVEMENTS_REDIS_KEY + userId, searchResults);

        return searchResults;
    }



    /**
     * Validates update request fields for updateLearnerAchievement
     * @return ApiResponse if validation fails, null if validation passes
     */
    private ApiResponse validateUpdateRequest(ApiResponse response, String id, String contextType, Map<String, Object> newContextData) {
        if (StringUtils.isBlank(id) || StringUtils.isBlank(contextType)) {
            ProjectUtil.errorResponse(response, "id and contextType are mandatory", HttpStatus.BAD_REQUEST);
            return response;
        }
        if (MapUtils.isEmpty(newContextData)) {
            ProjectUtil.errorResponse(response, "contextData is mandatory for update", HttpStatus.BAD_REQUEST);
            return response;
        }
        if (!newContextData.containsKey(Constants.UPLOAD_DOCUMENT_URL) || !newContextData.containsKey(Constants.URL)) {
            ProjectUtil.errorResponse(
                    response,
                    "Both uploadedDocumentUrl and url fields must be present",
                    HttpStatus.BAD_REQUEST
            );
            return response;
        }

        String uploadedDocumentUrl = (String) newContextData.get(Constants.UPLOAD_DOCUMENT_URL);
        String url = (String) newContextData.get(Constants.URL);

        if (uploadedDocumentUrl == null || url == null) {
            ProjectUtil.errorResponse(
                    response,
                    "uploadedDocumentUrl and url must not be null (can be empty string)",
                    HttpStatus.BAD_REQUEST
            );
            return response;
        }

        if (StringUtils.isBlank(uploadedDocumentUrl) && StringUtils.isBlank(url)) {
            ProjectUtil.errorResponse(
                    response,
                    "Either uploadedDocumentUrl or url must have a value",
                    HttpStatus.BAD_REQUEST
            );
            return response;
        }

        boolean isUploadedBlank = StringUtils.isBlank(uploadedDocumentUrl);
        boolean isUrlBlank = StringUtils.isBlank(url);

        if (isUploadedBlank == isUrlBlank) {
            ProjectUtil.errorResponse(
                    response,
                    "Exactly one of uploadedDocumentUrl or url must have a value",
                    HttpStatus.BAD_REQUEST
            );
            return response;
        }

        return null;
    }

    /**
     * Updates the existing record with new context data and metadata
     */
    private void updateExistingRecord(Map<String, Object> existingRecord, Map<String, Object> requestData, String userId, java.time.Instant updateOnTimestamp) {
        if (Objects.isNull(existingRecord) || Objects.isNull(requestData)) {
            return;
        }
        requestData.forEach((key, value) -> {
            // ignore null values
            if (Objects.isNull(value)) {
                return;
            }
            // merge contextData instead of replacing
            if (Constants.CONTEXT_DATA.equals(key) && value instanceof Map<?, ?> newContextMap) {

                Map<String, Object> existingContext =
                        (Map<String, Object>) existingRecord.get(Constants.CONTEXT_DATA);

                if (Objects.isNull(existingContext)) {
                    existingContext = new HashMap<>();
                }
                final Map<String, Object> finalExistingContext = existingContext;

                newContextMap.forEach((ctxKey, ctxValue) -> {
                    if (Objects.nonNull(ctxValue)) {
                        finalExistingContext.put(ctxKey.toString(), ctxValue);
                    }
                });
                existingRecord.put(Constants.CONTEXT_DATA, finalExistingContext);
            } else {
                // normal update (contextType, source etc.)
                existingRecord.put(key, value);
            }

        });
        // update metadata
        existingRecord.put(Constants.UPDATED_BY, userId);
        existingRecord.put(Constants.UPDATED_ON, updateOnTimestamp);
    }

    /**
     * Updates achievement in Elasticsearch with formatted timestamps
     */
    private void updateAchievementInElasticsearch(String id, Map<String, Object> existingRecord, String userId, java.time.Instant updateOnTimestamp) {
        Map<String, Object> esDoc = esClientService.readDocument(Constants.LEARNER_ACHIEVEMENT_INDEX, id);
        String createdOnFormatted = getFormattedCreatedOn(esDoc, existingRecord);

        Map<String, Object> esRecord = new HashMap<>(existingRecord);
        esRecord.put(Constants.CREATED_ON, createdOnFormatted);
        esRecord.put(Constants.UPDATED_ON, updateOnTimestamp);
        esRecord.put(Constants.UPDATED_BY, userId);

        Map<String, Object> map = objectMapper.convertValue(esRecord, Map.class);
        esClientService.updateDocument(Constants.LEARNER_ACHIEVEMENT_INDEX, Constants.INDEX_TYPE, id, map, cbServerProperties.getAchievementEsRequiredFieldsMappingPath());
        refreshAchievementSearchCacheForUser((String) existingRecord.get(Constants.USER_ID_RQST));
    }

    /**
     * Retrieves formatted createdOn timestamp from ES document or existing record
     */
    private String getFormattedCreatedOn(Map<String, Object> esDoc, Map<String, Object> existingRecord) {
        if (MapUtils.isNotEmpty(esDoc) && esDoc.get(Constants.CREATED_ON) instanceof String createdOnStr) {
            return createdOnStr;
        }

        Object createdOnObj = existingRecord.get(Constants.CREATED_ON);
        if (createdOnObj instanceof String createdOnStr) {
            return createdOnStr;
        }
        if (createdOnObj instanceof LocalDate createdOnDate) {
            return createdOnDate
                    .atStartOfDay(ZoneId.of("UTC"))
                    .format(DateTimeFormatter.ofPattern(DATE_FORMAT_PATTERN));
        }
        return null;
    }

    private Map<String, Object> processAchievement(Map<String, Object> achievement) {

        normalizeContextData(achievement);

        normalizeDateField(achievement, Constants.CREATED_ON);
        normalizeDateField(achievement, Constants.UPDATED_ON);
        normalizeDateField(achievement, Constants.FIELD_APPROVED_ON);

        return achievement;
    }

    private void normalizeContextData(Map<String, Object> achievement) {

        Object contextData = achievement.get(Constants.CONTEXT_DATA);

        if (contextData instanceof String contextStr) {
            try {
                Map<String, Object> parsed =
                        objectMapper.readValue(contextStr, Map.class);
                achievement.put(Constants.CONTEXT_DATA, parsed);
            } catch (Exception e) {
                log.warn("Failed to parse contextData", e);
                achievement.put(Constants.CONTEXT_DATA, Map.of());
            }
        }
    }

    private void normalizeDateField(Map<String, Object> achievement, String fieldName) {

        Object value = achievement.get(fieldName);

        if (value instanceof LocalDate localDate) {
            achievement.put(fieldName, localDate.toString());
        } else if (value instanceof LocalDateTime localDateTime) {
            achievement.put(fieldName, localDateTime.toString());
        } else if (value instanceof Instant instant) {
            achievement.put(fieldName, instant.toString());
        }
    }

    private Instant parseCreatedOn(Object value) {

        if (value instanceof Instant instant) {
            return instant;
        }
        if (value instanceof String str) {
            try {
                return Instant.parse(str);
            } catch (Exception e) {
                // not a parsable Instant string; fall through and return null below
                log.debug("Unable to parse createdOn value as Instant: {}", str);
            }
        }

        return null;
    }

    /**
     * Publishes competency delta event to Kafka with ONLY changed competencies (added and removed)
     * Unchanged competencies are NOT included in the event as per review feedback
     * The event includes action field for each competency that was added or removed
     *
     * @param userId        The user ID
     * @param achievementId The achievement ID
     * @param contextType   The context type
     * @param delta         The CompetencyDelta containing unchanged, added, and removed competencies
     * @param action        The main action type (UPDATE, DELETE, etc.)
     */
    private void publishCompetencyDeltaEvent(String userId,
                                             String achievementId,
                                             String contextType,
                                             CompetencyDelta delta,
                                             String action,
                                             boolean isUrlChanged,
                                             Map<String, Object> existingRecord) {
        try {
            // ONLY include changed competencies (added and removed), NOT unchanged
            List<Map<String, String>> changedCompetencies = new ArrayList<>();
            changedCompetencies.addAll(delta.added);
            changedCompetencies.addAll(delta.removed);

            if (isUrlChanged) {
                List<Map<String, String>> urlChangedCompetencies =
                        buildChangeUrlCompetencies(existingRecord);

                Set<String> removedKeys = delta.removed.stream()
                        .map(this::buildKey)
                        .collect(Collectors.toSet());

                urlChangedCompetencies = urlChangedCompetencies.stream()
                        .filter(comp -> !removedKeys.contains(buildKey(comp)))
                        .toList();

                changedCompetencies.addAll(urlChangedCompetencies);
            }

            if (changedCompetencies.isEmpty()) {
                log.debug("No changed competencies to publish for achievementId: {}", achievementId);
                return;
            }

            CompetencyAcquiredEvent event = CompetencyAcquiredEvent.builder()
                    .eventType(Constants.EVENT_TYPE_COMPETENCY_ACQUIRED)
                    .userId(userId)
                    .contentId(achievementId)
                    .batchId("")
                    .contextType(contextType)
                    .action(StringUtils.isNotBlank(action) ? action : null)
                    .competencyIds(changedCompetencies)
                    .build();

            // Wrap the event in edata structure
            CompetencyEventWrapper wrapper = CompetencyEventWrapper.builder()
                    .edata(event)
                    .build();

            kafkaEventPublisher.publish(
                    cbServerProperties.getUserCompetencyTopicName(),
                    wrapper,
                    String.format("userId: %s, contentId: %s, action: %s, added: %d, removed: %d",
                            userId,
                            achievementId,
                            action,
                            delta.added.size(),
                            delta.removed.size())
            );

            log.info("Published competency delta event for achievementId: {} with {} added, {} removed (unchanged excluded)",
                    achievementId, delta.added.size(), delta.removed.size());

        } catch (Exception e) {
            log.error("Failed to publish competency delta event", e);
        }
    }

    /**
     * Consolidated method to publish competency events to Kafka for create, update, and delete operations
     * This method handles all three operations by parameterizing the action type
     * Both action and competency fields are only included in the event if they have non-empty values
     *
     * @param userId        The user ID
     * @param achievementId The achievement ID (content ID)
     * @param contextType   The context type
     * @param contextData   The context data containing competency information
     * @param action        The action type: "create", "update", or "delete" (optional)
     */
    private void publishCompetencyEvent(String userId,
                                        String achievementId,
                                        String contextType,
                                        Map<String, Object> contextData,
                                        String action) {

        try {

            CompetencyAcquiredEvent.CompetencyAcquiredEventBuilder eventBuilder =
                    CompetencyAcquiredEvent.builder()
                            .eventType(Constants.EVENT_TYPE_COMPETENCY_ACQUIRED)
                            .userId(userId)
                            .contentId(achievementId)
                            .batchId("")
                            .contextType(contextType);

            if (StringUtils.isNotBlank(action)) {
                eventBuilder.action(action);
            }

            // Only for update/delete
            if (Constants.UPDATE.equalsIgnoreCase(action)
                    || Constants.DELETE.equalsIgnoreCase(action)) {

                List<Map<String, String>> competencies =
                        extractCompetencies(contextData);

                eventBuilder.competencyIds(competencies);
            }

            CompetencyAcquiredEvent event = eventBuilder.build();

            // Wrap the event in edata structure
            CompetencyEventWrapper wrapper = CompetencyEventWrapper.builder()
                    .edata(event)
                    .build();

            kafkaEventPublisher.publish(
                    cbServerProperties.getUserCompetencyTopicName(),
                    wrapper,
                    String.format("userId: %s, contentId: %s, action: %s",
                            userId,
                            achievementId,
                            action)
            );

        } catch (Exception e) {
            log.error("Failed to publish competency event", e);
        }
    }

    /**
     * Computes delta-based comparison for competencies between existing and new context data
     * Returns a CompetencyDelta object containing unchanged, added, and removed competencies
     *
     * @param existingRecord The existing achievement record
     * @param newContextData The new context data from request
     * @return CompetencyDelta object with unchanged, added, and removed competencies
     */
    private CompetencyDelta computeCompetencyDelta(Map<String, Object> existingRecord, Map<String, Object> newContextData) {
        if (existingRecord == null || newContextData == null) {
            return new CompetencyDelta();
        }

        // Extract existing competency from context data
        Object existingContextObj = existingRecord.get(Constants.CONTEXT_DATA);
        Map<String, Object> existingContextData = null;

        if (existingContextObj instanceof Map<?, ?> map) {
            existingContextData = convertToStringObjectMap(map);
        } else if (existingContextObj instanceof String json) {
            try {
                existingContextData = objectMapper.readValue(json, Map.class);
            } catch (Exception e) {
                log.warn("Failed to parse existing context data", e);
                return new CompetencyDelta();
            }
        }

        if (existingContextData == null) {
            existingContextData = new HashMap<>();
        }

        // Extract competencies_v6 from both datasets
        List<Map<String, Object>> existingCompetencies = extractCompetenciesV6List(existingContextData);
        List<Map<String, Object>> newCompetencies = extractCompetenciesV6List(newContextData);

        // Convert to maps for set-based comparison
        Map<String, Map<String, Object>> existingCompetencyMap = competenciesListToMap(existingCompetencies);
        Map<String, Map<String, Object>> newCompetencyMap = competenciesListToMap(newCompetencies);

        // Compute delta
        return computeDelta(existingCompetencyMap, newCompetencyMap);
    }

    /**
     * Extracts competencies_v6 list from context data
     *
     * @param contextData The context data map
     * @return List of competency maps
     */
    private List<Map<String, Object>> extractCompetenciesV6List(Map<String, Object> contextData) {
        if (contextData == null) {
            return List.of();
        }
        Object competenciesObj = contextData.get(Constants.COMPETENCIES_V6);
        if (competenciesObj instanceof List<?> list) {
            return list.stream()
                    .filter(Map.class::isInstance)
                    .map(item -> (Map<String, Object>) item)
                    .toList();
        }
        return List.of();
    }

    /**
     * Converts competencies list to a map keyed by unique competency identifier
     * Key format: areaId|themeId|subThemeId (case-insensitive)
     *
     * @param competencies List of competency maps
     * @return Map with unique key as key and competency map as value
     */
    private Map<String, Map<String, Object>> competenciesListToMap(List<Map<String, Object>> competencies) {
        return competencies.stream()
                .collect(Collectors.toMap(
                        this::buildCompetencyKey,
                        competency -> competency,
                        (existing, duplicate) -> existing,
                        LinkedHashMap::new
                ));
    }

    /**
     * Builds unique competency key from a competency map
     * Format: areaId|themeId|subThemeId (all lowercase for case-insensitive comparison)
     *
     * @param competency The competency map
     * @return Unique key string
     */
    private String buildCompetencyKey(Map<String, Object> competency) {
        String areaId = getStringValueFromObject(competency.get(Constants.COMPETENCY_AREA_REF_ID)).toLowerCase();
        String themeId = getStringValueFromObject(competency.get(Constants.COMPETENCY_THEME_REF_ID)).toLowerCase();
        String subThemeId = getStringValueFromObject(competency.get(Constants.COMPETENCY_SUB_THEME_REF_ID)).toLowerCase();
        return String.join("|", areaId, themeId, subThemeId);
    }

    /**
     * Computes delta between existing and new competency maps
     * Uses Set-based comparison to identify unchanged, added, and removed competencies
     *
     * @param existingMap Map of existing competencies
     * @param newMap      Map of new competencies
     * @return CompetencyDelta containing the differences
     */
    private CompetencyDelta computeDelta(Map<String, Map<String, Object>> existingMap,
                                         Map<String, Map<String, Object>> newMap) {
        CompetencyDelta delta = new CompetencyDelta();

        Set<String> existingKeys = existingMap.keySet();
        Set<String> newKeys = newMap.keySet();

        // Unchanged: keys present in both
        delta.unchanged = existingKeys.stream()
                .filter(newKeys::contains)
                .map(key -> buildCompetencyIdMap(newMap.get(key), null))
                .toList();

        // Added: keys in new but not in existing
        delta.added = newKeys.stream()
                .filter(key -> !existingKeys.contains(key))
                .map(key -> buildCompetencyIdMap(newMap.get(key), Constants.ADDED))
                .toList();

        // Removed: keys in existing but not in new
        delta.removed = existingKeys.stream()
                .filter(key -> !newKeys.contains(key))
                .map(key -> buildCompetencyIdMap(existingMap.get(key), Constants.REMOVED))
                .toList();

        return delta;
    }

    /**
     * Builds a competency ID map with extracted fields
     *
     * @param competency The competency map
     * @param action     The action type (null for unchanged, "added", or "removed")
     * @return Map with competencyAreaId, competencyThemeId, competencySubThemeId, and optional action
     */
    private Map<String, String> buildCompetencyIdMap(Map<String, Object> competency, String action) {
        Map<String, String> competencyIdMap = new LinkedHashMap<>();
        competencyIdMap.put(Constants.COMPETENCY_AREA_ID,
                getStringValueFromObject(competency.get(Constants.COMPETENCY_AREA_REF_ID)));
        competencyIdMap.put(Constants.COMPETENCY_THEME_ID,
                getStringValueFromObject(competency.get(Constants.COMPETENCY_THEME_REF_ID)));
        competencyIdMap.put(Constants.COMPETENCY_SUB_THEME_ID,
                getStringValueFromObject(competency.get(Constants.COMPETENCY_SUB_THEME_REF_ID)));

        if (StringUtils.isNotBlank(action)) {
            competencyIdMap.put(Constants.ACTION, action);
        }
        return competencyIdMap;
    }

    /**
     * Checks if there are any competency changes (added or removed)
     *
     * @param delta The CompetencyDelta object
     * @return true if there are changes, false otherwise
     */
    private boolean hasCompetencyChanges(CompetencyDelta delta) {
        return !delta.added.isEmpty() || !delta.removed.isEmpty();
    }

    /**
     * Extracts context data from an achievement record
     * Handles both Map and JSON String formats
     *
     * @param achievementRecord The achievement record
     * @return The extracted context data as a map, or empty map if parsing fails
     */
    private Map<String, Object> extractContextData(Map<String, Object> achievementRecord) {
        if (achievementRecord == null) {
            return new HashMap<>();
        }

        Object contextDataObj = achievementRecord.get(Constants.CONTEXT_DATA);

        if (contextDataObj instanceof Map) {
            return (Map<String, Object>) contextDataObj;
        } else if (contextDataObj instanceof String contextDataJson) {
            try {
                return objectMapper.readValue(contextDataJson, Map.class);
            } catch (Exception e) {
                log.warn("Failed to parse context data from achievement record", e);
                return new HashMap<>();
            }
        }

        return new HashMap<>();
    }

    private List<Map<String, String>> extractCompetencies(Map<String, Object> contextData) {
        List<Map<String, String>> competencyList = new ArrayList<>();

        Object competenciesObj = contextData != null
                ? contextData.get(Constants.COMPETENCIES_V6)
                : null;

        if (competenciesObj instanceof List<?> list) {

            for (Object obj : list) {
                if (obj instanceof Map<?, ?> map) {
                    Map<String, String> competencyMap = new HashMap<>();

                    competencyMap.put(Constants.COMPETENCY_AREA_ID,
                            getStringValueFromObject(map.get(Constants.COMPETENCY_AREA_REF_ID)));

                    competencyMap.put(Constants.COMPETENCY_THEME_ID,
                            getStringValueFromObject(map.get(Constants.COMPETENCY_THEME_REF_ID)));

                    competencyMap.put(Constants.COMPETENCY_SUB_THEME_ID,
                            getStringValueFromObject(map.get(Constants.COMPETENCY_SUB_THEME_REF_ID)));

                    competencyList.add(competencyMap);
                }
            }
        }
        return competencyList;
    }

    private String getStringValueFromObject(Object value) {
        if (value instanceof String str) {
            return str;
        }
        return "";
    }

    private Map<String, Object> convertToStringObjectMap(Map<?, ?> source) {
        Map<String, Object> result = new HashMap<>();
        for (Map.Entry<?, ?> entry : source.entrySet()) {
            if (entry.getKey() instanceof String key) {
                result.put(key, entry.getValue());
            }
        }
        return result;
    }

    /**
     * Validates that every field present in the entire request body (including nested maps and
     * list elements) is present in the configured allowed-fields list.
     * If an unknown field is found a human-readable error message is returned; otherwise null.
     *
     * @param requestBody the full request body map received from the caller
     * @return validation error message or null when all fields are allowed
     */
    private String validateAllowedFields(Map<String, Object> requestBody) {
        String allowedFieldsConfig = cbServerProperties.getAchievementsAllowedFields();
        if (StringUtils.isBlank(allowedFieldsConfig)) {
            // config not set – skip this check
            return null;
        }
        Set<String> allowedFields = Arrays.stream(allowedFieldsConfig.split(","))
                .map(String::trim)
                .filter(StringUtils::isNotBlank)
                .collect(Collectors.toSet());

        return validateFieldsRecursively(requestBody, allowedFields);
    }

    /**
     * Recursively walks through a request node (Map, List, or scalar) and checks that
     * every Map key is present in {@code allowedFields}.
     *
     * @param node          the current node being inspected
     * @param allowedFields the set of permitted field names loaded from config
     * @return the first invalid field error found, or null if all fields are allowed
     */
    private String validateFieldsRecursively(Object node, Set<String> allowedFields) {
        if (node == null) {
            return null;
        }
        if (node instanceof Map<?, ?> map) {
            return validateMapFields(map, allowedFields);
        } else if (node instanceof List<?> list) {
            return validateListFields(list, allowedFields);
        }
        return null;
    }

    /**
     * Validates that every key of the given map is in {@code allowedFields}, recursing into
     * nested values. Extracted from {@link #validateFieldsRecursively} to keep its cognitive
     * complexity low.
     */
    private String validateMapFields(Map<?, ?> map, Set<String> allowedFields) {
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (!(entry.getKey() instanceof String key)) {
                continue;
            }
            if (!allowedFields.contains(key)) {
                return "Invalid field in request: '" + key + "'. Only configured fields are allowed.";
            }
            String childError = validateFieldsRecursively(entry.getValue(), allowedFields);
            if (StringUtils.isNotBlank(childError)) {
                return childError;
            }
        }
        return null;
    }

    /**
     * Validates that every element of the given list recursively satisfies {@code allowedFields}.
     * Extracted from {@link #validateFieldsRecursively} to keep its cognitive complexity low.
     */
    private String validateListFields(List<?> list, Set<String> allowedFields) {
        for (Object item : list) {
            String childError = validateFieldsRecursively(item, allowedFields);
            if (StringUtils.isNotBlank(childError)) {
                return childError;
            }
        }
        return null;
    }

    @Override
    public ApiResponse getUserAchievementsByUserIds(String authToken, Map<String, Object> request) {
        log.info("AchievementService::getUserAchievementsByAchievementIds");
        ApiResponse response = ProjectUtil.createDefaultResponse(Constants.API_ACHIEVEMENT_V2_LIST);
        String userId = accessTokenValidator.fetchUserIdFromAccessToken(authToken);
        if (StringUtils.isBlank(userId)) {
            ProjectUtil.errorResponse(response, ERR_INVALID_ACCESS_TOKEN, HttpStatus.UNAUTHORIZED);
            return response;
        }
        List<String> achievementIds = null;
        if (request.get(Constants.REQUEST) instanceof Map<?, ?> requestMap &&
                requestMap.get(Constants.ACHIEVEMENT_IDS) instanceof List<?> ids) {

            achievementIds = ids.stream()
                    .map(Object::toString)
                    .toList();
        }
        if (CollectionUtils.isEmpty(achievementIds)) {
            ProjectUtil.errorResponse(response, "achievementIds list is mandatory and cannot be empty", HttpStatus.BAD_REQUEST);
            return response;
        }
        // Load both config sets once per request
        Set<String> responseFields = loadConfiguredFields(cbServerProperties.getBulkListResponseFields());
        Set<String> contextDataFields = loadConfiguredFields(cbServerProperties.getBulkListContextDataFields());
        try {
            List<Map<String, Object>> result = new ArrayList<>();
            for (String achievementId : achievementIds) {
                if (StringUtils.isBlank(achievementId)) {
                    continue;
                }
                String cacheKey = buildCacheKey(ACHIEVEMENT_CACHE_PREFIX, userId, Constants.ACHIEVEMENTS, achievementId);
                Map<String, Object> achievement = getAchievementFromCache(cacheKey);
                if (MapUtils.isNotEmpty(achievement)) {
                    log.info("AchievementServiceImpl::getUserAchievementsByUserIds: fetched from cache for achievementId: {}", achievementId);
                } else {
                    log.info("AchievementServiceImpl::getUserAchievementsByUserIds: fetching from DB for achievementId: {}", achievementId);
                    achievement = getAndCacheAchievementFromCassandra(userId, Constants.ACHIEVEMENTS, achievementId);
                }
                if (MapUtils.isNotEmpty(achievement)) {
                    result.add(applyBulkListFilters(achievement, responseFields, contextDataFields));
                }
            }
            Map<String, Object> searchResults = new HashMap<>();
            searchResults.put(Constants.DATA, result);
            searchResults.put(Constants.TOTAL_COUNT, result.size());
            response.getResult().put(Constants.SEARCH_RESULTS, searchResults);
            response.setResponseCode(HttpStatus.OK);
        } catch (Exception e) {
            log.error("Exception while fetching achievements for userId: {}, achievementIds: {}", userId, achievementIds, e);
            ProjectUtil.errorResponse(response, "Failed to fetch achievements: " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
        return response;
    }

    /**
     * Parses a comma-separated config string into a Set of trimmed field names.
     * Returns an empty set if the config is blank, which means "return all fields".
     */
    private Set<String> loadConfiguredFields(String config) {
        if (StringUtils.isBlank(config)) {
            return Collections.emptySet();
        }
        return Arrays.stream(config.split(","))
                .map(String::trim)
                .filter(StringUtils::isNotBlank)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /**
     * Applies both filters to an achievement map:
     * 1. Top-level field filter  – keeps only fields listed in responseFields     (if non-empty)
     * 2. contextData field filter – keeps only fields listed in contextDataFields (if non-empty)
     * <p>
     * The original cached/DB map is never mutated; a new map is always returned.
     */
    private Map<String, Object> applyBulkListFilters(Map<String, Object> achievement,
                                                     Set<String> responseFields,
                                                     Set<String> contextDataFields) {
        Map<String, Object> filtered = filterTopLevelFields(achievement, responseFields);
        filterContextDataFieldsInPlace(filtered, contextDataFields);
        return filtered;
    }

    /**
     * Keeps only the top-level fields listed in {@code responseFields} (or all fields when empty).
     * Extracted from {@link #applyBulkListFilters} to keep its cognitive complexity low.
     */
    private Map<String, Object> filterTopLevelFields(Map<String, Object> achievement, Set<String> responseFields) {
        if (CollectionUtils.isEmpty(responseFields)) {
            return new HashMap<>(achievement);   // copy so we can mutate contextData safely
        }
        Map<String, Object> filtered = new LinkedHashMap<>();
        for (String field : responseFields) {
            if (achievement.containsKey(field)) {
                filtered.put(field, achievement.get(field));
            }
        }
        return filtered;
    }

    /**
     * Narrows the contextData entry of {@code filtered} down to {@code contextDataFields}, in place.
     * Extracted from {@link #applyBulkListFilters} to keep its cognitive complexity low.
     */
    private void filterContextDataFieldsInPlace(Map<String, Object> filtered, Set<String> contextDataFields) {
        if (CollectionUtils.isEmpty(contextDataFields) || !filtered.containsKey(Constants.CONTEXT_DATA)) {
            return;
        }
        Object contextDataObj = filtered.get(Constants.CONTEXT_DATA);
        if (!(contextDataObj instanceof Map<?, ?> rawMap)) {
            return;
        }
        Map<String, Object> filteredContextData = new LinkedHashMap<>();
        for (String field : contextDataFields) {
            if (rawMap.containsKey(field)) {
                filteredContextData.put(field, rawMap.get(field));
            }
        }
        filtered.put(Constants.CONTEXT_DATA, filteredContextData);
    }

    private boolean hasUrlChanges(Map<String, Object> existingRecord,
                                  Map<String, Object> newContextData) {

        //  Get old context
        Map<String, Object> oldContext =
                (Map<String, Object>) existingRecord.get(Constants.CONTEXT_DATA);

        //  Extract old values (default "" if null)
        String oldUploaded = oldContext != null && oldContext.get(Constants.UPLOAD_DOCUMENT_URL) != null
                ? oldContext.get(Constants.UPLOAD_DOCUMENT_URL).toString()
                : "";

        String oldUrl = oldContext != null && oldContext.get(Constants.URL) != null
                ? oldContext.get(Constants.URL).toString()
                : "";

        //  Extract new values (default "" if null)
        String newUploaded = newContextData.get(Constants.UPLOAD_DOCUMENT_URL) != null
                ? newContextData.get(Constants.UPLOAD_DOCUMENT_URL).toString()
                : "";

        String newUrl = newContextData.get(Constants.URL) != null
                ? newContextData.get(Constants.URL).toString()
                : "";

        // Compare fields directly (no normalization to null)
        boolean isUploadedChanged = !oldUploaded.equals(newUploaded);
        boolean isUrlChanged = !oldUrl.equals(newUrl);

        return isUploadedChanged || isUrlChanged;
    }

    private List<Map<String, String>> buildChangeUrlCompetencies(Map<String, Object> existingRecord) {

        Map<String, Object> existingContextData;

        Object existingContextObj = existingRecord.get(Constants.CONTEXT_DATA);

        if (existingContextObj instanceof Map<?, ?> map) {
            existingContextData = convertToStringObjectMap(map);
        } else if (existingContextObj instanceof String json) {
            try {
                existingContextData = objectMapper.readValue(json, Map.class);
            } catch (Exception e) {
                log.warn("Failed to parse existing context data", e);
                return List.of();
            }
        } else {
            return List.of();
        }

        //  Extract competencies exactly like delta method
        List<Map<String, Object>> existingCompetencies =
                extractCompetenciesV6List(existingContextData);

        //  Reuse SAME structure builder
        return existingCompetencies.stream()
                .map(comp -> buildCompetencyIdMap(comp, "changeUrl"))
                .toList();
    }

    private String buildKey(Map<String, String> comp) {
        return (comp.get(Constants.COMPETENCY_AREA_ID) + "|" +
                comp.get(Constants.COMPETENCY_THEME_ID) + "|" +
                comp.get(Constants.COMPETENCY_SUB_THEME_ID))
                .toLowerCase();
    }
}
