package com.igot.cb.profile.service;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import com.igot.cb.common.OutboundRequestHandlerServiceImpl;
import com.igot.cb.util.*;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.igot.cb.authentication.util.AccessTokenValidator;
import com.igot.cb.profile.entity.CustomFieldEntity;
import com.igot.cb.profile.repository.CustomFieldRepository;
import com.igot.cb.transactional.cassandrautils.CassandraOperation;
import com.igot.cb.transactional.elasticsearch.service.EsUtilServiceImpl;
import com.igot.cb.transactional.redis.cache.CacheService;
import com.igot.cb.transactional.service.RequestHandlerServiceImpl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@RequiredArgsConstructor
public class ProfileServiceImpl implements ProfileService {

    private static final String INVALID_USER_ID_MSG = "Invalid UserId in the request";
    private static final String INVALID_OR_MISSING_ACCESS_TOKEN_MSG = "Invalid or missing access token";
    private static final String EXTENDED_PROFILE_CACHE_PREFIX = "user:extendedProfile";
    private static final String CUSTOM_FIELD_PREFIX = "Custom field ";

    private final AccessTokenValidator accessTokenValidator;

    private final CbServerProperties serverConfig;

    private final CassandraOperation cassandraOperation;

    private final CacheService cacheService;

    private final ObjectMapper mapper;

    private final ProjectUtil projectUtil;

    private final RequestHandlerServiceImpl requestHandlerService;

    private final CustomFieldRepository customFieldRepository;

    private final EsUtilServiceImpl esUtilService;

    @Value("${profile.visible.allowed.fields}")
    private String profileVisibleAllowedFields;

    @Value("${user.basic.details.filtered}")
    private String basicDetailsFilteredKeys;

    private final OutboundRequestHandlerServiceImpl outboundRequestHandlerService;

    // -------------------- Service METHODS --------------------

    @Override
    public ApiResponse saveExtendedProfile(Map<String, Object> request, String userToken) {
        ApiResponse response = ProjectUtil.createDefaultResponse("api.extendedProfile.create");
        Map<String, Object> requestData = (Map<String, Object>) request.get(Constants.REQUEST);
        String userId = (String) requestData.get(Constants.USER_ID_RQST);
        String userIdFromToken = accessTokenValidator.fetchUserIdFromAccessToken(userToken);

        if (!StringUtils.equalsIgnoreCase(userIdFromToken, userId)) {
            ProjectUtil.errorResponse(response, INVALID_USER_ID_MSG, HttpStatus.BAD_REQUEST);
            return response;
        }

        String validationError = validateRequestContextTypes(requestData, serverConfig.getContextType());
        if (StringUtils.isNotBlank(validationError)) {
            ProjectUtil.errorResponse(response, validationError, HttpStatus.BAD_REQUEST);
            return response;
        }

        String errMsg = validateUserExtendedProfileRequest(requestData);
        if (StringUtils.isNotBlank(errMsg)) {
            ProjectUtil.errorResponse(response, errMsg, HttpStatus.BAD_REQUEST);
            return response;
        }

        List<Map<String, Object>> savedDataWithUUIDs = new ArrayList<>();
        for (String contextType : serverConfig.getContextType()) {
            List<Map<String, Object>> incomingList = (List<Map<String, Object>>) requestData.get(contextType);
            if (incomingList == null || incomingList.isEmpty())
                continue;

            List<Map<String, Object>> dataWithUUIDs = addUUIDs(incomingList);
            List<Map<String, Object>> existingList = getExistingContextData(userId, contextType);

            if(Constants.ACHIEVEMENTS.equalsIgnoreCase(contextType)) {
                mergeAndSortByIssuedDateOrTitle(existingList, dataWithUUIDs);
            }else{
                existingList.addAll(dataWithUUIDs);
            }

            if (!saveContextData(userId, contextType, existingList)) {
                ProjectUtil.errorResponse(response, "Failed to save data for contextType: " + contextType,
                        HttpStatus.INTERNAL_SERVER_ERROR);
                return response;
            }

            cacheService.putCache(buildCacheKey(EXTENDED_PROFILE_CACHE_PREFIX, contextType, userId), existingList);
            updateExtendedProfileAllCache(userId, contextType, existingList);
            savedDataWithUUIDs.addAll(dataWithUUIDs);
        }

        response.setResponseCode(HttpStatus.OK);
        response.put(Constants.RESULT, savedDataWithUUIDs);
        return response;
    }

    @Override
    public ApiResponse updateExtendedProfile(Map<String, Object> request, String userToken) {
        ApiResponse response = ProjectUtil.createDefaultResponse("api.extendedProfile.update");
        Map<String, Object> requestData = (Map<String, Object>) request.get(Constants.REQUEST);
        String userId = (String) requestData.get(Constants.USER_ID_RQST);
        String userIdFromToken = accessTokenValidator.fetchUserIdFromAccessToken(userToken);

        if (!StringUtils.equalsIgnoreCase(userIdFromToken, userId)) {
            ProjectUtil.errorResponse(response, INVALID_USER_ID_MSG, HttpStatus.BAD_REQUEST);
            return response;
        }

        String regexError = validateExtendedProfileFieldsRegex(requestData, Constants.EDUCATIONAL_QUALIFICATIONS);
        if (StringUtils.isNotBlank(regexError)) {
            ProjectUtil.errorResponse(response, regexError + ".", HttpStatus.BAD_REQUEST);
            return response;
        }

        for (String contextType : serverConfig.getContextType()) {
            List<Map<String, Object>> incomingList = (List<Map<String, Object>>) requestData.get(contextType);
            if (incomingList == null || incomingList.isEmpty())
                continue;

            if (!mergeContextTypeUpdate(userId, contextType, incomingList, response)) {
                return response;
            }
        }

        response.setResponseCode(HttpStatus.OK);
        response.put(Constants.RESPONSE, Constants.SUCCESS);
        return response;
    }

    /**
     * Merges incoming data for a single contextType into the existing stored data (by UUID),
     * persists it and refreshes the caches. On failure, populates the error on {@code response}.
     *
     * @return true if the merge/save succeeded, false if an error response was set
     */
    private boolean mergeContextTypeUpdate(String userId, String contextType,
            List<Map<String, Object>> incomingList, ApiResponse response) {
        List<Map<String, Object>> existingData = getExistingContextData(userId, contextType);
        Map<String, Map<String, Object>> dataMap = existingData.stream()
                .filter(e -> e.get(Constants.UUID) != null)
                .collect(Collectors.toMap(e -> (String) e.get(Constants.UUID), e -> e));

        for (Map<String, Object> item : incomingList) {
            String uuid = (String) item.get(Constants.UUID);
            if (uuid != null && dataMap.containsKey(uuid)) {
                dataMap.get(uuid).putAll(item);
            } else {
                ProjectUtil.errorResponse(response, "Invalid or missing UUID in incoming data.",
                        HttpStatus.BAD_REQUEST);
                return false;
            }
        }

        List<Map<String, Object>> mergedList = new ArrayList<>(dataMap.values());

        if (Constants.ACHIEVEMENTS.equalsIgnoreCase(contextType)) {
            mergeAndSortByIssuedDateOrTitle(mergedList, new ArrayList<>());
        }
        if (!saveContextData(userId, contextType, mergedList)) {
            ProjectUtil.errorResponse(response, "Failed to update data for contextType: " + contextType,
                    HttpStatus.INTERNAL_SERVER_ERROR);
            return false;
        }

        cacheService.putCache(buildCacheKey(EXTENDED_PROFILE_CACHE_PREFIX, contextType, userId), mergedList);
        updateExtendedProfileAllCache(userId, contextType, mergedList);
        return true;
    }

    @Override
    public ApiResponse deleteExtendedProfile(Map<String, Object> request, String userToken) {
        ApiResponse response = ProjectUtil.createDefaultResponse("api.extendedProfile.delete");
        Map<String, Object> requestData = (Map<String, Object>) request.get(Constants.REQUEST);
        String userId = (String) requestData.get(Constants.USER_ID_RQST);
        String userIdFromToken = accessTokenValidator.fetchUserIdFromAccessToken(userToken);

        if (!StringUtils.equalsIgnoreCase(userIdFromToken, userId)) {
            ProjectUtil.errorResponse(response, INVALID_USER_ID_MSG, HttpStatus.BAD_REQUEST);
            return response;
        }

        for (String contextType : serverConfig.getContextType()) {
            List<Map<String, Object>> toDeleteList = (List<Map<String, Object>>) requestData.get(contextType);
            if (toDeleteList == null || toDeleteList.isEmpty())
                continue;

            Set<String> uuids = toDeleteList.stream()
                    .map(e -> (String) e.get(Constants.UUID))
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());

            List<Map<String, Object>> existingData = getExistingContextData(userId, contextType);
            existingData.removeIf(e -> uuids.contains(e.get(Constants.UUID)));

            if (!saveContextData(userId, contextType, existingData)) {
                ProjectUtil.errorResponse(response, "Failed to delete data for contextType: " + contextType,
                        HttpStatus.INTERNAL_SERVER_ERROR);
                return response;
            }

            cacheService.putCache(buildCacheKey(EXTENDED_PROFILE_CACHE_PREFIX, contextType, userId), existingData);
            updateExtendedProfileAllCache(userId, contextType, existingData);
        }

        response.setResponseCode(HttpStatus.OK);
        response.put(Constants.RESPONSE, Constants.SUCCESS);
        return response;
    }

    @Override
    public ApiResponse getExtendedProfileSummary(String userId, String userToken) {
        ApiResponse response = ProjectUtil.createDefaultResponse("api.extendedProfile.read");
        String userIdFromToken = accessTokenValidator.fetchUserIdFromAccessToken(userToken);
        if (StringUtils.isBlank(userIdFromToken)) {
            ProjectUtil.errorResponse(response, INVALID_OR_MISSING_ACCESS_TOKEN_MSG, HttpStatus.BAD_REQUEST);
            return response;
        }

        if (StringUtils.isBlank(userId)) {
            userId = userIdFromToken;
        }

        String redisKey = buildCacheKey(EXTENDED_PROFILE_CACHE_PREFIX, "all", userId);
        try {
            String cachedJson = cacheService.getCache(redisKey);
            if (cachedJson != null) {
                Map<String, Object> cachedResult = mapper.readValue(cachedJson, Map.class);
                Map<String, Object> limitedResult = buildLimitedSummary(cachedResult);
                response.setResponseCode(HttpStatus.OK);
                response.put(Constants.RESPONSE, limitedResult);
                return response;
            }
        } catch (Exception e) {
            log.error("Failed to fetch summary from cache for userId {}: {}", userId, e);
        }

        Map<String, Object> result = new HashMap<>();
        for (String contextType : serverConfig.getContextType()) {
            List<Map<String, Object>> data = getExistingContextData(userId, contextType);
            if (!data.isEmpty()) {
                Map<String, Object> contextSummary = new HashMap<>();
                contextSummary.put(Constants.COUNT, data.size());
                contextSummary.put(Constants.DATA, data.stream().limit(2).toList());
                result.put(contextType, contextSummary);
            }
        }

        if (result.isEmpty()) {
            ProjectUtil.errorResponse(response, "No data found for user.", HttpStatus.NO_CONTENT);
            return response;
        }

        result.put(Constants.USERID_KEY, userId);
        try {
            cacheService.putCache(redisKey, result);
        } catch (Exception e) {
            log.warn("Failed to cache extended profile summary for userId {}: {}", userId, e.getMessage());
        }

        response.setResponseCode(HttpStatus.OK);
        response.put(Constants.RESPONSE, result);
        return response;
    }

    @Override
    public ApiResponse readFullExtendedProfile(String userId, String contextType, String userToken) {
        ApiResponse response = ProjectUtil.createDefaultResponse("api.extendedProfile.read");
        String userIdFromToken = accessTokenValidator.fetchUserIdFromAccessToken(userToken);

        if (userIdFromToken == null) {
            ProjectUtil.errorResponse(response, INVALID_USER_ID_MSG, HttpStatus.BAD_REQUEST);
            return response;
        }

        String redisKey = buildCacheKey(EXTENDED_PROFILE_CACHE_PREFIX, contextType, userId);
        List<Map<String, Object>> contextData = null;

        try {
            String cachedJson = cacheService.getCache(redisKey);
            if (cachedJson != null) {
                contextData = projectUtil.parseListOfMap(cachedJson);
            }
        } catch (Exception e) {
            log.warn("Error reading from cache for key {}: {}", redisKey, e.getMessage());
        }

        if (contextData == null) {
            contextData = getExistingContextData(userId, contextType);
            if (contextData == null || contextData.isEmpty()) {
                ProjectUtil.errorResponse(response, "No data found for user.", HttpStatus.NO_CONTENT);
                return response;
            }
            try {
                cacheService.putCache(redisKey, contextData);
            } catch (Exception e) {
                log.warn("Failed to cache data for key {}: {}", redisKey, e.getMessage());
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put(contextType, contextData);
        result.put(Constants.USER_ID_RQST, userId);
        result.put(Constants.COUNT, contextData.size());

        response.setResponseCode(HttpStatus.OK);
        response.put(Constants.RESPONSE,
                contextType.equalsIgnoreCase(Constants.LOCATION_DETAILS) ? contextData.get(0) : result);
        return response;
    }

    @Override
    public ApiResponse getBasicProfile(String userId, String userToken,boolean isNgo) {
        ApiResponse response = ProjectUtil.createDefaultResponse("api.getBasicProfile.read");
        String userIdFromToken = accessTokenValidator.fetchUserIdFromAccessToken(userToken);

        if (userIdFromToken == null) {
            ProjectUtil.errorResponse(response, INVALID_OR_MISSING_ACCESS_TOKEN_MSG, HttpStatus.UNAUTHORIZED);
            return response;
        }

        if(isNgo && StringUtils.isBlank(userId)){
            userId = userIdFromToken;
        }

        boolean isSelfUser = userIdFromToken.equalsIgnoreCase(userId);
        String cacheKey = Constants.USER + ":basicProfile:" + userId;

        try {
            String cachedJson = cacheService.getCache(cacheKey);
            Map<String, Object> userProfile;
            if (StringUtils.isNotEmpty(cachedJson)) {
                userProfile = mapper.readValue(cachedJson, new TypeReference<>() {});
                Set<String> cachedKeysLower = userProfile.keySet().stream()
                        .map(String::toLowerCase)
                        .collect(Collectors.toSet());
                List<String> differenceList = serverConfig.getBasicProfileFields().stream()
                        .filter(key -> !cachedKeysLower.contains(key.toLowerCase()))
                        .toList();
                if (!differenceList.isEmpty()) {
                    Set<String> allRequiredKeys = new LinkedHashSet<>();
                    allRequiredKeys.addAll(userProfile.keySet());
                    allRequiredKeys.addAll(differenceList);
                    Map<String, Object> userDetails =
                            readUserDataFromDB(userId, new ArrayList<>(allRequiredKeys));
                    if (MapUtils.isNotEmpty(userDetails)) {
                        userProfile.putAll(userDetails);
                    }
                }
            } else {
                userProfile = readUserDataFromDB(userId, null);
            }
            UserUtility.decryptSpecificUserData(userProfile, Arrays.asList(Constants.USERNAME_LOWERCASE));
            if (MapUtils.isEmpty(userProfile)) {
                response.setResponseCode(HttpStatus.NOT_FOUND);
                response.put(Constants.RESPONSE, Collections.emptyMap());
                return response;
            }
            userProfile.put(Constants.ROLES, getUserRoles(userId,(String)userProfile.get(Constants.ROOT_ORG_ID)));
            userProfile.put(Constants.PROFILE_COMPLETION_PERCENTAGE, calculateProfileCompletionPercentage(userProfile,
                    userId, userToken));
            userProfile.put(Constants.KARMA_POINTS, getUserKarmaPoints(userId));
            if (!userProfile.containsKey(Constants.CERTIFICATE_COUNT)) {
                userProfile.put(Constants.CERTIFICATE_COUNT, getIssuedCertificateCount(userId));
                cacheService.putCache(cacheKey, userProfile);
            }
            userProfile.put(Constants.POSTCOUNT, getUserPostCount(userId));
            userProfile.put(Constants.BADGE_COUNT, getUserBadgeCount(userId));
            if (!isSelfUser) {
                sanitizeProfile(userProfile, userToken);
            }

            Map<String,Object> responseMap = new HashMap<>();
            responseMap.put(Constants.RESPONSE, userProfile);
            response.setResponse(responseMap);
        } catch (Exception e) {
            log.error("Error fetching basic profile for userId: {}", userId, e);
            ProjectUtil.errorResponse(response, "Internal server error while fetching profile",
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }

        return response;
    }

    @Override
    public ApiResponse listCompetencies(String userId, String userToken) {
        ApiResponse response = ProjectUtil.createDefaultResponse("api.listCompetencies.read");
        String userIdFromToken = accessTokenValidator.fetchUserIdFromAccessToken(userToken);

        if (userIdFromToken == null) {
            ProjectUtil.errorResponse(response, INVALID_OR_MISSING_ACCESS_TOKEN_MSG, HttpStatus.UNAUTHORIZED);
            return response;
        }

        String cacheKey = Constants.USER + ":competencies:" + userId;
        try {
            String cachedJson = cacheService.getCache(cacheKey);
            Map<String, Object> competencies = (cachedJson != null) ? projectUtil.parseMap(cachedJson) : Map.of();

            if (competencies.isEmpty()) {
                Map<String, Object> queryParams = Map.of(Constants.USERID_KEY, userId);
                List<String> fields = Arrays.asList(Constants.USERID_KEY, Constants.COURSE_ID, Constants.BATCH_ID,
                        Constants.ACTIVE, Constants.STATUS);
                List<Map<String, Object>> allEnrolmentRecords = cassandraOperation.getAllRecordsByPrimaryKey(
                        Constants.KEYSPACE_SUNBIRD_COURSES,
                        Constants.TABLE_USER_ENROLMENTS, queryParams, fields, 100);
                List<String> completedCourseIdList = allEnrolmentRecords.stream()
                        .filter(map -> Boolean.TRUE.equals(map.get(Constants.ACTIVE_LOWERCASE)) && Integer.valueOf(2).equals(map.get(Constants.STATUS)))
                        .map(map -> map.get(Constants.COURSE_ID))
                        .filter(Objects::nonNull)
                        .map(Object::toString)
                        .toList();
                if (completedCourseIdList.isEmpty()) {
                    ProjectUtil.errorResponse(response, "No competencies found for user.", HttpStatus.NO_CONTENT);
                    return response;
                }
                Map<String, Map<String, Object>> courseMetadata = getCourseMetadataBatched(completedCourseIdList, 100,
                        Arrays.asList(Constants.COURSE_ID, Constants.COURSE_CATEGORY, Constants.COMPETENCIES_V6,
                                Constants.NAME));
                competencies = analyzeCompetencies(courseMetadata);

                if (competencies.isEmpty()) {
                    ProjectUtil.errorResponse(response, "No competencies found for user.", HttpStatus.NO_CONTENT);
                    return response;
                }
                cacheService.putCache(cacheKey, competencies);
            }

            response.setResponseCode(HttpStatus.OK);
            response.put(Constants.RESPONSE, competencies);
        } catch (Exception e) {
            log.error("Error fetching competencies for userId: {}", userId, e);
            ProjectUtil.errorResponse(response, "Internal server error while fetching competencies",
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }

        return response;
    }

    // -------------------- HELPER METHODS --------------------

    private List<Map<String, Object>> addUUIDs(List<Map<String, Object>> list) {
        list.forEach(item -> item.put(Constants.UUID, UUID.randomUUID().toString()));
        return new ArrayList<>(list);
    }

    private List<Map<String, Object>> getExistingContextData(String userId, String contextType) {
        Map<String, Object> query = Map.of(Constants.USERID_KEY, userId, Constants.CONTEXT_TYPE, contextType);
        List<Map<String, Object>> rows = cassandraOperation.getRecordsByPropertiesByKey(Constants.KEYSPACE_SUNBIRD,
                Constants.TABLE_USER_EXTENDED_PROFILE, query, null, null);
        if (rows != null && !rows.isEmpty()) {
            String json = (String) rows.get(0).get(Constants.CONTEXT_DATA);
            try {
                return projectUtil.parseListOfMap(json);
            } catch (IOException e) {
                log.error("Error parsing existing data for userId: {}, contextType: {}", userId, contextType);
            }
        }
        return new ArrayList<>();
    }

    /**
     * Retained for unit-test coverage (invoked via reflection); not called from production code paths.
     */
    private void sortContextData(List<Map<String, Object>> dataList, String contextType) {
        Comparator<Map<String, Object>> comparator = getSortingComparator(contextType);
        if (comparator != null) {
            dataList.sort(comparator.reversed());
        }
    }

    private Comparator<Map<String, Object>> getSortingComparator(String contextType) {
        return switch (contextType) {
            case Constants.SERVICE_HISTORY ->
                Comparator.comparing(map -> OffsetDateTime.parse((String) map.get(Constants.START_DATE)));
            case Constants.EDUCATIONAL_QUALIFICATIONS ->
                Comparator.comparing(map -> Integer.parseInt((String) map.get(Constants.START_YEAR)));
            case Constants.ACHIVEMENTS ->
                Comparator.comparing(map -> OffsetDateTime.parse((String) map.get(Constants.ISSUED_DATE)));
            default -> null;
        };
    }

    private boolean saveContextData(String userId, String contextType, List<Map<String, Object>> dataList) {
        try {
            String finalJson = mapper.writeValueAsString(dataList);
            Map<String, Object> query = new HashMap<>();
            query.put(Constants.USERID_KEY, userId);
            query.put(Constants.CONTEXT_TYPE, contextType);
            query.put(Constants.CONTEXT_DATA, finalJson);
            ApiResponse insertResponse = (ApiResponse) cassandraOperation.insertRecord(Constants.KEYSPACE_SUNBIRD,
                    Constants.TABLE_USER_EXTENDED_PROFILE, query);
            return Constants.SUCCESS.equalsIgnoreCase((String) insertResponse.get(Constants.RESPONSE));
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize context data for userId: {}, contextType: {}", userId, contextType);
        }
        return false;
    }

    private String buildCacheKey(String prefix, String contextType, String userId) {
        return String.join(":", prefix, contextType, userId);
    }

    private void updateExtendedProfileAllCache(String userId, String contextType,
            List<Map<String, Object>> updatedContextData) {
        String allKey = "user:extendedProfile:all:" + userId;
        try {
            String allJson = cacheService.getCache(allKey);
            Map<String, Object> allProfileData = (allJson != null && !allJson.isEmpty())
                    ? mapper.readValue(allJson, new TypeReference<>() {
                    })
                    : new HashMap<>();
            Map<String, Object> updatedContext = new HashMap<>();
            updatedContext.put(Constants.DATA, updatedContextData);
            updatedContext.put(Constants.COUNT, updatedContextData != null ? updatedContextData.size() : 0);
            allProfileData.put(contextType, updatedContext);
            cacheService.putCache(allKey, allProfileData);
        } catch (Exception e) {
            log.error("Error updating extendedProfile all cache for userId {}: {}", userId, e.getMessage());
        }
    }

    private String validateUserExtendedProfileRequest(Map<String, Object> requestData) {
        if (requestData == null)
            return "Request data is missing.";
        List<String> errList = new ArrayList<>();
        validateFieldsForList(requestData, Constants.EDUCATIONAL_QUALIFICATIONS,
                serverConfig.getEducationalQualificationMandatoryFields(), errList, false);
        validateFieldsForList(requestData, Constants.ACHIVEMENTS,
                serverConfig.getAchievementsMandatoryFields(), errList, false);
        validateFieldsForList(requestData, Constants.SERVICE_HISTORY,
                serverConfig.getServiceHistoryMandatoryFields(), errList, true);

        String regexError =
                validateExtendedProfileFieldsRegex(requestData, Constants.EDUCATIONAL_QUALIFICATIONS);

        if (CollectionUtils.isEmpty(errList) && StringUtils.isBlank(regexError)) {
            return "";
        }

        StringBuilder errorMessage = new StringBuilder();
        if (CollectionUtils.isNotEmpty(errList)) {
            errorMessage.append(Constants.MISSING_OR_INVALID_PARAMS)
                    .append(String.join(", ", errList))
                    .append(".");
        }

        if (StringUtils.isNotBlank(regexError)) {
            if (!errorMessage.isEmpty()) {
                errorMessage.append(" ");
            }
            errorMessage.append(regexError).append(".");
        }
        return errorMessage.toString();
    }

    private String validateExtendedProfileFieldsRegex(Map<String, Object> requestData, String listKey) {
        List<Map<String, Object>> dataList = (List<Map<String, Object>>) requestData.get(listKey);

        if (CollectionUtils.isEmpty(dataList)) {
            return "";
        }

        Set<String> invalidFields = new LinkedHashSet<>();

        for (Map<String, Object> data : dataList) {
            for (Map.Entry<String, Object> entry : data.entrySet()) {
                Object value = entry.getValue();

                if (value instanceof String strValue
                        && StringUtils.isNotBlank(strValue)
                        && !validateField(listKey, entry.getKey(), strValue)) {
                    invalidFields.add(entry.getKey());
                }
            }
        }

        if (CollectionUtils.isEmpty(invalidFields)) {
            return "";
        }
        return Constants.INVALID_CHARACTERS_ERROR + String.join(", ", invalidFields);
    }

    private void validateFieldsForList(Map<String, Object> requestData, String listKey, String mandatoryFields,
            List<String> errList, boolean allowSkipEndDate) {
        List<Map<String, Object>> dataList = (List<Map<String, Object>>) requestData.get(listKey);
        if (dataList != null) {
            for (Map<String, Object> data : dataList) {
                String error = validateFields(data, mandatoryFields, allowSkipEndDate);
                if (!error.isEmpty()) {
                    errList.add(error);
                }
            }
        }
    }

    private String validateFields(Map<String, Object> data, String mandatoryFields, boolean allowSkipEndDate) {
        StringBuilder errorMessages = new StringBuilder();
        for (String field : mandatoryFields.split(",")) {
            if (allowSkipEndDate && Constants.END_DATE.equals(field)) {
                Object currentlyWorking = data.get(Constants.CURRENTLY_WORKING);
                if (Constants.TRUE.equalsIgnoreCase(String.valueOf(currentlyWorking))) {
                    continue;
                }
            }
            if (StringUtils.isBlank((String) data.get(field))) {
                errorMessages.append(field).append(" is mandatory. ");
            }
        }
        return errorMessages.toString();
    }

    private String validateRequestContextTypes(Map<String, Object> requestData, String[] contextTypes) {
        Set<String> allowedKeys = new HashSet<>(Arrays.asList(contextTypes));
        allowedKeys.add(Constants.USER_ID_RQST);
        return requestData.keySet().stream()
                .filter(key -> !allowedKeys.contains(key))
                .findFirst()
                .map(key -> "Invalid context type in request: " + key)
                .orElse(null);
    }

    public Map<String, Object> readUserDataFromDB(String userId, List<String> keyList) {
        if (CollectionUtils.isEmpty(keyList)) {
            keyList = serverConfig.getBasicProfileFields();
        }
        String cacheKey = Constants.USER + ":basicProfile:" + userId;
        Map<String, Object> queryParams = Map.of(Constants.ID, userId);
        List<Map<String, Object>> userList = cassandraOperation.getRecordsByPropertiesByKey(
                Constants.KEYSPACE_SUNBIRD, Constants.USER, queryParams, keyList, null);

        if (CollectionUtils.isEmpty(userList)) {
            return Map.of();
        }
        Map<String, Object> userObj = userList.get(0);
        String profileDetailsJson = (String) userObj.get(Constants.PROFILE_DETAILS);

        try {
            if (StringUtils.isNotBlank(profileDetailsJson)) {
                Map<String, Object> profileDetailsMap = mapper.readValue(profileDetailsJson, new TypeReference<Map<String, Object>>() {
                });
                userObj.put(Constants.PROFILE_DETAILS, profileDetailsMap);
            } else {
                userObj.put(Constants.PROFILE_DETAILS, Map.of());
            }
            cacheService.putCache(cacheKey, userObj);
        } catch (IOException e) {
            log.error("Invalid profileDetails JSON for userId: {}", userId, e);
            userObj.put(Constants.PROFILE_DETAILS, Map.of());
        }

        return userObj;
    }

    private void sanitizeProfile(Map<String, Object> profile, String userToken) {
        Object detailsObj = profile.get(Constants.PROFILE_DETAILS);

        if (!(detailsObj instanceof Map<?, ?> detailsMap)) {
            return;
        }

        removePersonalDetailsIfPresent(detailsMap);
        ProfilePreference profilePref = resolveProfilePreference(detailsMap);

        // If PUBLIC, return everything
        if (ProfilePreference.PUBLIC.equals(profilePref)) {
            return;
        }

        // Load keys from property
        List<String> filteredKeys = Arrays.asList(basicDetailsFilteredKeys.split(","));
        // Shared allowed keys from config
        List<String> allowedKeys = Arrays.asList(profileVisibleAllowedFields.split(","));

        if (ProfilePreference.PRIVATE_NO_ONE.equals(profilePref)) {
            applyPrivateNoOneSanitization(profile, detailsMap, filteredKeys, allowedKeys, profilePref);
        } else if (ProfilePreference.PRIVATE_CONNECTIONS.equals(profilePref)) {
            applyPrivateConnectionsSanitization(profile, detailsMap, filteredKeys, allowedKeys, profilePref, userToken);
        } else {
            // Fallback case – remove personalDetails
            removePersonalDetailsIfPresent(detailsMap);
        }
    }

    private void removePersonalDetailsIfPresent(Map<?, ?> detailsMap) {
        if (detailsMap.containsKey(Constants.PERSONAL_DETAILS)) {
            detailsMap.remove(Constants.PERSONAL_DETAILS);
            log.info("Removed personalDetails due to unrecognized profilePreference.");
        }
    }

    private ProfilePreference resolveProfilePreference(Map<?, ?> detailsMap) {
        ProfilePreference profilePref = ProfilePreference.PUBLIC; // default to PUBLIC
        Object preferenceObj = detailsMap.get(Constants.PROFILE_PREFERENCE);
        if (preferenceObj instanceof Integer integer) {
            ProfilePreference resolvedPref = ProfilePreference.fromValue(integer);
            if (resolvedPref != null) {
                profilePref = resolvedPref;
            }
        }
        return profilePref;
    }

    private Map<String, Object> filterAllowedDetails(Map<?, ?> detailsMap, List<String> allowedKeys) {
        Map<String, Object> filteredDetails = new HashMap<>();
        for (String key : allowedKeys) {
            if (detailsMap.containsKey(key)) {
                filteredDetails.put(key, detailsMap.get(key));
            }
        }
        return filteredDetails;
    }

    private void applyPrivateNoOneSanitization(Map<String, Object> profile, Map<?, ?> detailsMap,
            List<String> filteredKeys, List<String> allowedKeys, ProfilePreference profilePref) {
        Map<String, Object> filteredDetails = filterAllowedDetails(detailsMap, allowedKeys);
        filteredKeys.forEach(profile::remove);
        profile.put(Constants.PROFILE_DETAILS, filteredDetails);
        log.info("Sanitized profileDetails for PRIVATE_NO_ONE ({}). Allowed fields: {}", profilePref.getValue(), allowedKeys);
    }

    private void applyPrivateConnectionsSanitization(Map<String, Object> profile, Map<?, ?> detailsMap,
            List<String> filteredKeys, List<String> allowedKeys, ProfilePreference profilePref, String userToken) {
        Map<String, Object> connectionResponse = checkConnected(
                (String) profile.get(Constants.ID),
                (String) profile.get(Constants.AUTH_TOKEN),
                userToken);

        if (connectionResponse != null) {
            Object statusObj = connectionResponse.get(Constants.STATUS);
            if (statusObj != null && Constants.APPROVED.equalsIgnoreCase(statusObj.toString())) {
                return; // If connection approved, allow full profile
            }
        }
        filteredKeys.forEach(profile::remove);
        Map<String, Object> filteredDetails = filterAllowedDetails(detailsMap, allowedKeys);
        profile.put(Constants.PROFILE_DETAILS, filteredDetails);
        log.info("Sanitized profileDetails for PRIVATE_CONNECTIONS ({}). Allowed fields: {}", profilePref.getValue(), allowedKeys);
    }

    public Map<String, Object> checkConnected(String userId, String authToken, String userAuthToken) {
        Map<String, String> header = buildConnectionCheckHeaders(authToken, userAuthToken);
        Map<String, Object> readData = (Map<String, Object>) outboundRequestHandlerService
                .fetchUsingGetWithHeadersProfile(serverConfig.hubGraphService + serverConfig.connectionApi + userId,
                        header);
        return extractConnectionResponseMap(readData);
    }

    private Map<String, String> buildConnectionCheckHeaders(String authToken, String userAuthToken) {
        Map<String, String> header = new HashMap<>();
        if (StringUtils.isNotEmpty(authToken)) {
            header.put(Constants.AUTH_TOKEN, authToken);
        }
        if (StringUtils.isNotEmpty(userAuthToken)) {
            header.put(Constants.X_AUTH_TOKEN, userAuthToken);
        }
        return header;
    }

    private Map<String, Object> extractConnectionResponseMap(Map<String, Object> readData) {
        Map<String, Object> responseMap = new HashMap<>();
        if (readData == null) {
            return responseMap;
        }
        Object resultObj = readData.get(Constants.RESULT);
        if (!(resultObj instanceof Map<?, ?> resultMap)) {
            return responseMap;
        }
        Object responseObj = resultMap.get(Constants.RESPONSE);
        if (!(responseObj instanceof Map<?, ?> responseData)) {
            return responseMap;
        }
        for (Map.Entry<?, ?> entry : responseData.entrySet()) {
            if (entry.getKey() instanceof String key) {
                responseMap.put(key, entry.getValue());
            }
        }
        return responseMap;
    }

    protected double calculateProfileCompletionPercentage(Map<String, Object> profileData,
                                                          String userId, String userToken) {
        if (profileData == null) {
            return 0.0;
        }
        List<String> roles = (List<String>) profileData.get(Constants.ROLES);
        boolean isVolunteer = !CollectionUtils.isEmpty(roles)
                && roles.contains(Constants.VOLUNTEER);

        List<String> requiredFields = isVolunteer
                ? serverConfig.getNgoUserProfileCompletionRequiredFields()
                : serverConfig.getProfileCompletionRequiredFields();

        double fieldWeight = isVolunteer
                ? serverConfig.getNgoUserProfileFieldWeight()
                : serverConfig.getFieldWeight();

        if (CollectionUtils.isEmpty(requiredFields)) {
            return 0.0;
        }
        double totalCompletion = 0.0;
        Map<String, Object> nestedData = Optional.ofNullable(profileData.get(Constants.PROFILE_DETAILS))
                .filter(Map.class::isInstance)
                .map(Map.class::cast)
                .orElse(Collections.emptyMap());

        for (String field : requiredFields) {
            if (isProfileFieldFilled(field, profileData, nestedData, userId, userToken)) {
                totalCompletion += fieldWeight;
            }
        }

        return Math.min(100.0, Math.round(totalCompletion * 10.0) / 10.0);
    }

    private boolean isProfileFieldFilled(String field, Map<String, Object> profileData,
            Map<String, Object> nestedData, String userId, String userToken) {
        try {
            if (isExtendedProfileField(field)) {
                return isExtendedProfileFieldFilled(field, profileData, userId, userToken);
            }
            if (Constants.EMPLOYMENT_DETAILS.equalsIgnoreCase(field)) {
                return isEmploymentAboutMeFilled(profileData);
            }
            Object value = profileData.getOrDefault(field, nestedData.get(field));
            return value != null && !value.toString().trim().isEmpty();
        } catch (Exception e) {
            log.warn("Exception checking field '{}' for user '{}': {}", field, userId, e.getMessage());
            return false;
        }
    }

    private boolean isExtendedProfileFieldFilled(String field, Map<String, Object> profileData, String userId,
            String userToken) {
        return hasExtendedProfileData(userId, field, userToken)
                || (Constants.SERVICE_HISTORY.equalsIgnoreCase(field) &&
                Optional.ofNullable(profileData.get(Constants.PROFILE_DETAILS))
                        .filter(Map.class::isInstance)
                        .map(Map.class::cast)
                        .map(details -> details.get(Constants.PROFESSIONAL_DETAILS))
                        .filter(List.class::isInstance)
                        .map(List.class::cast)
                        .map(CollectionUtils::isNotEmpty)
                        .orElse(false));
    }

    private boolean isEmploymentAboutMeFilled(Map<String, Object> profileData) {
        return Optional.ofNullable(profileData.get(Constants.PROFILE_DETAILS))
                .filter(Map.class::isInstance)
                .map(Map.class::cast)
                .map(details -> details.get(Constants.EMPLOYMENT_DETAILS))
                .filter(Map.class::isInstance)
                .map(Map.class::cast)
                .map(empDetails -> empDetails.get(Constants.ABOUT_ME))
                .map(Object::toString)
                .filter(aboutMe -> !aboutMe.trim().isEmpty())
                .isPresent();
    }

    private boolean isExtendedProfileField(String field) {
        return serverConfig.getExtendedFieldsConfig().stream()
                .anyMatch(f -> f.equalsIgnoreCase(field));
    }

    protected boolean hasExtendedProfileData(String userId, String contextType, String userToken) {
        try {
            ApiResponse response = readFullExtendedProfile(userId, contextType, userToken);
            if (response != null && response.getResponseCode() == HttpStatus.OK) {
                Map<String, Object> result = (Map<String, Object>) response.get(Constants.RESPONSE);
                if (Constants.LOCATION_DETAILS.equalsIgnoreCase(contextType))
                    return Stream.of(Constants.STATE, Constants.DISTRICT).allMatch(result::containsKey);
                Object contextData = result.get(contextType);
                return contextData instanceof Collection && !((Collection<?>) contextData).isEmpty();
            }
        } catch (Exception e) {
            log.error("Error checking extended profile data for userId {} and contextType {}: {}", userId,
                    contextType, e.getMessage());
        }
        return false;
    }

    public Map<String, Map<String, Object>> getCourseMetadataBatched(List<String> courseIds, int batchSize,
            List<String> fields) {
        Map<String, Map<String, Object>> allResults = new LinkedHashMap<>();
        if (courseIds == null || courseIds.isEmpty())
            return allResults;

        for (int i = 0; i < courseIds.size(); i += batchSize) {
            int end = Math.min(i + batchSize, courseIds.size());
            List<String> batch = courseIds.subList(i, end);

            Map<String, String> courseDetailsStrMap = cacheService.getCourseMetadataAsJsonString(batch);

            for (String courseId : batch) {
                addCourseMetadataIfPresent(courseId, courseDetailsStrMap.get(courseId), fields, allResults);
            }
        }
        return allResults;
    }

    private void addCourseMetadataIfPresent(String courseId, String json, List<String> fields,
            Map<String, Map<String, Object>> allResults) {
        if (json == null) {
            log.warn("No cached data found for courseId: {}", courseId);
            return;
        }
        try {
            Map<String, Object> parsed = projectUtil.parseMap(json);
            if (parsed == null || parsed.isEmpty()) {
                log.warn("Parsed JSON for key {} is empty or null", courseId);
                return;
            }

            if (fields == null || fields.isEmpty()) {
                allResults.put(courseId, parsed);
                return;
            }

            // Filter only requested fields
            Map<String, Object> filtered = parsed.entrySet().stream()
                    .filter(e -> fields.contains(e.getKey()))
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

            if (!filtered.isEmpty()) {
                allResults.put(courseId, filtered);
            }
        } catch (Exception e) {
            log.error("Failed to parse JSON for key {}: {}", courseId, e.getMessage(), e);
        }
    }

    public Map<String, Object> analyzeCompetencies(Map<String, Map<String, Object>> courseMetadata) {
        // Result containers
        Map<String, Long> areaCountMap = new HashMap<>();
        Map<String, Map<String, Object>> themeGroupMap = new HashMap<>();

        for (Map.Entry<String, Map<String, Object>> entry : courseMetadata.entrySet()) {
            String courseId = entry.getKey();
            Map<String, Object> course = entry.getValue();

            Object compObj = course.get(Constants.COMPETENCIES_V6);
            if (!(compObj instanceof List<?> competencies))
                continue;

            for (Object comp : competencies) {
                if (!(comp instanceof Map<?, ?> compMap))
                    continue;

                String areaName = String.valueOf(compMap.get(Constants.COMPETENCY_AREA_NAME));
                String themeName = String.valueOf(compMap.get(Constants.COMPETENCY_THEME_NAME));
                String subThemeName = String.valueOf(compMap.get(Constants.COMPETENCY_SUB_THEME_NAME));

                // 1. Count by competencyAreaName
                areaCountMap.merge(areaName, 1L, Long::sum);

                // 2. Group by competencyThemeName
                themeGroupMap.computeIfAbsent(themeName, k -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put(Constants.COMPETENCY_SUB_THEME_NAMES, new HashSet<String>());
                    m.put(Constants.COURSE_IDS, new HashSet<String>());
                    return m;
                });

                Set<String> subThemes = (Set<String>) themeGroupMap.get(themeName).get(Constants.COMPETENCY_SUB_THEME_NAMES);
                Set<String> courseIds = (Set<String>) themeGroupMap.get(themeName).get(Constants.COURSE_IDS);

                if (subThemeName != null && !subThemeName.isBlank())
                    subThemes.add(subThemeName);
                courseIds.add(courseId);
            }
        }

        // Prepare final output
        Map<String, Object> result = new HashMap<>();
        result.put(Constants.COMPETENCY_AREA_COUNTS, areaCountMap);

        // Convert sets to lists for serialization/final response
        Map<String, Map<String, Object>> groupedThemes = new LinkedHashMap<>();
        for (Map.Entry<String, Map<String, Object>> entry : themeGroupMap.entrySet()) {
            groupedThemes.put(entry.getKey(), Map.of(
                    Constants.COMPETENCY_SUB_THEME_NAMES,
                    new ArrayList<>((Set<?>) entry.getValue().get(Constants.COMPETENCY_SUB_THEME_NAMES)),
                    Constants.COURSE_IDS, new ArrayList<>((Set<?>) entry.getValue().get(Constants.COURSE_IDS))));
        }

        result.put(Constants.COMPETENCY_THEME_GROUPS, groupedThemes);
        return result;
    }

    private Map<String, Object> buildLimitedSummary(Map<String, Object> fullData) {
        Map<String, Object> limitedData = new HashMap<>();

        for (Map.Entry<String, Object> entry : fullData.entrySet()) {
            String key = entry.getKey();

            if (!(entry.getValue() instanceof Map)) {
                limitedData.put(key, entry.getValue());
                continue;
            }

            Map<String, Object> contextBlock = (Map<String, Object>) entry.getValue();
            Object dataObj = contextBlock.get(Constants.DATA);

            if (dataObj instanceof List) {
                List<Map<String, Object>> dataList = (List<Map<String, Object>>) dataObj;
                Map<String, Object> limitedBlock = new HashMap<>();
                limitedBlock.put(Constants.COUNT, contextBlock.get(Constants.COUNT));
                limitedBlock.put(Constants.DATA, dataList.size() > 2 ? dataList.subList(0, 2) : dataList);
                limitedData.put(key, limitedBlock);
            } else {
                limitedData.put(key, contextBlock);
            }
        }

        return limitedData;
    }

    private int getUserKarmaPoints(String userId) {
        String redisKey = "user:karmaPoints:" + userId;

        try {
            String redisValue = cacheService.getCache(redisKey);
            if (redisValue != null) {
                return Integer.parseInt(redisValue);
            }

            List<Map<String, Object>> records = cassandraOperation.getRecordsByPropertiesByKey(Constants.KEYSPACE_SUNBIRD,Constants.USER_KARMA_POINTS_SUMMARY_TABLE,
                    Map.of(Constants.USERID_KEY, userId), List.of(Constants.TOTAL_POINTS), userId);
            int totalPoints = 0;
            if(!CollectionUtils.isEmpty(records)){
                totalPoints=(int) records.get(0).get(Constants.TOTAL_POINTS);
            }

            cacheService.putCache(redisKey, totalPoints);
            return totalPoints;
        } catch (Exception e) {
            log.warn("Failed to fetch karma points for userId {}: {}", userId, e.getMessage());
            return 0;
        }
    }


    private int getIssuedCertificateCount(String userId) {

        try {

            List<Map<String, Object>> courseRecords = cassandraOperation.getRecordsByPropertiesByKey(
                    Constants.KEYSPACE_SUNBIRD_COURSES,
                    serverConfig.getUserEnrolmentsTable(),
                    Map.of(Constants.USERID_KEY, userId),
                    List.of(Constants.ISSUED_CERTIFICATES),
                    userId
            );

            int totalIssuedCertificates = 0;
            totalIssuedCertificates += (int) courseRecords.stream()
                    .filter(MapUtils::isNotEmpty)
                    .map(rec -> rec.get(Constants.ISSUED_CERTIFICATES_KEY))
                    .filter(certObj -> certObj instanceof List<?>)
                    .map(certObj -> (List<?>) certObj)
                    .filter(CollectionUtils::isNotEmpty)
                    .count();

            List<Map<String, Object>> eventRecords = cassandraOperation.getRecordsByPropertiesByKey(
                    Constants.KEYSPACE_SUNBIRD_COURSES,
                    Constants.USER_ENTITY_ENROLMENTS,
                    Map.of(Constants.USERID_KEY, userId),
                    List.of(Constants.ISSUED_CERTIFICATES,Constants.PROGRESS_KEY,Constants.STATUS),
                    userId
            );

            int certificatesFromEvents = (int) eventRecords.stream()
                    .filter(MapUtils::isNotEmpty)
                    .filter(r -> r.get(Constants.STATUS) instanceof Number status && status.intValue() == 2)
                    .filter(r -> r.get(Constants.PROGRESS_KEY) instanceof Number progress && progress.intValue() == 100)
                    .map(r -> r.get(Constants.ISSUED_CERTIFICATES_KEY))
                    .filter(obj -> obj instanceof List<?>)
                    .map(obj -> (List<?>) obj)
                    .filter(CollectionUtils::isNotEmpty)
                    .count();

            List<Map<String, Object>> externalCourseRecords = cassandraOperation.getRecordsByPropertiesByKey(
                    Constants.KEYSPACE_SUNBIRD_COURSES,
                    Constants.USER_EXTERNAL_COURSE_ENROLMENTS,
                    Map.of(Constants.USERID_KEY, userId),
                    List.of(Constants.ISSUED_CERTIFICATES,Constants.PROGRESS_KEY,Constants.STATUS),
                    userId
            );

            int certificatesFromExternalCourses = (int) externalCourseRecords.stream()
                    .filter(MapUtils::isNotEmpty)
                    .filter(r -> r.get(Constants.STATUS) instanceof Number status && status.intValue() == 2)
                    .filter(r -> r.get(Constants.PROGRESS_KEY) instanceof Number progress && progress.intValue() == 100)
                    .map(r -> r.get(Constants.ISSUED_CERTIFICATES_KEY))
                    .filter(obj -> obj instanceof List<?>)
                    .map(obj -> (List<?>) obj)
                    .filter(CollectionUtils::isNotEmpty)
                    .count();
            totalIssuedCertificates += certificatesFromEvents + certificatesFromExternalCourses;
            return totalIssuedCertificates;

        } catch (Exception e) {
            log.warn("Failed to fetch issued certificate count for userId {}: {}", userId, e.getMessage());
            return 0;
        }
    }

    private int getUserPostCount(String userId) {
        String redisKey = "user:postCount_" + userId;

        try {
            String cachedValue = cacheService.getCache(redisKey);
            if (cachedValue != null) {
                return Integer.parseInt(cachedValue);
            }

            int postCount = fetchPostCountFromApi(userId);
            cacheService.putCache(redisKey, postCount);
            return postCount;

        } catch (Exception e) {
            log.warn("Failed to fetch post count for userId {}: {}", userId, e.getMessage());
            return 0;
        }
    }

    @SuppressWarnings("unchecked")
    private int fetchPostCountFromApi(String userId) {
        String uri = serverConfig.getCommunityBaseUrl() + serverConfig.getCommunityPostCountApiUrl() + userId;

        try {
            Map<String, Object> response = (Map<String, Object>) requestHandlerService.fetchUsingGetWithHeadersProfile(uri, null);

            return Optional.ofNullable(response)
                    .filter(MapUtils::isNotEmpty)
                    .map(rd -> (Map<String, Object>) rd.get(Constants.RESULT))
                    .filter(MapUtils::isNotEmpty)
                    .map(result -> result.get(Constants.POSTCOUNT))
                    .filter(Integer.class::isInstance)
                    .map(Integer.class::cast)
                    .orElse(0);

        } catch (Exception e) {
            log.warn("Failed to fetch post count from community API for userId {}: {}", userId, e.getMessage());
            return 0;
        }
    }

    public List<String> getUserRoles(String userId, String rootOrgId) {
        List<Map<String, Object>> userRoleList = cassandraOperation.getRecordsByPropertiesByKey(
                Constants.KEYSPACE_SUNBIRD, Constants.USER_ROLES,
                Map.of(Constants.USERID_KEY, userId), List.of(Constants.ROLE, Constants.SCOPE), userId
        );
        return userRoleList.stream()
                .map(userRoleObj -> {
                    Object userRoleScope = userRoleObj.get(Constants.SCOPE);
                    List<Map<String, Object>> scopes = new ArrayList<>();
                    if (userRoleScope instanceof List) {
                        scopes = (List<Map<String, Object>>) userRoleScope;
                    } else if (userRoleScope instanceof String scopeStr && !scopeStr.isBlank()) {
                        try {
                            scopes = mapper.readValue(scopeStr, new TypeReference<List<Map<String, Object>>>() {
                            });
                        } catch (Exception e) {
                            log.warn("Failed to parse scope JSON for userId {}: {}", userId, e.getMessage());
                            return null;
                        }
                    }
                    if (!scopes.isEmpty() && scopes.stream().allMatch(scope -> rootOrgId.equals(scope.get(Constants.ORGANISATION_ID)))) {
                        return (String) userRoleObj.get(Constants.ROLE);
                    }
                    return null;
                })
                .filter(Objects::nonNull)
                .distinct()
                .toList();
    }

    private void mergeAndSortByIssuedDateOrTitle(List<Map<String, Object>> existingList, List<Map<String, Object>> newList) {
        List<Map<String, Object>> merged = Stream.concat(existingList.stream(), newList.stream())
                .sorted(this::compareByIssuedDateOrTitle)
                .toList();
        IntStream.range(0, merged.size()).forEach(i -> merged.get(i).put(Constants.INDEX, i));
        existingList.clear();
        existingList.addAll(merged);
    }

    private int compareByIssuedDateOrTitle(Map<String, Object> a, Map<String, Object> b) {
        OffsetDateTime dateA = parseOffsetDateTime(a.get(Constants.ISSUED_DATE));
        OffsetDateTime dateB = parseOffsetDateTime(b.get(Constants.ISSUED_DATE));
        if (dateA != null && dateB != null) {
            return dateB.compareTo(dateA);
        } else if (dateA == null && dateB == null) {
            return compareByTitle((String) a.get(Constants.TITLE), (String) b.get(Constants.TITLE));
        } else if (dateA == null) {
            return 1;
        } else {
            return -1;
        }
    }

    private int compareByTitle(String titleA, String titleB) {
        if (titleA == null && titleB == null) return 0;
        if (titleA == null) return 1;
        if (titleB == null) return -1;
        return titleA.compareToIgnoreCase(titleB);
    }

    private OffsetDateTime parseOffsetDateTime(Object dateObj) {
        if (dateObj instanceof String str && !str.isBlank()) {
            try {
                return OffsetDateTime.parse(str);
            } catch (Exception e) {
                // Not a parsable OffsetDateTime; treated as absent for sorting purposes.
            }
        }
        return null;
    }

    /**
     * Updates additional fields for a user in an organization
     */
    @Override
    public ApiResponse updateAdditionalFields(Map<String, Object> request,String orgId, String authToken) {
        ApiResponse response = ProjectUtil.createDefaultResponse("api.update.additionalFields");
        String userIdFromToken = accessTokenValidator.fetchUserIdFromAccessToken(authToken);

        if (StringUtils.isBlank(authToken)) {
            ProjectUtil.errorResponse(response, INVALID_OR_MISSING_ACCESS_TOKEN_MSG, HttpStatus.UNAUTHORIZED);
            return response;
        }

        String validationError = validateAdditionalFieldsRequest(request);
        if (validationError != null) {
            ProjectUtil.errorResponse(response, validationError, HttpStatus.BAD_REQUEST);
            return response;
        }

        String userId = (String) request.get(Constants.USER_ID);
        String organisationId = (String) request.get(Constants.ORGANISATION_ID);
        List<Map<String, Object>> customFieldValues = (List<Map<String, Object>>) request.get(Constants.CUSTOM_FIELD_VALUES);

        if (!StringUtils.equalsIgnoreCase(userIdFromToken, userId)) {
            ProjectUtil.errorResponse(response, "User ID in token does not match request", HttpStatus.UNAUTHORIZED);
            return response;
        }
        if(!StringUtils.equalsIgnoreCase(organisationId,orgId)){
            ProjectUtil.errorResponse(response, Constants.INVALID_ORGID, HttpStatus.UNAUTHORIZED);
            return response;
        }

        String contextType = Constants.ORG_ADDITIONAL_PROPERTIES;

        try {
            List<Map<String, Object>> existingData = getExistingContextData(userId, contextType);

            List<Map<String, Object>> restructuredData = restructureByOrgId(existingData, organisationId, customFieldValues);

            if (!saveContextData(userId, contextType, restructuredData)) {
                ProjectUtil.errorResponse(response, "Failed to save additional fields", HttpStatus.INTERNAL_SERVER_ERROR);
                return response;
            }

            // Transform for ES and update
            List<Map<String, Object>> esOrgCustomFields = transformOrgCustomFieldsForES(restructuredData);
            boolean updated = esUtilService.updateUserOrgCustomFields(userId, organisationId, esOrgCustomFields);

            if (!updated) {
                ProjectUtil.errorResponse(response, "Failed to update orgCustomFields in ES", HttpStatus.INTERNAL_SERVER_ERROR);
                return response;
            }

            response.setResponseCode(HttpStatus.OK);
            response.put(Constants.RESPONSE, Constants.SUCCESS);
        } catch (Exception e) {
            log.error("Error updating additional fields for userId: {}, orgId: {}", userId, organisationId, e);
            ProjectUtil.errorResponse(response, "Internal server error", HttpStatus.INTERNAL_SERVER_ERROR);
        }
        return response;
    }

    /**
     * Validates the request body for updating additional fields
     *
     * @param request The request body to validate
     * @return Error message if validation fails, null if validation passes
     */
    private String validateAdditionalFieldsRequest(Map<String, Object> request) {
        String missingParamsError = validateRequiredAdditionalFieldsParams(request);
        if (missingParamsError != null) {
            return missingParamsError;
        }

        String organisationId = (String) request.get(Constants.ORGANISATION_ID);
        List<Map<String, Object>> customFieldValues =
                (List<Map<String, Object>>) request.get(Constants.CUSTOM_FIELD_VALUES);

        for (Map<String, Object> field : customFieldValues) {
            String fieldError = validateSingleCustomField(field, organisationId);
            if (fieldError != null) {
                return fieldError;
            }
        }
        return null;
    }

    private String validateRequiredAdditionalFieldsParams(Map<String, Object> request) {
        List<String> errList = new ArrayList<>();

        String userId = (String) request.get(Constants.USER_ID_RQST);
        if (StringUtils.isBlank(userId)) {
            errList.add(Constants.USER_ID_RQST);
        }

        String organisationId = (String) request.get(Constants.ORGANISATION_ID);
        if (StringUtils.isBlank(organisationId)) {
            errList.add(Constants.ORGANISATION_ID);
        }

        List<Map<String, Object>> customFieldValues = (List<Map<String, Object>>) request.get(Constants.CUSTOM_FIELD_VALUES);
        if (CollectionUtils.isEmpty(customFieldValues)) {
            errList.add(Constants.CUSTOM_FIELD_VALUES);
        }

        if (errList.isEmpty()) {
            return null;
        }
        StringBuilder str = new StringBuilder();
        str.append(Constants.FAILED_DUE_TO_MISSING_PARAMS).append(errList).append(".");
        return str.toString();
    }

    private String validateSingleCustomField(Map<String, Object> field, String organisationId) {
        StringBuilder str = new StringBuilder();
        String customFieldId = (String) field.get(Constants.CUSTOM_FIELD_ID);
        String fieldType = (String) field.get(Constants.FIELD_TYPE);

        if (StringUtils.isBlank(customFieldId)) {
            str.append("Each custom field must have a customFieldId. ");
            return str.toString();
        }

        if (StringUtils.isBlank(fieldType)) {
            str.append("Each custom field must have a type. ");
            return str.toString();
        }

        CustomFieldEntity customFieldEntity = getCustomFieldById(customFieldId);
        if (customFieldEntity == null) {
            str.append("Custom field with ID ").append(customFieldId).append(" does not exist. ");
            return str.toString();
        }

        boolean isActive = customFieldEntity.getIsActive();
        if (!isActive) {
            str.append("Custom field with ID ").append(customFieldId).append(" is not active. ");
            return str.toString();
        }

        String orgId = customFieldEntity.getCustomFieldData().get(Constants.ORGANISATION_ID).asText();
        if (!StringUtils.equals(orgId, organisationId)) {
            str.append(CUSTOM_FIELD_PREFIX).append(customFieldId)
                    .append(" is not configured for organization ").append(organisationId).append(". ");
            return str.toString();
        }

        String requestedAttributeName = (String) field.get(Constants.ATTRIBUTE_NAME);
        String actualAttributeName = customFieldEntity.getCustomFieldData().get(Constants.ATTRIBUTE_NAME).asText();
        if (!StringUtils.equals(requestedAttributeName, actualAttributeName)) {
            str.append("Invalid attribute name for custom field ").append(customFieldId).append(". ");
            return str.toString();
        }

        return validateCustomFieldTypeValue(field, customFieldEntity, customFieldId, fieldType);
    }

    private String validateCustomFieldTypeValue(Map<String, Object> field, CustomFieldEntity customFieldEntity,
            String customFieldId, String fieldType) {
        StringBuilder str = new StringBuilder();
        String storedType = customFieldEntity.getCustomFieldData().get(Constants.TYPE).asText();
        if (Constants.TEXT.equals(fieldType)) {
            if (field.get(Constants.VALUE) == null) {
                str.append("Text field ").append(customFieldId).append(" must have a value. ");
                return str.toString();
            }

            if (!Constants.TEXT.equals(storedType)) {
                str.append(CUSTOM_FIELD_PREFIX).append(customFieldId).append(" is not of type text. ");
                return str.toString();
            }
            return null;
        } else if (Constants.MASTER_LIST.equals(fieldType)) {
            List<Map<String, Object>> values = (List<Map<String, Object>>) field.get(Constants.VALUES);
            if (CollectionUtils.isEmpty(values)) {
                str.append("MasterList field ").append(customFieldId).append(" must have values. ");
                return str.toString();
            }

            if (!Constants.MASTER_LIST.equals(storedType)) {
                str.append(CUSTOM_FIELD_PREFIX).append(customFieldId).append(" is not of type masterList. ");
                return str.toString();
            }

            return validateMasterListValues(customFieldEntity, values);
        } else {
            str.append("Unsupported field type: ").append(fieldType).append(". ");
            return str.toString();
        }
    }

    /**
     * Validates the values for a masterList custom field
     *
     * @param entity          CustomFieldEntity containing the valid values
     * @param requestedValues Values from the request to validate
     * @return Error message if validation fails, null if validation passes
     */
    private String validateMasterListValues(CustomFieldEntity entity, List<Map<String, Object>> requestedValues) {
        try {
            JsonNode customFieldData = entity.getCustomFieldData().get(Constants.CUSTOM_FIELD_DATA);
            if (customFieldData == null || !customFieldData.isArray()) {
                return "Invalid master list field definition.";
            }

            String duplicateLevelError = findDuplicateLevelError(requestedValues);
            if (duplicateLevelError != null) {
                return duplicateLevelError;
            }

            // Sort values by level to validate parent-child relationships
            List<Map<String, Object>> sortedValues = requestedValues.stream()
                    .sorted(Comparator.comparing(map -> (Integer) map.get(Constants.LEVEL)))
                    .toList();

            return validateMasterListHierarchy(sortedValues, customFieldData);
        } catch (Exception e) {
            log.error("Error validating master list values: {}", e.getMessage());
            return "Error validating master list values.";
        }
    }

    /**
     * Checks for duplicate levels - only one entry per level is allowed.
     */
    private String findDuplicateLevelError(List<Map<String, Object>> requestedValues) {
        Map<Integer, Integer> levelCounts = new HashMap<>();
        for (Map<String, Object> value : requestedValues) {
            Integer level = (Integer) value.get(Constants.LEVEL);
            if (level == null) {
                return "Each master list value must have a level.";
            }

            levelCounts.put(level, levelCounts.getOrDefault(level, 0) + 1);
            if (levelCounts.get(level) > 1) {
                return "Only one value allowed per level. Found multiple entries at level " + level;
            }
        }
        return null;
    }

    /**
     * Validates each value against the hierarchical (parent/child) master list definition.
     */
    private String validateMasterListHierarchy(List<Map<String, Object>> sortedValues, JsonNode customFieldData) {
        // Track parent node for hierarchical validation across iterations
        JsonNode[] currentParentHolder = new JsonNode[1];

        for (Map<String, Object> value : sortedValues) {
            String error = validateHierarchyValue(value, customFieldData, currentParentHolder);
            if (error != null) {
                return error;
            }
        }

        return null;
    }

    private String validateHierarchyValue(Map<String, Object> value, JsonNode customFieldData,
            JsonNode[] currentParentHolder) {
        String attributeName = (String) value.get(Constants.ATTRIBUTE_NAME);
        String valueStr = String.valueOf(value.get(Constants.VALUE));
        Integer level = (Integer) value.get(Constants.LEVEL);

        if (StringUtils.isBlank(attributeName) || valueStr == null || level == null) {
            return "Each master list value must have attribute name, value and level.";
        }

        // For level 1, find matching node by value
        if (level == 1) {
            JsonNode match = findChildNodeByValue(customFieldData, valueStr);
            if (match == null) {
                return "Invalid value '" + valueStr + "' at level 1";
            }
            currentParentHolder[0] = match;
            return null;
        }

        // For higher levels, find in children of current parent by value
        JsonNode currentParentNode = currentParentHolder[0];
        if (currentParentNode == null) {
            return "Invalid hierarchy structure. Parent node not found for level " + level;
        }

        JsonNode childValues = currentParentNode.get(Constants.FIELD_VALUES);
        JsonNode nextParent = (childValues != null && childValues.isArray())
                ? findChildNodeByValue(childValues, valueStr)
                : null;

        if (nextParent == null) {
            return "Invalid value '" + valueStr + "' at level " + level +
                    ". Not found under parent '" + currentParentNode.get(Constants.FIELD_VALUE).asText() + "'";
        }

        currentParentHolder[0] = nextParent;
        return null;
    }

    private JsonNode findChildNodeByValue(JsonNode nodes, String valueStr) {
        for (JsonNode node : nodes) {
            if (node.has(Constants.FIELD_VALUE) && valueStr.equals(node.get(Constants.FIELD_VALUE).asText())) {
                return node;
            }
        }
        return null;
    }

    /**
     * Restructures data to group by organization ID
     */
    private List<Map<String, Object>> restructureByOrgId(List<Map<String, Object>> existingData,
                                                         String currentOrgId,
                                                         List<Map<String, Object>> newCustomFieldValues) {

        Map<String, List<Map<String, Object>>> orgMap = new HashMap<>();
        for (Map<String, Object> item : existingData) {
            if (item.containsKey(Constants.ORGANISATION_ID) && item.containsKey(Constants.CUSTOM_FIELD_VALUES)) {
                String orgId = (String) item.get(Constants.ORGANISATION_ID);
                orgMap.put(orgId, (List<Map<String, Object>>) item.get(Constants.CUSTOM_FIELD_VALUES));
            } else if (item.containsKey(Constants.ORGANISATION_ID)) {
                String orgId = (String) item.get(Constants.ORGANISATION_ID);
                orgMap.computeIfAbsent(orgId, k -> new ArrayList<>()).add(item);
            }
        }
        orgMap.put(currentOrgId, newCustomFieldValues);

        List<Map<String, Object>> result = new ArrayList<>();
        for (Map.Entry<String, List<Map<String, Object>>> entry : orgMap.entrySet()) {
            Map<String, Object> orgData = new HashMap<>();
            orgData.put(Constants.ORGANISATION_ID, entry.getKey());
            orgData.put(Constants.CUSTOM_FIELD_VALUES, entry.getValue());
            result.add(orgData);
        }
        return result;
    }

    /**
     * Retrieves a custom field from PostgreSQL by its ID
     *
     * @param customFieldId ID of the custom field to retrieve
     * @return CustomFieldEntity if found, null otherwise
     */
    private CustomFieldEntity getCustomFieldById(String customFieldId) {
        try {
            return customFieldRepository.findByCustomFiledIdAndIsActiveTrue(customFieldId).orElse(null);
        } catch (Exception e) {
            log.error("Error retrieving custom field with ID {}: {}", customFieldId, e.getMessage(), e);
            return null;
        }
    }

    @Override
    public ApiResponse getAdditionalFieldsByOrg(String userId, String orgId, String authToken,boolean userOrAdmin) {
        ApiResponse response = ProjectUtil.createDefaultResponse("api.get.additionalFieldsByOrg");
        String userIdFromToken = accessTokenValidator.fetchUserIdFromAccessToken(authToken);

        if (StringUtils.isBlank(authToken)) {
            ProjectUtil.errorResponse(response, INVALID_OR_MISSING_ACCESS_TOKEN_MSG, HttpStatus.UNAUTHORIZED);
            return response;
        }

        if (userOrAdmin) {
            userId = userIdFromToken;
        } else if (StringUtils.isBlank(userId)) {
            ProjectUtil.errorResponse(response, INVALID_USER_ID_MSG, HttpStatus.UNAUTHORIZED);
            return response;
        }


        try {
            String contextType = Constants.ORG_ADDITIONAL_PROPERTIES;
            List<Map<String, Object>> dataList = getExistingContextData(userId, contextType);

            // Find data for the specified organization
            Map<String, Object> orgData = null;
            for (Map<String, Object> item : dataList) {
                String itemOrgId = (String) item.get(Constants.ORGANISATION_ID);
                if (orgId.equals(itemOrgId)) {
                    orgData = item;
                    break;
                }
            }

            if (MapUtils.isEmpty(orgData)) {
                response.setResponseCode(HttpStatus.OK);
                response.put(Constants.RESPONSE, Collections.emptyMap());
                return response;
            }
            response.setResponseCode(HttpStatus.OK);
            response.put(Constants.RESPONSE, orgData);
            return response;
        } catch (Exception e) {
            log.error("Error retrieving additional fields for userId: {} and orgId: {}", userId, orgId, e);
            ProjectUtil.errorResponse(response, "Internal server error", HttpStatus.INTERNAL_SERVER_ERROR);
            return response;
        }
    }

    private List<Map<String, Object>> transformOrgCustomFieldsForES(List<Map<String, Object>> restructuredData) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> orgEntry : restructuredData) {
            String orgId = (String) orgEntry.get(Constants.ORGANISATION_ID);
            List<Map<String, Object>> customFieldValues = (List<Map<String, Object>>) orgEntry.get(Constants.CUSTOM_FIELD_VALUES);
            List<Map<String, Object>> fields = new ArrayList<>();

            for (Map<String, Object> field : customFieldValues) {
                String type = (String) field.get(Constants.FIELD_TYPE);
                if (Constants.MASTER_LIST.equals(type)) {
                    List<Map<String, Object>> values = (List<Map<String, Object>>) field.get(Constants.VALUES);
                    for (Map<String, Object> value : values) {
                        String attr = (String) value.get(Constants.ATTRIBUTE_NAME);
                        Object val = value.get(Constants.VALUE);
                        fields.add(Map.of(attr, val));
                    }
                } else if (Constants.TEXT.equals(type)) {
                    String attr = (String) field.get(Constants.ATTRIBUTE_NAME);
                    Object val = field.get(Constants.VALUE);
                    fields.add(Map.of(attr, val));
                }
            }

            Map<String, Object> orgFields = new HashMap<>();
            orgFields.put(Constants.ORG_ID, orgId);
            orgFields.put(Constants.FIELDS, fields);
            result.add(orgFields);
        }
        return result;
    }

    private int getUserBadgeCount(String userId) {
        String redisKey = Constants.USER_BADGE_COUNT + userId;

        try {
            String cachedValue = cacheService.getCache(redisKey);
            if (StringUtils.isNotBlank(cachedValue)) {
                return Integer.parseInt(cachedValue);
            }

            List<Map<String, Object>> records = cassandraOperation.getRecordsByPropertiesByKey(Constants.KEYSPACE_SUNBIRD_COURSES,Constants.USER_BADGE_LOOKUP_TABLE,
                    Map.of(Constants.USERID_KEY, userId), List.of(Constants.COURSE_ID), userId);
            int totalPoints = 0;
            if(!CollectionUtils.isEmpty(records)){
                totalPoints= records.size();
            }
            cacheService.putCache(redisKey, totalPoints, serverConfig.getBadgeCountRedisTtl());
            return totalPoints;

        } catch (Exception e) {
            log.warn("Failed to fetch badge count for userId {}: {}", userId, e.getMessage());
            return 0;
        }
    }

    private boolean validateField(String listKey, String fieldName, String fieldValue) {
        boolean retValue = true;
        if (Constants.EDUCATIONAL_QUALIFICATIONS.equals(listKey)) {
            switch (fieldName) {
                case Constants.DEGREE:
                    retValue = fieldValue.matches(serverConfig.getDegreeNameRegex());
                    break;
                case Constants.INSTITUTE_NAME:
                    retValue = fieldValue.matches(serverConfig.getInstituteNameRegex());
                    break;
                case Constants.FIELD_OF_STUDY:
                    retValue = fieldValue.matches(serverConfig.getFieldOfStudyRegex());
                    break;
                case Constants.START_YEAR, Constants.END_YEAR:
                    retValue = fieldValue.matches(serverConfig.getYearRegex());
                    break;
                case Constants.UUID:
                    retValue = fieldValue.matches(serverConfig.getUuidRegex());
                    break;
                default:
                    break;
            }
        }
        return retValue;
    }
}
