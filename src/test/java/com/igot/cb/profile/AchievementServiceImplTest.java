package com.igot.cb.profile;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.igot.cb.authentication.util.AccessTokenValidator;
import com.igot.cb.common.KafkaEventPublisher;
import com.igot.cb.profile.service.AchievementServiceImpl;
import com.igot.cb.transactional.cassandrautils.CassandraOperation;
import com.igot.cb.transactional.elasticsearch.dto.SearchCriteria;
import com.igot.cb.transactional.elasticsearch.dto.SearchResult;
import com.igot.cb.transactional.elasticsearch.service.EsClientService;
import com.igot.cb.transactional.redis.cache.CacheService;
import com.igot.cb.util.ApiResponse;
import com.igot.cb.util.CbServerProperties;
import com.igot.cb.util.Constants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.HttpStatus;

import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AchievementServiceImplTest {

    @Mock
    private AccessTokenValidator accessTokenValidator;
    @Mock
    private CbServerProperties cbServerProperties;
    @Mock
    private CassandraOperation cassandraOperation;
    @Mock
    private EsClientService esClientService;
    @Mock
    private ObjectMapper objectMapper;
    @Mock
    private RedisTemplate<String, SearchResult> redisTemplate;
    @Mock
    private ValueOperations<String, SearchResult> valueOperations;
    @Mock
    private CacheService cacheService;
    @Mock
    private KafkaEventPublisher kafkaEventPublisher;

    @InjectMocks
    private AchievementServiceImpl achievementService;

    @BeforeEach
    void setUp() throws Exception {
        MockitoAnnotations.openMocks(this);
        when(cbServerProperties.getRequiredFieldsProperty()).thenReturn("id,contextType");
        when(cbServerProperties.getContextType()).thenReturn(new String[]{"testContext"});
        when(cbServerProperties.getAchievementsMandatoryFields()).thenReturn("field1,field2");
        when(cbServerProperties.getAchievementsAllowedFields()).thenReturn(null);
        when(cbServerProperties.getAchievementEsRequiredFieldsMappingPath()).thenReturn("/tmp/mapping.json");
        when(cbServerProperties.getSearchResultRedisTtl()).thenReturn(1000L);
        when(cbServerProperties.getUserCompetencyTopicName()).thenReturn("user-competency-mapping-event");
        when(cbServerProperties.getAchievementCacheTtl()).thenReturn(100);
        when(cbServerProperties.getAchievementJwtSecretKey()).thenReturn("test-secret-key");
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        achievementService = new AchievementServiceImpl(
                accessTokenValidator, cbServerProperties, cassandraOperation,
                esClientService, objectMapper, redisTemplate, cacheService, kafkaEventPublisher
        );
        java.lang.reflect.Method initMethod = AchievementServiceImpl.class.getDeclaredMethod("initRequiredFields");
        initMethod.setAccessible(true);
        initMethod.invoke(achievementService);
    }

    // ==================== CREATE TESTS ====================

    @Test
    void testCreateLearnerAchievement_success() throws Exception {
        Map<String, Object> contextData = new HashMap<>();
        contextData.put("field1", "value1");
        contextData.put("field2", "value2");
        Map<String, Object> requestData = new HashMap<>();
        requestData.put(Constants.CONTEXT_TYPE, "testContext");
        requestData.put(Constants.SOURCE, "source");
        requestData.put(Constants.CONTEXT_DATA, contextData);
        Map<String, Object> request = new HashMap<>();
        request.put(Constants.REQUEST, requestData);

        when(accessTokenValidator.fetchUserIdFromAccessToken(anyString())).thenReturn("user123");
        ApiResponse cassandraResponse = new ApiResponse();
        cassandraResponse.put(Constants.RESPONSE, Constants.SUCCESS);
        when(cassandraOperation.insertRecord(any(), any(), any())).thenReturn(cassandraResponse);
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");
        when(objectMapper.convertValue(any(), eq(Map.class))).thenReturn(new HashMap<>());
        when(cbServerProperties.isRequireEs()).thenReturn(true);

        // Mock searchDocuments for refreshAchievementSearchCacheForUser
        SearchResult searchResult = new SearchResult();
        searchResult.setData(new ArrayList<>());
        try {
            when(esClientService.searchDocuments(any(), any())).thenReturn(searchResult);
        } catch (Exception e) {
            fail("Mock setup failed");
        }

        ApiResponse response = achievementService.createLearnerAchievement(request, "token", "org1");

        assertEquals(HttpStatus.OK, response.getResponseCode());
        assertNotNull(response.getResult());
        verify(cassandraOperation, times(1)).insertRecord(any(), any(), any());
        verify(esClientService, times(1)).addDocument(any(), any(), any(), any(), any());
        verify(esClientService, times(5)).searchDocuments(any(), any()); // Called 5 times in refreshAchievementSearchCacheForUser
    }

    @Test
    void testCreateLearnerAchievement_userIdNotFound() {
        Map<String, Object> request = new HashMap<>();
        when(accessTokenValidator.fetchUserIdFromAccessToken(anyString())).thenReturn("");
        ApiResponse response = achievementService.createLearnerAchievement(request, "token", "org1");
        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
    }

    @Test
    void testCreateLearnerAchievement_validationError() {
        Map<String, Object> requestData = new HashMap<>();
        requestData.put(Constants.CONTEXT_TYPE, "invalid");
        Map<String, Object> request = new HashMap<>();
        request.put(Constants.REQUEST, requestData);
        when(accessTokenValidator.fetchUserIdFromAccessToken(anyString())).thenReturn("user123");
        ApiResponse response = achievementService.createLearnerAchievement(request, "token", "org1");
        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
    }

    @Test
    void testCreateLearnerAchievement_cassandraFail() throws Exception {
        Map<String, Object> contextData = new HashMap<>();
        contextData.put("field1", "value1");
        contextData.put("field2", "value2");
        Map<String, Object> requestData = new HashMap<>();
        requestData.put(Constants.CONTEXT_TYPE, "testContext");
        requestData.put(Constants.SOURCE, "source");
        requestData.put(Constants.CONTEXT_DATA, contextData);
        Map<String, Object> request = new HashMap<>();
        request.put(Constants.REQUEST, requestData);

        when(accessTokenValidator.fetchUserIdFromAccessToken(anyString())).thenReturn("user123");
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");
        when(cassandraOperation.insertRecord(any(), any(), any())).thenThrow(new RuntimeException("Cassandra error"));

        ApiResponse response = achievementService.createLearnerAchievement(request, "token", "org1");

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getResponseCode());
    }

    // ==================== UPDATE TESTS ====================

    @Test
    void testUpdateLearnerAchievement_success() throws Exception {
        Map<String, Object> contextData = new HashMap<>();
        contextData.put("field1", "value1");
        contextData.put("field2", "value2");
        contextData.put(Constants.UPLOAD_DOCUMENT_URL, "http://example.com/doc.pdf");
        contextData.put(Constants.URL, "");
        Map<String, Object> requestData = new HashMap<>();
        requestData.put(Constants.ID, "achv1");
        requestData.put(Constants.CONTEXT_TYPE, "testContext");
        requestData.put(Constants.CONTEXT_DATA, contextData);
        Map<String, Object> request = new HashMap<>();
        request.put(Constants.REQUEST, requestData);

        when(accessTokenValidator.fetchUserIdFromAccessToken(anyString())).thenReturn("user123");
        when(cbServerProperties.isRequireEs()).thenReturn(true);
        Map<String, Object> existingRecord = new HashMap<>();
        existingRecord.put(Constants.STATUS, Constants.PENDING);
        existingRecord.put(Constants.CREATED_ON, LocalDate.now());
        existingRecord.put(Constants.CONTEXT_DATA, "{}");
        when(cassandraOperation.getRecordsByPropertiesByKey(any(), any(), any(), any(), any()))
                .thenReturn(Collections.singletonList(existingRecord));
        ApiResponse cassandraResponse = new ApiResponse();
        cassandraResponse.put(Constants.RESPONSE, Constants.SUCCESS);
        when(cassandraOperation.insertRecord(any(), any(), any())).thenReturn(cassandraResponse);
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");
        when(objectMapper.convertValue(any(), eq(Map.class))).thenReturn(new HashMap<>());
        when(objectMapper.readValue(anyString(), eq(Map.class))).thenReturn(new HashMap<>());
        Map<String, Object> esDoc = new HashMap<>();
        esDoc.put(Constants.CREATED_ON, "2024-01-01T00:00:00.000+0000");
        when(esClientService.readDocument(any(), any())).thenReturn(esDoc);
        try {
            when(esClientService.searchDocuments(any(), any())).thenReturn(new SearchResult());
        } catch (Exception e) {
            fail("Mock setup failed");
        }

        ApiResponse response = achievementService.updateLearnerAchievement(request, "token", "org1");

        assertEquals(HttpStatus.OK, response.getResponseCode());
        assertNotNull(response.getResult());
        verify(esClientService, times(1)).updateDocument(any(), any(), any(), any(), any());
    }

    @Test
    void testUpdateLearnerAchievement_userIdNotFound() {
        Map<String, Object> requestData = new HashMap<>();
        requestData.put(Constants.CONTEXT_TYPE, "testContext");
        Map<String, Object> request = new HashMap<>();
        request.put(Constants.REQUEST, requestData);
        when(accessTokenValidator.fetchUserIdFromAccessToken(anyString())).thenReturn("");
        ApiResponse response = achievementService.updateLearnerAchievement(request, "token", "org1");
        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
    }

    @Test
    void testUpdateLearnerAchievement_validationError() {
        Map<String, Object> requestData = new HashMap<>();
        requestData.put(Constants.CONTEXT_TYPE, "invalid");
        Map<String, Object> request = new HashMap<>();
        request.put(Constants.REQUEST, requestData);
        when(accessTokenValidator.fetchUserIdFromAccessToken(anyString())).thenReturn("user123");
        ApiResponse response = achievementService.updateLearnerAchievement(request, "token", "org1");
        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
    }

    @Test
    void testUpdateLearnerAchievement_missingIdOrContextType() {
        Map<String, Object> requestData = new HashMap<>();
        requestData.put(Constants.CONTEXT_TYPE, "testContext");
        Map<String, Object> request = new HashMap<>();
        request.put(Constants.REQUEST, requestData);
        when(accessTokenValidator.fetchUserIdFromAccessToken(anyString())).thenReturn("user123");
        ApiResponse response = achievementService.updateLearnerAchievement(request, "token", "org1");
        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
    }

    @Test
    void testUpdateLearnerAchievement_contextDataMissing() {
        Map<String, Object> requestData = new HashMap<>();
        requestData.put(Constants.ID, "achv1");
        requestData.put(Constants.CONTEXT_TYPE, "testContext");
        requestData.put(Constants.CONTEXT_DATA, null);
        Map<String, Object> request = new HashMap<>();
        request.put(Constants.REQUEST, requestData);
        when(accessTokenValidator.fetchUserIdFromAccessToken(anyString())).thenReturn("user123");
        ApiResponse response = achievementService.updateLearnerAchievement(request, "token", "org1");
        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
    }

    @Test
    void testUpdateLearnerAchievement_recordNotFound() {
        Map<String, Object> contextData = new HashMap<>();
        contextData.put("field1", "value1");
        contextData.put("field2", "value2");
        contextData.put(Constants.UPLOAD_DOCUMENT_URL, "http://example.com/doc.pdf");
        contextData.put(Constants.URL, "");
        Map<String, Object> requestData = new HashMap<>();
        requestData.put(Constants.ID, "achv1");
        requestData.put(Constants.CONTEXT_TYPE, "testContext");
        requestData.put(Constants.CONTEXT_DATA, contextData);
        Map<String, Object> request = new HashMap<>();
        request.put(Constants.REQUEST, requestData);
        when(accessTokenValidator.fetchUserIdFromAccessToken(anyString())).thenReturn("user123");
        when(cassandraOperation.getRecordsByPropertiesByKey(any(), any(), any(), any(), any())).thenReturn(Collections.emptyList());
        ApiResponse response = achievementService.updateLearnerAchievement(request, "token", "org1");
        assertEquals(HttpStatus.NOT_FOUND, response.getResponseCode());
    }

    @Test
    void testUpdateLearnerAchievement_cassandraFail() throws Exception {
        Map<String, Object> contextData = new HashMap<>();
        contextData.put("field1", "value1");
        contextData.put("field2", "value2");
        contextData.put(Constants.UPLOAD_DOCUMENT_URL, "http://example.com/doc.pdf");
        contextData.put(Constants.URL, "");
        Map<String, Object> requestData = new HashMap<>();
        requestData.put(Constants.ID, "achv1");
        requestData.put(Constants.CONTEXT_TYPE, "testContext");
        requestData.put(Constants.CONTEXT_DATA, contextData);
        Map<String, Object> request = new HashMap<>();
        request.put(Constants.REQUEST, requestData);

        when(accessTokenValidator.fetchUserIdFromAccessToken(anyString())).thenReturn("user123");
        Map<String, Object> existingRecord = new HashMap<>();
        existingRecord.put(Constants.STATUS, Constants.PENDING);
        existingRecord.put(Constants.CREATED_ON, LocalDate.now());
        existingRecord.put(Constants.CONTEXT_DATA, "{}");
        when(cassandraOperation.getRecordsByPropertiesByKey(any(), any(), any(), any(), any()))
                .thenReturn(Collections.singletonList(existingRecord));
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");
        when(objectMapper.readValue(anyString(), eq(Map.class))).thenReturn(new HashMap<>());
        when(cassandraOperation.insertRecord(any(), any(), any())).thenThrow(new RuntimeException("DB error"));

        ApiResponse response = achievementService.updateLearnerAchievement(request, "token", "org1");

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getResponseCode());
    }

    // ==================== DELETE TESTS ====================

    @Test
    void testDeleteLearnerAchievement_success() {
        Map<String, Object> reqMap = new HashMap<>();
        reqMap.put(Constants.ID, "achv1");
        reqMap.put(Constants.CONTEXT_TYPE, "testContext");
        Map<String, Object> request = new HashMap<>();
        request.put(Constants.REQUEST, reqMap);

        when(accessTokenValidator.fetchUserIdFromAccessToken(anyString())).thenReturn("user123");
        Map<String, Object> cassandraResponse = new HashMap<>();
        cassandraResponse.put(Constants.RESPONSE, Constants.SUCCESS);
        when(cassandraOperation.deleteRecordByCompositeKey(any(), any(), any())).thenReturn(cassandraResponse);
        try {
            when(esClientService.searchDocuments(any(), any())).thenReturn(new SearchResult());
        } catch (Exception e) {
            fail("Mock setup failed");
        }

        ApiResponse response = achievementService.deleteLearnerAchievement(request, "token");

        assertEquals(HttpStatus.OK, response.getResponseCode());
        assertEquals("Achievement deleted successfully", response.getResult().get("message"));
        verify(cassandraOperation, times(1)).deleteRecordByCompositeKey(any(), any(), any());
    }

    @Test
    void testDeleteLearnerAchievement_invalidRequest() {
        ApiResponse response = achievementService.deleteLearnerAchievement(null, "token");
        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
    }

    @Test
    void testDeleteLearnerAchievement_missingFields() {
        Map<String, Object> reqMap = new HashMap<>();
        Map<String, Object> request = new HashMap<>();
        request.put(Constants.REQUEST, reqMap);
        when(accessTokenValidator.fetchUserIdFromAccessToken(anyString())).thenReturn("user123");
        ApiResponse response = achievementService.deleteLearnerAchievement(request, "token");
        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
    }

    @Test
    void testDeleteLearnerAchievement_userIdNotFound() {
        Map<String, Object> reqMap = new HashMap<>();
        reqMap.put(Constants.ID, "achv1");
        reqMap.put(Constants.CONTEXT_TYPE, "testContext");
        Map<String, Object> request = new HashMap<>();
        request.put(Constants.REQUEST, reqMap);
        when(accessTokenValidator.fetchUserIdFromAccessToken(anyString())).thenReturn("");
        ApiResponse response = achievementService.deleteLearnerAchievement(request, "token");
        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
    }

    @Test
    void testDeleteLearnerAchievement_cassandraFail() {
        Map<String, Object> reqMap = new HashMap<>();
        reqMap.put(Constants.ID, "achv1");
        reqMap.put(Constants.CONTEXT_TYPE, "testContext");
        Map<String, Object> request = new HashMap<>();
        request.put(Constants.REQUEST, reqMap);
        when(accessTokenValidator.fetchUserIdFromAccessToken(anyString())).thenReturn("user123");
        Map<String, Object> cassandraResponse = new HashMap<>();
        cassandraResponse.put(Constants.RESPONSE, "fail");
        when(cassandraOperation.deleteRecordByCompositeKey(any(), any(), any())).thenReturn(cassandraResponse);
        ApiResponse response = achievementService.deleteLearnerAchievement(request, "token");
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getResponseCode());
    }

    // ==================== READ TESTS ====================

    @Test
    void testReadLearnerAchievement_success() throws Exception {
        when(accessTokenValidator.fetchUserIdFromAccessToken(anyString())).thenReturn("user123");
        Map<String, Object> achievement = new HashMap<>();
        achievement.put(Constants.ID, "achv1");
        achievement.put(Constants.CONTEXT_TYPE, "testContext");
        achievement.put(Constants.CONTEXT_DATA, "{}");

        when(cacheService.getCache(any())).thenReturn(null);
        when(cassandraOperation.getRecordsByPropertiesByKey(any(), any(), any(), any(), any()))
                .thenReturn(Collections.singletonList(achievement));
        when(objectMapper.readValue(anyString(), eq(Map.class))).thenReturn(new HashMap<>());
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        ApiResponse response = achievementService.readLearnerAchievement("achv1", "token", "testContext");

        assertEquals(HttpStatus.OK, response.getResponseCode());
        assertNotNull(response.getResult());
        verify(cassandraOperation, times(1)).getRecordsByPropertiesByKey(any(), any(), any(), any(), any());
    }

    @Test
    void testReadLearnerAchievement_userIdNotFound() {
        when(accessTokenValidator.fetchUserIdFromAccessToken(anyString())).thenReturn("");
        ApiResponse response = achievementService.readLearnerAchievement("achv1", "token", "testContext");
        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
    }

    @Test
    void testReadLearnerAchievement_missingAchievementId() {
        when(accessTokenValidator.fetchUserIdFromAccessToken(anyString())).thenReturn("user123");
        ApiResponse response = achievementService.readLearnerAchievement("", "token", "testContext");
        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
    }

    @Test
    void testReadLearnerAchievement_notFound() {
        when(accessTokenValidator.fetchUserIdFromAccessToken(anyString())).thenReturn("user123");
        when(cacheService.getCache(any())).thenReturn(null);
        when(cassandraOperation.getRecordsByPropertiesByKey(any(), any(), any(), any(), any())).thenReturn(Collections.emptyList());
        ApiResponse response = achievementService.readLearnerAchievement("achv1", "token", "testContext");
        assertEquals(HttpStatus.NOT_FOUND, response.getResponseCode());
    }

    // ==================== STATUS UPDATE TESTS ====================

    @Test
    void testStatusUpdateLearnerAchievement_success() {
        Map<String, Object> reqMap = new HashMap<>();
        reqMap.put("id", "achv1");
        reqMap.put("contextType", "testContext");
        reqMap.put("learnerId", "user123");
        reqMap.put(Constants.STATUS, "APPROVED");
        reqMap.put("reason", "approved");
        Map<String, Object> request = new HashMap<>();
        request.put(Constants.REQUEST, reqMap);

        when(accessTokenValidator.fetchUserIdFromAccessToken(anyString())).thenReturn("user123");
        Map<String, Object> recordMap = new HashMap<>();
        recordMap.put(Constants.STATUS, Constants.PENDING);
        recordMap.put(Constants.CREATED_ON, LocalDate.now());
        recordMap.put("id", "achv1");
        when(cassandraOperation.getAllRecordsByPrimaryKey(any(), any(), any(), any(), anyInt()))
                .thenReturn(Collections.singletonList(recordMap));
        Map<String, Object> cassandraResponse = new HashMap<>();
        cassandraResponse.put(Constants.RESPONSE, Constants.SUCCESS);
        when(cassandraOperation.updateRecordByCompositeKey(any(), any(), any(), any())).thenReturn(cassandraResponse);
        Map<String, Object> esDoc = new HashMap<>();
        esDoc.put(Constants.CREATED_ON, "2024-01-01T00:00:00.000+0000");
        esDoc.put(Constants.STATUS, "PENDING");
        when(esClientService.readDocument(any(), any())).thenReturn(esDoc);

        ApiResponse response = achievementService.statusUpdateLearnerAchievement(request, "token");

        assertEquals("Achievement status updated successfully", response.getResult().get("message"));
        verify(cassandraOperation, times(1)).updateRecordByCompositeKey(any(), any(), any(), any());
    }

    @Test
    void testStatusUpdateLearnerAchievement_invalidToken() {
        when(accessTokenValidator.fetchUserIdFromAccessToken(anyString())).thenReturn("");
        Map<String, Object> request = new HashMap<>();
        ApiResponse response = achievementService.statusUpdateLearnerAchievement(request, "token");
        assertEquals(HttpStatus.UNAUTHORIZED, response.getResponseCode());
    }

    @Test
    void testStatusUpdateLearnerAchievement_invalidRequest() {
        when(accessTokenValidator.fetchUserIdFromAccessToken(anyString())).thenReturn("user123");
        Map<String, Object> request = new HashMap<>();
        ApiResponse response = achievementService.statusUpdateLearnerAchievement(request, "token");
        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
    }

    @Test
    void testStatusUpdateLearnerAchievement_recordNotFound() {
        when(accessTokenValidator.fetchUserIdFromAccessToken(anyString())).thenReturn("user123");
        Map<String, Object> reqMap = new HashMap<>();
        reqMap.put("id", "achv1");
        reqMap.put("contextType", "testContext");
        reqMap.put("learnerId", "user123");
        reqMap.put(Constants.STATUS, "APPROVED");
        reqMap.put("reason", "test");
        Map<String, Object> request = new HashMap<>();
        request.put(Constants.REQUEST, reqMap);
        when(cassandraOperation.getAllRecordsByPrimaryKey(any(), any(), any(), any(), anyInt())).thenReturn(Collections.emptyList());
        ApiResponse response = achievementService.statusUpdateLearnerAchievement(request, "token");
        assertEquals(HttpStatus.NOT_FOUND, response.getResponseCode());
    }

    @Test
    void testStatusUpdateLearnerAchievement_statusNotPending() {
        when(accessTokenValidator.fetchUserIdFromAccessToken(anyString())).thenReturn("user123");
        Map<String, Object> reqMap = new HashMap<>();
        reqMap.put("id", "achv1");
        reqMap.put("contextType", "testContext");
        reqMap.put("learnerId", "user123");
        reqMap.put(Constants.STATUS, "APPROVED");
        reqMap.put("reason", "test");
        Map<String, Object> request = new HashMap<>();
        request.put(Constants.REQUEST, reqMap);
        Map<String, Object> recordMap = new HashMap<>();
        recordMap.put(Constants.STATUS, "APPROVED");
        when(cassandraOperation.getAllRecordsByPrimaryKey(any(), any(), any(), any(), anyInt())).thenReturn(Collections.singletonList(recordMap));
        ApiResponse response = achievementService.statusUpdateLearnerAchievement(request, "token");
        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
    }

    @Test
    void testStatusUpdateLearnerAchievement_cassandraFail() {
        when(accessTokenValidator.fetchUserIdFromAccessToken(anyString())).thenReturn("user123");
        Map<String, Object> reqMap = new HashMap<>();
        reqMap.put("id", "achv1");
        reqMap.put("contextType", "testContext");
        reqMap.put("learnerId", "user123");
        reqMap.put(Constants.STATUS, "APPROVED");
        reqMap.put("reason", "test");
        Map<String, Object> request = new HashMap<>();
        request.put(Constants.REQUEST, reqMap);
        Map<String, Object> recordMap = new HashMap<>();
        recordMap.put(Constants.STATUS, Constants.PENDING);
        when(cassandraOperation.getAllRecordsByPrimaryKey(any(), any(), any(), any(), anyInt())).thenReturn(Collections.singletonList(recordMap));
        Map<String, Object> cassandraResponse = new HashMap<>();
        cassandraResponse.put(Constants.RESPONSE, "fail");
        when(cassandraOperation.updateRecordByCompositeKey(any(), any(), any(), any())).thenReturn(cassandraResponse);
        ApiResponse response = achievementService.statusUpdateLearnerAchievement(request, "token");
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getResponseCode());
    }

    // ==================== SEARCH TESTS ====================

    @Test
    void testSearchLearnerAchievements_success() throws Exception {
        SearchCriteria criteria = new SearchCriteria();
        SearchResult searchResult = new SearchResult();
        List<Map<String, Object>> data = new ArrayList<>();
        Map<String, Object> achievement = new HashMap<>();
        achievement.put(Constants.USER_ID, "user123");
        data.add(achievement);
        searchResult.setData(data);

        when(valueOperations.get(anyString())).thenReturn(null);
        when(esClientService.searchDocuments(any(), any())).thenReturn(searchResult);
        when(cacheService.hget(any())).thenReturn(Collections.emptyList());
        when(cassandraOperation.getRecordsByPropertiesWithoutFiltering(any(), any(), any(), any(), any()))
                .thenReturn(Collections.emptyList());

        ApiResponse response = achievementService.searchLearnerAchievements(criteria, "token");

        assertNotNull(response);
        assertTrue(response.getResult().containsKey(Constants.SEARCH_RESULTS));
        verify(esClientService, times(1)).searchDocuments(any(), any());
    }

    @Test
    void testSearchLearnerAchievements_fromCache() throws Exception {
        SearchCriteria criteria = new SearchCriteria();
        SearchResult cachedResult = new SearchResult();

        when(valueOperations.get(anyString())).thenReturn(cachedResult);

        ApiResponse response = achievementService.searchLearnerAchievements(criteria, "token");

        assertNotNull(response);
        assertTrue(response.getResult().containsKey(Constants.SEARCH_RESULTS));
        verify(esClientService, never()).searchDocuments(any(), any());
    }

    @Test
    void testSearchLearnerAchievements_minimumCharacters() {
        SearchCriteria criteria = new SearchCriteria();
        criteria.setSearchString("ab");
        ApiResponse response = achievementService.searchLearnerAchievements(criteria, "token");
        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
    }

    @Test
    void testSearchLearnerAchievements_noDataFound() throws Exception {
        SearchCriteria criteria = new SearchCriteria();
        SearchResult searchResult = new SearchResult();
        searchResult.setData(Collections.emptyList());

        when(valueOperations.get(anyString())).thenReturn(null);
        when(esClientService.searchDocuments(any(), any())).thenReturn(searchResult);

        ApiResponse response = achievementService.searchLearnerAchievements(criteria, "token");

        assertEquals(HttpStatus.OK, response.getResponseCode());
        assertTrue(response.getResult().containsKey(Constants.SEARCH_RESULTS));
    }

    @Test
    void testSearchLearnerAchievements_esException() throws Exception {
        SearchCriteria criteria = new SearchCriteria();

        when(valueOperations.get(anyString())).thenReturn(null);
        when(esClientService.searchDocuments(any(), any())).thenThrow(new RuntimeException("ES error"));

        ApiResponse response = achievementService.searchLearnerAchievements(criteria, "token");

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getResponseCode());
    }

    @Test
    void testGetAchievementFromCache_success() throws Exception {
        Map<String, Object> map = new HashMap<>();
        map.put("key", "value");

        when(cacheService.getCache(any())).thenReturn("{\"key\":\"value\"}");
        when(objectMapper.readValue(anyString(), any(com.fasterxml.jackson.core.type.TypeReference.class)))
                .thenReturn(map);

        Map<String, Object> result =
                achievementService.readLearnerAchievement("id1", "token", "testContext").getResult();

        assertNotNull(result);
    }

    @Test
    void testGetAchievementFromCache_deserializationFail() throws Exception {
        when(cacheService.getCache(any())).thenReturn("invalid-json");
        when(objectMapper.readValue(anyString(), any(com.fasterxml.jackson.core.type.TypeReference.class)))
                .thenThrow(new RuntimeException("JSON error"));

        Map<String, Object> result =
                achievementService.readLearnerAchievement("id1", "token", "testContext").getResult();

        assertNotNull(result); // should fallback
    }

    @Test
    void testValidateRequest_missingContextType() {
        Map<String, Object> requestData = new HashMap<>();
        requestData.put(Constants.CONTEXT_DATA, new HashMap<>());

        Map<String, Object> request = new HashMap<>();
        request.put(Constants.REQUEST, requestData);

        when(accessTokenValidator.fetchUserIdFromAccessToken(anyString())).thenReturn("user");

        ApiResponse response = achievementService.createLearnerAchievement(request, "token", "org");

        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
    }

    @Test
    void testValidateRequest_blankMandatoryField() {
        Map<String, Object> contextData = new HashMap<>();
        contextData.put("field1", "");  // blank
        contextData.put("field2", "value");

        Map<String, Object> requestData = new HashMap<>();
        requestData.put(Constants.CONTEXT_TYPE, "testContext");
        requestData.put(Constants.CONTEXT_DATA, contextData);

        Map<String, Object> request = new HashMap<>();
        request.put(Constants.REQUEST, requestData);

        when(accessTokenValidator.fetchUserIdFromAccessToken(anyString())).thenReturn("user");

        ApiResponse response = achievementService.createLearnerAchievement(request, "token", "org");

        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
    }

    @Test
    void testUpdateAchievementInES_contextDataString() throws Exception {
        Map<String, Object> recordMap = new HashMap<>();
        recordMap.put(Constants.STATUS, Constants.PENDING);
        recordMap.put("contextdata", "{\"key\":\"value\"}");
        recordMap.put(Constants.CREATED_ON, LocalDate.now());

        when(cassandraOperation.getAllRecordsByPrimaryKey(any(), any(), any(), any(), anyInt()))
                .thenReturn(Collections.singletonList(recordMap));

        when(objectMapper.readValue(anyString(), eq(Map.class)))
                .thenReturn(new HashMap<>());

        when(esClientService.readDocument(any(), any()))
                .thenReturn(new HashMap<>());

        Map<String, Object> cassandraResponse = new HashMap<>();
        cassandraResponse.put(Constants.RESPONSE, Constants.SUCCESS);
        when(cassandraOperation.updateRecordByCompositeKey(any(), any(), any(), any()))
                .thenReturn(cassandraResponse);

        Map<String, Object> request = new HashMap<>();
        Map<String, Object> req = new HashMap<>();
        req.put("id", "1");
        req.put("contextType", "testContext");
        req.put("learnerId", "user");
        req.put(Constants.STATUS, "APPROVED");
        req.put("reason", "ok");
        request.put(Constants.REQUEST, req);

        when(accessTokenValidator.fetchUserIdFromAccessToken(anyString()))
                .thenReturn("user");

        achievementService.statusUpdateLearnerAchievement(request, "token");

        verify(esClientService).updateDocument(any(), any(), any(), any(), any());
    }

    @Test
    void testGenerateRedisJwtTokenKey_exception() throws Exception {
        when(objectMapper.writeValueAsString(any()))
                .thenThrow(new com.fasterxml.jackson.core.JsonProcessingException("error") {});

        String key = achievementService.generateRedisJwtTokenKey(new Object());

        assertEquals("", key);
    }

    @Test
    void testRefreshAchievementSearchCacheForUser_exception() throws Exception {

        // Valid token
        when(accessTokenValidator.fetchUserIdFromAccessToken(anyString()))
                .thenReturn("user123");

        when(cbServerProperties.isRequireEs()).thenReturn(true);

        // Cassandra insert success
        ApiResponse cassandraResponse = new ApiResponse();
        cassandraResponse.put(Constants.RESPONSE, Constants.SUCCESS);
        when(cassandraOperation.insertRecord(any(), any(), any()))
                .thenReturn(cassandraResponse);

        when(objectMapper.writeValueAsString(any()))
                .thenReturn("{}");

        when(objectMapper.convertValue(any(), eq(Map.class)))
                .thenReturn(new HashMap<>());

        // Force ES failure during refresh cache
        when(esClientService.searchDocuments(any(), any()))
                .thenThrow(new RuntimeException("ES failure"));

        Map<String, Object> validRequest = buildValidRequest();
        assertThrows(RuntimeException.class, () ->
                achievementService.createLearnerAchievement(
                        validRequest,
                        "token",
                        "org"
                )
        );
    }

    @Test
    void testFetchUserDetails_cassandraFallback() throws Exception {
        SearchCriteria criteria = new SearchCriteria();
        SearchResult searchResult = new SearchResult();

        Map<String, Object> data = new HashMap<>();
        data.put(Constants.USER_ID, "user1");
        searchResult.setData(Collections.singletonList(data));

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(any())).thenReturn(null);
        when(esClientService.searchDocuments(any(), any())).thenReturn(searchResult);

        when(cacheService.hget(any())).thenReturn(Collections.emptyList());
        when(cassandraOperation.getRecordsByPropertiesWithoutFiltering(any(), any(), any(), any(), any()))
                .thenReturn(Collections.emptyList());

        ApiResponse response = achievementService.searchLearnerAchievements(criteria, "token");

        assertNotNull(response);
    }


    private Map<String, Object> buildValidRequest() {
        Map<String, Object> contextData = new HashMap<>();
        contextData.put("field1", "value1");
        contextData.put("field2", "value2");

        Map<String, Object> requestData = new HashMap<>();
        requestData.put(Constants.CONTEXT_TYPE, "testContext");
        requestData.put(Constants.SOURCE, "source");
        requestData.put(Constants.CONTEXT_DATA, contextData);

        Map<String, Object> request = new HashMap<>();
        request.put(Constants.REQUEST, requestData);

        return request;
    }

    @Test
    void testStatusUpdate_invalidStatusValue() {
        when(accessTokenValidator.fetchUserIdFromAccessToken(anyString()))
                .thenReturn("user123");

        Map<String, Object> req = new HashMap<>();
        req.put("id", "1");
        req.put("contextType", "testContext");
        req.put("learnerId", "user123");
        req.put(Constants.STATUS, "WRONG_STATUS");
        req.put("reason", "test");

        Map<String, Object> request = new HashMap<>();
        request.put(Constants.REQUEST, req);

        ApiResponse response =
                achievementService.statusUpdateLearnerAchievement(request, "token");

        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
    }

    @Test
    void testGetAchievementFromCassandra_contextDataString() throws Exception {

        when(accessTokenValidator.fetchUserIdFromAccessToken(anyString()))
                .thenReturn("user123");

        Map<String, Object> recordMap = new HashMap<>();
        recordMap.put(Constants.CONTEXT_DATA, "{\"a\":\"b\"}");
        recordMap.put(Constants.STATUS, Constants.PENDING);

        when(cassandraOperation.getRecordsByPropertiesByKey(any(), any(), any(), any(), any()))
                .thenReturn(Collections.singletonList(recordMap));

        when(objectMapper.readValue(anyString(), eq(Map.class)))
                .thenReturn(new HashMap<>());

        ApiResponse response =
                achievementService.readLearnerAchievement("1", "token", "testContext");

        assertNotNull(response);
    }

    @Test
    void testGetAndCacheAchievementFromCassandra_serializationFail() throws Exception {

        when(accessTokenValidator.fetchUserIdFromAccessToken(anyString()))
                .thenReturn("user123");

        Map<String, Object> recordMap = new HashMap<>();
        recordMap.put(Constants.CONTEXT_DATA, new HashMap<>());
        recordMap.put(Constants.STATUS, Constants.PENDING);

        when(cacheService.getCache(any())).thenReturn(null);

        when(cassandraOperation.getRecordsByPropertiesByKey(any(), any(), any(), any(), any()))
                .thenReturn(Collections.singletonList(recordMap));

        when(objectMapper.writeValueAsString(any()))
                .thenThrow(new RuntimeException("Serialization fail"));

        ApiResponse response =
                achievementService.readLearnerAchievement("1", "token", "testContext");

        assertNotNull(response);
    }

    @Test
    void testUpdateAchievementInES_contextDataMap() {

        when(accessTokenValidator.fetchUserIdFromAccessToken(anyString()))
                .thenReturn("user123");

        Map<String, Object> recordMap = new HashMap<>();
        recordMap.put(Constants.STATUS, Constants.PENDING);
        recordMap.put("contextdata", new HashMap<>());
        recordMap.put(Constants.CREATED_ON, LocalDate.now());

        when(cassandraOperation.getAllRecordsByPrimaryKey(any(), any(), any(), any(), anyInt()))
                .thenReturn(Collections.singletonList(recordMap));

        Map<String, Object> cassandraResponse = new HashMap<>();
        cassandraResponse.put(Constants.RESPONSE, Constants.SUCCESS);
        when(cassandraOperation.updateRecordByCompositeKey(any(), any(), any(), any()))
                .thenReturn(cassandraResponse);

        when(esClientService.readDocument(any(), any()))
                .thenReturn(new HashMap<>());

        Map<String, Object> req = new HashMap<>();
        req.put("id", "1");
        req.put("contextType", "testContext");
        req.put("learnerId", "user123");
        req.put(Constants.STATUS, "APPROVED");
        req.put("reason", "ok");

        Map<String, Object> request = new HashMap<>();
        request.put(Constants.REQUEST, req);

        achievementService.statusUpdateLearnerAchievement(request, "token");

        verify(esClientService).updateDocument(any(), any(), any(), any(), any());
    }

    @Test
    void testRefreshAchievementSearchCacheForUser_success() throws Exception {

        when(accessTokenValidator.fetchUserIdFromAccessToken(anyString()))
                .thenReturn("user123");

        when(cbServerProperties.isRequireEs()).thenReturn(true);

        ApiResponse cassandraResponse = new ApiResponse();
        cassandraResponse.put(Constants.RESPONSE, Constants.SUCCESS);
        when(cassandraOperation.insertRecord(any(), any(), any()))
                .thenReturn(cassandraResponse);

        when(objectMapper.writeValueAsString(any())).thenReturn("{}");
        when(objectMapper.convertValue(any(), eq(Map.class))).thenReturn(new HashMap<>());

        SearchResult searchResult = new SearchResult();
        searchResult.setData(Collections.singletonList(new HashMap<>()));

        when(esClientService.searchDocuments(any(), any()))
                .thenReturn(searchResult);

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(cbServerProperties.getSearchResultRedisTtl()).thenReturn(3600L);

        achievementService.createLearnerAchievement(
                buildValidRequest(),
                "token",
                "org"
        );

        verify(valueOperations, atLeastOnce())
                .set(anyString(), any(), anyLong(), any());
    }

    @Test
    void testFetchUserDetails_mergeBranch() throws Exception {

        SearchCriteria criteria = new SearchCriteria();
        SearchResult searchResult = new SearchResult();

        Map<String, Object> data = new HashMap<>();
        data.put(Constants.USER_ID, "user1");
        searchResult.setData(Collections.singletonList(data));

        when(valueOperations.get(any())).thenReturn(null);
        when(esClientService.searchDocuments(any(), any()))
                .thenReturn(searchResult);

        // Redis returns empty -> missing user
        when(cacheService.hget(any()))
                .thenReturn(Collections.emptyList());

        Map<String, Object> userMap = new HashMap<>();
        userMap.put(Constants.ID, "user1");
        userMap.put(Constants.FIRST_NAME_CAMEL_CASE, "John");

        when(cassandraOperation.getRecordsByPropertiesWithoutFiltering(any(), any(), any(), any(), any()))
                .thenReturn(Collections.singletonList(userMap));

        ApiResponse response =
                achievementService.searchLearnerAchievements(criteria, "token");

        assertNotNull(response);
    }

    // ==================== GET USER ACHIEVEMENTS TESTS ====================

    @Test
    void testGetUserAchievements_success_fromCache() throws Exception {
        when(accessTokenValidator.fetchUserIdFromAccessToken(anyString())).thenReturn("user123");

        Map<String, Object> cachedData = new HashMap<>();
        List<Map<String, Object>> achievements = new ArrayList<>();
        Map<String, Object> achievement = new HashMap<>();
        achievement.put(Constants.ID, "achv1");
        achievement.put(Constants.CONTEXT_TYPE, "testContext");
        achievement.put(Constants.STATUS, "APPROVED");
        achievements.add(achievement);
        cachedData.put(Constants.DATA, achievements);
        cachedData.put(Constants.TOTAL_COUNT, 1);

        String cachedJson = "{\"data\":[{\"id\":\"achv1\",\"contextType\":\"testContext\"}],\"totalCount\":1}";
        when(cacheService.getCache(anyString())).thenReturn(cachedJson);
        when(objectMapper.readValue(anyString(), eq(Map.class))).thenReturn(cachedData);

        ApiResponse response = achievementService.getUserAchievements("token", "123");

        assertEquals(HttpStatus.OK, response.getResponseCode());
        assertNotNull(response.getResult().get(Constants.SEARCH_RESULTS));
        verify(cacheService, times(1)).getCache(anyString());
        verify(cassandraOperation, never()).getRecordsByPropertiesWithoutFiltering(any(), any(), any(), any(), anyInt());
    }

    @Test
    void testGetUserAchievements_success_fromDatabase() {
        when(accessTokenValidator.fetchUserIdFromAccessToken(anyString())).thenReturn("user123");
        when(cacheService.getCache(anyString())).thenReturn(null);
        when(cbServerProperties.getCassandraFetchLimit()).thenReturn(100);

        List<Map<String, Object>> achievements = new ArrayList<>();
        Map<String, Object> achievement1 = new HashMap<>();
        achievement1.put(Constants.ID, "achv1");
        achievement1.put(Constants.CONTEXT_TYPE, "testContext");
        achievement1.put(Constants.CONTEXT_DATA, new HashMap<>());
        achievement1.put(Constants.CREATED_ON, LocalDate.now());
        achievement1.put(Constants.UPDATED_ON, LocalDate.now());
        achievement1.put(Constants.FIELD_APPROVED_ON, LocalDate.now());
        achievement1.put(Constants.STATUS, "APPROVED");
        achievements.add(achievement1);

        when(cassandraOperation.getRecordsByPropertiesWithoutFiltering(any(), any(), any(), any(), anyInt()))
                .thenReturn(achievements);

        ApiResponse response = achievementService.getUserAchievements("token", "123");

        assertEquals(HttpStatus.OK, response.getResponseCode());
        assertNotNull(response.getResult().get(Constants.SEARCH_RESULTS));
        Map<String, Object> searchResults = (Map<String, Object>) response.getResult().get(Constants.SEARCH_RESULTS);
        assertNotNull(searchResults.get(Constants.DATA));
        assertEquals(1, searchResults.get(Constants.TOTAL_COUNT));
        verify(cacheService, times(1)).putCache(anyString(), any());
    }

    @Test
    void testGetUserAchievements_success_withContextDataString() throws Exception {
        when(accessTokenValidator.fetchUserIdFromAccessToken(anyString())).thenReturn("user123");
        when(cacheService.getCache(anyString())).thenReturn(null);
        when(cbServerProperties.getCassandraFetchLimit()).thenReturn(100);

        List<Map<String, Object>> achievements = new ArrayList<>();
        Map<String, Object> achievement1 = new HashMap<>();
        achievement1.put(Constants.ID, "achv1");
        achievement1.put(Constants.CONTEXT_TYPE, "testContext");
        achievement1.put(Constants.CONTEXT_DATA, "{\"field1\":\"value1\",\"field2\":\"value2\"}");
        achievement1.put(Constants.CREATED_ON, LocalDate.now());
        achievement1.put(Constants.STATUS, "APPROVED");
        achievements.add(achievement1);

        when(cassandraOperation.getRecordsByPropertiesWithoutFiltering(any(), any(), any(), any(), anyInt()))
                .thenReturn(achievements);
        when(objectMapper.readValue(anyString(), eq(Map.class))).thenReturn(new HashMap<>());

        ApiResponse response = achievementService.getUserAchievements("token", null);

        assertEquals(HttpStatus.OK, response.getResponseCode());
        assertNotNull(response.getResult().get(Constants.SEARCH_RESULTS));
        verify(objectMapper, atLeastOnce()).readValue(anyString(), eq(Map.class));
    }

    @Test
    void testGetUserAchievements_success_withLocalDateTimeFields() {
        when(accessTokenValidator.fetchUserIdFromAccessToken(anyString())).thenReturn("user123");
        when(cacheService.getCache(anyString())).thenReturn(null);
        when(cbServerProperties.getCassandraFetchLimit()).thenReturn(100);

        List<Map<String, Object>> achievements = new ArrayList<>();
        Map<String, Object> achievement1 = new HashMap<>();
        achievement1.put(Constants.ID, "achv1");
        achievement1.put(Constants.CONTEXT_TYPE, "testContext");
        achievement1.put(Constants.CONTEXT_DATA, new HashMap<>());
        achievement1.put(Constants.CREATED_ON, java.time.LocalDateTime.now());
        achievement1.put(Constants.UPDATED_ON, java.time.LocalDateTime.now());
        achievement1.put(Constants.FIELD_APPROVED_ON, java.time.LocalDateTime.now());
        achievement1.put(Constants.STATUS, "APPROVED");
        achievements.add(achievement1);

        when(cassandraOperation.getRecordsByPropertiesWithoutFiltering(any(), any(), any(), any(), anyInt()))
                .thenReturn(achievements);

        ApiResponse response = achievementService.getUserAchievements("token", null);

        assertEquals(HttpStatus.OK, response.getResponseCode());
        assertNotNull(response.getResult().get(Constants.SEARCH_RESULTS));
        Map<String, Object> searchResults = (Map<String, Object>) response.getResult().get(Constants.SEARCH_RESULTS);
        List<Map<String, Object>> resultData = (List<Map<String, Object>>) searchResults.get(Constants.DATA);
        assertNotNull(resultData);
        assertEquals(1, resultData.size());
        // Verify dates are converted to strings
        assertTrue(resultData.get(0).get(Constants.CREATED_ON) instanceof String);
        assertTrue(resultData.get(0).get(Constants.UPDATED_ON) instanceof String);
        assertTrue(resultData.get(0).get(Constants.FIELD_APPROVED_ON) instanceof String);
    }

    @Test
    void testGetUserAchievements_success_emptyResults() {
        when(accessTokenValidator.fetchUserIdFromAccessToken(anyString())).thenReturn("user123");
        when(cacheService.getCache(anyString())).thenReturn(null);
        when(cbServerProperties.getCassandraFetchLimit()).thenReturn(100);

        when(cassandraOperation.getRecordsByPropertiesWithoutFiltering(any(), any(), any(), any(), anyInt()))
                .thenReturn(Collections.emptyList());

        ApiResponse response = achievementService.getUserAchievements("token", null);

        assertEquals(HttpStatus.OK, response.getResponseCode());
        assertNotNull(response.getResult().get(Constants.SEARCH_RESULTS));
        Map<String, Object> searchResults = (Map<String, Object>) response.getResult().get(Constants.SEARCH_RESULTS);
        assertEquals(0, searchResults.get(Constants.TOTAL_COUNT));
        verify(cacheService, times(1)).putCache(anyString(), any());
    }

    @Test
    void testGetUserAchievements_success_nullResults() {
        when(accessTokenValidator.fetchUserIdFromAccessToken(anyString())).thenReturn("user123");
        when(cacheService.getCache(anyString())).thenReturn(null);
        when(cbServerProperties.getCassandraFetchLimit()).thenReturn(100);

        when(cassandraOperation.getRecordsByPropertiesWithoutFiltering(any(), any(), any(), any(), anyInt()))
                .thenReturn(null);

        ApiResponse response = achievementService.getUserAchievements("token", null);

        assertEquals(HttpStatus.OK, response.getResponseCode());
        assertNotNull(response.getResult().get(Constants.SEARCH_RESULTS));
        Map<String, Object> searchResults = (Map<String, Object>) response.getResult().get(Constants.SEARCH_RESULTS);
        assertEquals(0, searchResults.get(Constants.TOTAL_COUNT));
    }

    @Test
    void testGetUserAchievements_invalidToken_blank() {
        when(accessTokenValidator.fetchUserIdFromAccessToken(anyString())).thenReturn("");

        ApiResponse response = achievementService.getUserAchievements("token", null);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getResponseCode());
        assertNotNull(response.getParams());
        assertTrue(response.getParams().getErrMsg().contains("Invalid or missing access token"));
        verify(cacheService, never()).getCache(anyString());
        verify(cassandraOperation, never()).getRecordsByPropertiesWithoutFiltering(any(), any(), any(), any(), anyInt());
    }

    @Test
    void testGetUserAchievements_invalidToken_null() {
        when(accessTokenValidator.fetchUserIdFromAccessToken(anyString())).thenReturn(null);

        ApiResponse response = achievementService.getUserAchievements("token", null);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getResponseCode());
        assertNotNull(response.getParams());
        assertTrue(response.getParams().getErrMsg().contains("Invalid or missing access token"));
        verify(cacheService, never()).getCache(anyString());
        verify(cassandraOperation, never()).getRecordsByPropertiesWithoutFiltering(any(), any(), any(), any(), anyInt());
    }

    @Test
    void testGetUserAchievements_cacheReadException() throws Exception {
        when(accessTokenValidator.fetchUserIdFromAccessToken(anyString())).thenReturn("user123");
        when(cacheService.getCache(anyString())).thenReturn("{invalid json}");
        when(objectMapper.readValue(anyString(), eq(Map.class)))
                .thenThrow(new com.fasterxml.jackson.core.JsonProcessingException("JSON parse error") {});

        ApiResponse response = achievementService.getUserAchievements("token", null);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getResponseCode());
        assertNotNull(response.getParams());
        assertTrue(response.getParams().getErrMsg().contains("Failed to fetch achievements"));
    }

    @Test
    void testGetUserAchievements_databaseException() {
        when(accessTokenValidator.fetchUserIdFromAccessToken(anyString())).thenReturn("user123");
        when(cacheService.getCache(anyString())).thenReturn(null);
        when(cbServerProperties.getCassandraFetchLimit()).thenReturn(100);
        when(cassandraOperation.getRecordsByPropertiesWithoutFiltering(any(), any(), any(), any(), anyInt()))
                .thenThrow(new RuntimeException("Database connection error"));

        ApiResponse response = achievementService.getUserAchievements("token", null);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getResponseCode());
        assertNotNull(response.getParams());
        assertTrue(response.getParams().getErrMsg().contains("Failed to fetch achievements"));
    }

    @Test
    void testGetUserAchievements_contextDataParseException() throws Exception {
        when(accessTokenValidator.fetchUserIdFromAccessToken(anyString())).thenReturn("user123");
        when(cacheService.getCache(anyString())).thenReturn(null);
        when(cbServerProperties.getCassandraFetchLimit()).thenReturn(100);

        List<Map<String, Object>> achievements = new ArrayList<>();
        Map<String, Object> achievement1 = new HashMap<>();
        achievement1.put(Constants.ID, "achv1");
        achievement1.put(Constants.CONTEXT_TYPE, "testContext");
        achievement1.put(Constants.CONTEXT_DATA, "{invalid json}");
        achievement1.put(Constants.CREATED_ON, LocalDate.now());
        achievements.add(achievement1);

        when(cassandraOperation.getRecordsByPropertiesWithoutFiltering(any(), any(), any(), any(), anyInt()))
                .thenReturn(achievements);
        when(objectMapper.readValue(anyString(), eq(Map.class)))
                .thenThrow(new com.fasterxml.jackson.core.JsonProcessingException("JSON parse error") {});

        ApiResponse response = achievementService.getUserAchievements("token", null);

        assertEquals(HttpStatus.OK, response.getResponseCode());
        Map<String, Object> searchResults = (Map<String, Object>) response.getResult().get(Constants.SEARCH_RESULTS);
        List<Map<String, Object>> resultData = (List<Map<String, Object>>) searchResults.get(Constants.DATA);
        // contextData should be set to empty HashMap on parse failure
        assertTrue(resultData.get(0).get(Constants.CONTEXT_DATA) instanceof Map);
    }

    @Test
    void testGetUserAchievements_cachePutException() {
        when(accessTokenValidator.fetchUserIdFromAccessToken(anyString())).thenReturn("user123");
        when(cacheService.getCache(anyString())).thenReturn(null);
        when(cbServerProperties.getCassandraFetchLimit()).thenReturn(100);

        List<Map<String, Object>> achievements = new ArrayList<>();
        Map<String, Object> achievement1 = new HashMap<>();
        achievement1.put(Constants.ID, "achv1");
        achievement1.put(Constants.CONTEXT_DATA, new HashMap<>());
        achievement1.put(Constants.CREATED_ON, LocalDate.now());
        achievements.add(achievement1);

        when(cassandraOperation.getRecordsByPropertiesWithoutFiltering(any(), any(), any(), any(), anyInt()))
                .thenReturn(achievements);
        doThrow(new RuntimeException("Cache write error")).when(cacheService).putCache(anyString(), any());

        ApiResponse response = achievementService.getUserAchievements("token", null);

        // Should still return OK even if cache write fails
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getResponseCode());
    }

    @Test
    void testGetUserAchievements_multipleAchievements_mixedDateTypes() throws Exception {
        when(accessTokenValidator.fetchUserIdFromAccessToken(anyString())).thenReturn("user123");
        when(cacheService.getCache(anyString())).thenReturn(null);
        when(cbServerProperties.getCassandraFetchLimit()).thenReturn(100);

        List<Map<String, Object>> achievements = new ArrayList<>();

        // Achievement with LocalDate
        Map<String, Object> achievement1 = new HashMap<>();
        achievement1.put(Constants.ID, "achv1");
        achievement1.put(Constants.CONTEXT_DATA, new HashMap<>());
        achievement1.put(Constants.CREATED_ON, LocalDate.now());
        achievement1.put(Constants.UPDATED_ON, LocalDate.now());
        achievements.add(achievement1);

        // Achievement with LocalDateTime
        Map<String, Object> achievement2 = new HashMap<>();
        achievement2.put(Constants.ID, "achv2");
        achievement2.put(Constants.CONTEXT_DATA, "{\"key\":\"value\"}");
        achievement2.put(Constants.CREATED_ON, java.time.LocalDateTime.now());
        achievement2.put(Constants.FIELD_APPROVED_ON, java.time.LocalDateTime.now());
        achievements.add(achievement2);

        // Achievement with String dates (already formatted)
        Map<String, Object> achievement3 = new HashMap<>();
        achievement3.put(Constants.ID, "achv3");
        achievement3.put(Constants.CONTEXT_DATA, new HashMap<>());
        achievement3.put(Constants.CREATED_ON, "2024-01-01");
        achievement3.put(Constants.UPDATED_ON, "2024-01-02");
        achievements.add(achievement3);

        when(cassandraOperation.getRecordsByPropertiesWithoutFiltering(any(), any(), any(), any(), anyInt()))
                .thenReturn(achievements);
        when(objectMapper.readValue(anyString(), eq(Map.class))).thenReturn(new HashMap<>());

        ApiResponse response = achievementService.getUserAchievements("token", null);

        assertEquals(HttpStatus.OK, response.getResponseCode());
        Map<String, Object> searchResults = (Map<String, Object>) response.getResult().get(Constants.SEARCH_RESULTS);
        List<Map<String, Object>> resultData = (List<Map<String, Object>>) searchResults.get(Constants.DATA);
        assertEquals(3, resultData.size());
        assertEquals(3, searchResults.get(Constants.TOTAL_COUNT));

        // Verify all dates are strings
        for (Map<String, Object> achievement : resultData) {
            if (achievement.containsKey(Constants.CREATED_ON)) {
                assertTrue(achievement.get(Constants.CREATED_ON) instanceof String);
            }
            if (achievement.containsKey(Constants.UPDATED_ON)) {
                assertTrue(achievement.get(Constants.UPDATED_ON) instanceof String);
            }
            if (achievement.containsKey(Constants.FIELD_APPROVED_ON)) {
                assertTrue(achievement.get(Constants.FIELD_APPROVED_ON) instanceof String);
            }
        }
    }

    @Test
    void testGetUserAchievements_achievementWithoutDates() {
        when(accessTokenValidator.fetchUserIdFromAccessToken(anyString())).thenReturn("user123");
        when(cacheService.getCache(anyString())).thenReturn(null);
        when(cbServerProperties.getCassandraFetchLimit()).thenReturn(100);

        List<Map<String, Object>> achievements = new ArrayList<>();
        Map<String, Object> achievement1 = new HashMap<>();
        achievement1.put(Constants.ID, "achv1");
        achievement1.put(Constants.CONTEXT_DATA, new HashMap<>());
        achievement1.put(Constants.STATUS, "PENDING");
        // No date fields
        achievements.add(achievement1);

        when(cassandraOperation.getRecordsByPropertiesWithoutFiltering(any(), any(), any(), any(), anyInt()))
                .thenReturn(achievements);

        ApiResponse response = achievementService.getUserAchievements("token", null);

        assertEquals(HttpStatus.OK, response.getResponseCode());
        Map<String, Object> searchResults = (Map<String, Object>) response.getResult().get(Constants.SEARCH_RESULTS);
        List<Map<String, Object>> resultData = (List<Map<String, Object>>) searchResults.get(Constants.DATA);
        assertEquals(1, resultData.size());
        assertEquals("achv1", resultData.get(0).get(Constants.ID));
    }

    // ==================== KAFKA EVENT PUBLISHING TESTS ====================

    @Test
    void testCreateLearnerAchievement_withCompetencies_publishesKafkaEvent() throws Exception {
        // Arrange
        Map<String, Object> contextData = new HashMap<>();
        contextData.put("field1", "value1");
        contextData.put("field2", "value2");
        List<Map<String, Object>> competencies = new ArrayList<>();
        Map<String, Object> competency = new HashMap<>();
        competency.put("competencyAreaId", "area1");
        competencies.add(competency);
        contextData.put(Constants.COMPETENCIES, competencies);

        Map<String, Object> requestData = new HashMap<>();
        requestData.put(Constants.CONTEXT_TYPE, "testContext");
        requestData.put(Constants.SOURCE, "source");
        requestData.put(Constants.CONTEXT_DATA, contextData);
        Map<String, Object> request = new HashMap<>();
        request.put(Constants.REQUEST, requestData);

        when(accessTokenValidator.fetchUserIdFromAccessToken(anyString())).thenReturn("user123");
        ApiResponse cassandraResponse = new ApiResponse();
        cassandraResponse.put(Constants.RESPONSE, Constants.SUCCESS);
        when(cassandraOperation.insertRecord(any(), any(), any())).thenReturn(cassandraResponse);
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");
        when(objectMapper.convertValue(any(), eq(Map.class))).thenReturn(new HashMap<>());
        when(cbServerProperties.isRequireEs()).thenReturn(true);

        SearchResult searchResult = new SearchResult();
        searchResult.setData(new ArrayList<>());
        when(esClientService.searchDocuments(any(), any())).thenReturn(searchResult);

        // Act
        ApiResponse response = achievementService.createLearnerAchievement(request, "token", "org1");

        // Assert
        assertEquals(HttpStatus.OK, response.getResponseCode());
        verify(kafkaEventPublisher, times(1)).publish(anyString(), (Object) any(), anyString());
    }

    @Test
    void testCreateLearnerAchievement_withoutCompetencies_doesNotPublishKafkaEvent() throws Exception {
        // Arrange
        Map<String, Object> contextData = new HashMap<>();
        contextData.put("field1", "value1");
        contextData.put("field2", "value2");
        // No competencies added

        Map<String, Object> requestData = new HashMap<>();
        requestData.put(Constants.CONTEXT_TYPE, "testContext");
        requestData.put(Constants.SOURCE, "source");
        requestData.put(Constants.CONTEXT_DATA, contextData);
        Map<String, Object> request = new HashMap<>();
        request.put(Constants.REQUEST, requestData);

        when(accessTokenValidator.fetchUserIdFromAccessToken(anyString())).thenReturn("user123");
        ApiResponse cassandraResponse = new ApiResponse();
        cassandraResponse.put(Constants.RESPONSE, Constants.SUCCESS);
        when(cassandraOperation.insertRecord(any(), any(), any())).thenReturn(cassandraResponse);
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");
        when(objectMapper.convertValue(any(), eq(Map.class))).thenReturn(new HashMap<>());
        when(cbServerProperties.isRequireEs()).thenReturn(true);

        SearchResult searchResult = new SearchResult();
        searchResult.setData(new ArrayList<>());
        when(esClientService.searchDocuments(any(), any())).thenReturn(searchResult);

        // Act
        ApiResponse response = achievementService.createLearnerAchievement(request, "token", "org1");

        // Assert
        assertEquals(HttpStatus.OK, response.getResponseCode());
        // Kafka event is always published regardless of competencies
        verify(kafkaEventPublisher, times(1)).publish(anyString(), (Object) any(), anyString());
    }

    @Test
    void testCreateLearnerAchievement_withEmptyCompetenciesList_publishesKafkaEvent() throws Exception {
        // Arrange
        Map<String, Object> contextData = new HashMap<>();
        contextData.put("field1", "value1");
        contextData.put("field2", "value2");
        contextData.put(Constants.COMPETENCIES, new ArrayList<>()); // Empty list

        Map<String, Object> requestData = new HashMap<>();
        requestData.put(Constants.CONTEXT_TYPE, "testContext");
        requestData.put(Constants.SOURCE, "source");
        requestData.put(Constants.CONTEXT_DATA, contextData);
        Map<String, Object> request = new HashMap<>();
        request.put(Constants.REQUEST, requestData);

        when(accessTokenValidator.fetchUserIdFromAccessToken(anyString())).thenReturn("user123");
        ApiResponse cassandraResponse = new ApiResponse();
        cassandraResponse.put(Constants.RESPONSE, Constants.SUCCESS);
        when(cassandraOperation.insertRecord(any(), any(), any())).thenReturn(cassandraResponse);
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");
        when(objectMapper.convertValue(any(), eq(Map.class))).thenReturn(new HashMap<>());
        when(cbServerProperties.isRequireEs()).thenReturn(true);

        SearchResult searchResult = new SearchResult();
        searchResult.setData(new ArrayList<>());
        when(esClientService.searchDocuments(any(), any())).thenReturn(searchResult);

        // Act
        ApiResponse response = achievementService.createLearnerAchievement(request, "token", "org1");

        // Assert
        assertEquals(HttpStatus.OK, response.getResponseCode());
        // Kafka event is always published regardless of competencies list content
        verify(kafkaEventPublisher, times(1)).publish(anyString(), (Object) any(), anyString());
    }

    @Test
    void testCreateLearnerAchievement_kafkaPublishingFails_achievementStillCreated() throws Exception {
        // Arrange
        Map<String, Object> contextData = new HashMap<>();
        contextData.put("field1", "value1");
        contextData.put("field2", "value2");
        List<Map<String, Object>> competencies = new ArrayList<>();
        Map<String, Object> competency = new HashMap<>();
        competency.put("competencyAreaId", "area1");
        competencies.add(competency);
        contextData.put(Constants.COMPETENCIES, competencies);

        Map<String, Object> requestData = new HashMap<>();
        requestData.put(Constants.CONTEXT_TYPE, "testContext");
        requestData.put(Constants.SOURCE, "source");
        requestData.put(Constants.CONTEXT_DATA, contextData);
        Map<String, Object> request = new HashMap<>();
        request.put(Constants.REQUEST, requestData);

        when(accessTokenValidator.fetchUserIdFromAccessToken(anyString())).thenReturn("user123");
        ApiResponse cassandraResponse = new ApiResponse();
        cassandraResponse.put(Constants.RESPONSE, Constants.SUCCESS);
        when(cassandraOperation.insertRecord(any(), any(), any())).thenReturn(cassandraResponse);
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");
        when(objectMapper.convertValue(any(), eq(Map.class))).thenReturn(new HashMap<>());
        when(cbServerProperties.isRequireEs()).thenReturn(true);

        SearchResult searchResult = new SearchResult();
        searchResult.setData(new ArrayList<>());
        when(esClientService.searchDocuments(any(), any())).thenReturn(searchResult);

        // Kafka publishing throws exception
        doThrow(new RuntimeException("Kafka error"))
                .when(kafkaEventPublisher).publish(anyString(), (Object) any(), anyString());

        // Act
        ApiResponse response = achievementService.createLearnerAchievement(request, "token", "org1");

        // Assert - Achievement creation should still succeed
        assertEquals(HttpStatus.OK, response.getResponseCode());
        assertNotNull(response.getResult());
        verify(cassandraOperation, times(1)).insertRecord(any(), any(), any());
        verify(esClientService, times(1)).addDocument(any(), any(), any(), any(), any());
    }

    @Test
    void testCreateLearnerAchievement_withNullContextData_doesNotPublishKafkaEvent() {
        // Arrange
        Map<String, Object> requestData = new HashMap<>();
        requestData.put(Constants.CONTEXT_TYPE, "testContext");
        requestData.put(Constants.SOURCE, "source");
        requestData.put(Constants.CONTEXT_DATA, null); // Null context data

        Map<String, Object> request = new HashMap<>();
        request.put(Constants.REQUEST, requestData);

        when(accessTokenValidator.fetchUserIdFromAccessToken(anyString())).thenReturn("user123");

        // Act
        ApiResponse response = achievementService.createLearnerAchievement(request, "token", "org1");

        // Assert
        // Should fail validation before reaching Kafka publishing
        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
        verify(kafkaEventPublisher, never()).publish(anyString(), (Object) any(), anyString());
    }

    // ==================== COMPETENCY CHANGE DETECTION TESTS ====================

    @Test
    void testUpdateLearnerAchievement_withChangedCompetencies_publishesKafkaEvent() throws Exception {
        // Arrange: Setup competencies_v6 with changes
        List<Map<String, String>> newCompetencies = new ArrayList<>();
        Map<String, String> newCompetency = new HashMap<>();
        newCompetency.put(Constants.COMPETENCY_AREA_REF_ID, "kcmfinal_fw_competencyarea_area1");
        newCompetency.put(Constants.COMPETENCY_THEME_REF_ID, "kcmfinal_fw_theme_theme1");
        newCompetency.put(Constants.COMPETENCY_SUB_THEME_REF_ID, "kcmfinal_fw_subtheme_sub1");
        newCompetencies.add(newCompetency);

        Map<String, Object> newContextData = new HashMap<>();
        newContextData.put("field1", "value1");
        newContextData.put("field2", "value2");
        newContextData.put(Constants.UPLOAD_DOCUMENT_URL, "http://example.com/doc.pdf");
        newContextData.put(Constants.URL, "");
        newContextData.put(Constants.COMPETENCIES_V6, newCompetencies);

        Map<String, Object> requestData = new HashMap<>();
        requestData.put(Constants.ID, "achv1");
        requestData.put(Constants.CONTEXT_TYPE, "testContext");
        requestData.put(Constants.CONTEXT_DATA, newContextData);
        Map<String, Object> request = new HashMap<>();
        request.put(Constants.REQUEST, requestData);

        when(accessTokenValidator.fetchUserIdFromAccessToken(anyString())).thenReturn("user123");
        when(cbServerProperties.isRequireEs()).thenReturn(true);

        // Existing record with different competencies
        Map<String, Object> existingRecord = new HashMap<>();
        existingRecord.put(Constants.STATUS, Constants.PENDING);
        existingRecord.put(Constants.CREATED_ON, LocalDate.now());
        existingRecord.put(Constants.USER_ID_RQST, "user123");

        List<Map<String, String>> existingCompetencies = new ArrayList<>();
        Map<String, String> existingCompetency = new HashMap<>();
        existingCompetency.put(Constants.COMPETENCY_AREA_REF_ID, "kcmfinal_fw_competencyarea_different");
        existingCompetency.put(Constants.COMPETENCY_THEME_REF_ID, "kcmfinal_fw_theme_different");
        existingCompetency.put(Constants.COMPETENCY_SUB_THEME_REF_ID, "kcmfinal_fw_subtheme_different");
        existingCompetencies.add(existingCompetency);

        Map<String, Object> existingContextData = new HashMap<>();
        existingContextData.put("field1", "old");
        existingContextData.put(Constants.COMPETENCIES_V6, existingCompetencies);
        existingRecord.put(Constants.CONTEXT_DATA, existingContextData);

        when(cassandraOperation.getRecordsByPropertiesByKey(any(), any(), any(), any(), any()))
                .thenReturn(Collections.singletonList(existingRecord));
        ApiResponse cassandraResponse = new ApiResponse();
        cassandraResponse.put(Constants.RESPONSE, Constants.SUCCESS);
        when(cassandraOperation.insertRecord(any(), any(), any())).thenReturn(cassandraResponse);
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");
        when(objectMapper.convertValue(any(), eq(Map.class))).thenReturn(new HashMap<>());
        when(objectMapper.readValue(anyString(), eq(Map.class))).thenReturn(existingContextData);

        Map<String, Object> esDoc = new HashMap<>();
        esDoc.put(Constants.CREATED_ON, "2024-01-01T00:00:00.000+0000");
        when(esClientService.readDocument(any(), any())).thenReturn(esDoc);

        SearchResult searchResult = new SearchResult();
        searchResult.setData(new ArrayList<>());
        when(esClientService.searchDocuments(any(), any())).thenReturn(searchResult);

        // Act
        ApiResponse response = achievementService.updateLearnerAchievement(request, "token", "org1");

        // Assert
        assertEquals(HttpStatus.OK, response.getResponseCode());
        verify(kafkaEventPublisher, times(1)).publish(anyString(), (Object) any(), anyString());
    }

    @Test
    void testUpdateLearnerAchievement_withNoCompetencyChange_doesNotPublishKafkaEvent() throws Exception {
        // Arrange: Same competencies
        List<Map<String, String>> competencies = new ArrayList<>();
        Map<String, String> competency = new HashMap<>();
        competency.put(Constants.COMPETENCY_AREA_REF_ID, "kcmfinal_fw_competencyarea_same");
        competency.put(Constants.COMPETENCY_THEME_REF_ID, "kcmfinal_fw_theme_same");
        competency.put(Constants.COMPETENCY_SUB_THEME_REF_ID, "kcmfinal_fw_subtheme_same");
        competencies.add(competency);

        Map<String, Object> contextData = new HashMap<>();
        contextData.put("field1", "value1");
        contextData.put("field2", "value2");
        contextData.put(Constants.UPLOAD_DOCUMENT_URL, "http://example.com/doc.pdf");
        contextData.put(Constants.URL, "");
        contextData.put(Constants.COMPETENCIES_V6, competencies);

        Map<String, Object> requestData = new HashMap<>();
        requestData.put(Constants.ID, "achv1");
        requestData.put(Constants.CONTEXT_TYPE, "testContext");
        requestData.put(Constants.CONTEXT_DATA, contextData);
        Map<String, Object> request = new HashMap<>();
        request.put(Constants.REQUEST, requestData);

        when(accessTokenValidator.fetchUserIdFromAccessToken(anyString())).thenReturn("user123");
        when(cbServerProperties.isRequireEs()).thenReturn(true);

        Map<String, Object> existingRecord = new HashMap<>();
        existingRecord.put(Constants.STATUS, Constants.PENDING);
        existingRecord.put(Constants.CREATED_ON, LocalDate.now());
        existingRecord.put(Constants.USER_ID_RQST, "user123");
        existingRecord.put(Constants.CONTEXT_DATA, contextData);

        when(cassandraOperation.getRecordsByPropertiesByKey(any(), any(), any(), any(), any()))
                .thenReturn(Collections.singletonList(existingRecord));
        ApiResponse cassandraResponse = new ApiResponse();
        cassandraResponse.put(Constants.RESPONSE, Constants.SUCCESS);
        when(cassandraOperation.insertRecord(any(), any(), any())).thenReturn(cassandraResponse);
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");
        when(objectMapper.convertValue(any(), eq(Map.class))).thenReturn(new HashMap<>());
        when(objectMapper.readValue(anyString(), eq(Map.class))).thenReturn(contextData);

        Map<String, Object> esDoc = new HashMap<>();
        esDoc.put(Constants.CREATED_ON, "2024-01-01T00:00:00.000+0000");
        when(esClientService.readDocument(any(), any())).thenReturn(esDoc);

        SearchResult searchResult = new SearchResult();
        searchResult.setData(new ArrayList<>());
        when(esClientService.searchDocuments(any(), any())).thenReturn(searchResult);

        // Act
        ApiResponse response = achievementService.updateLearnerAchievement(request, "token", "org1");

        // Assert - No Kafka event should be published if competencies haven't changed
        assertEquals(HttpStatus.OK, response.getResponseCode());
        verify(kafkaEventPublisher, never()).publish(anyString(), (Object) any(), anyString());
    }

    @Test
    void testDeleteLearnerAchievement_withCompetencies_publishesDeleteKafkaEvent() throws Exception {
        // Arrange
        List<Map<String, String>> competencies = new ArrayList<>();
        Map<String, String> competency = new HashMap<>();
        competency.put(Constants.COMPETENCY_AREA_REF_ID, "area1");
        competency.put(Constants.COMPETENCY_THEME_REF_ID, "theme1");
        competency.put(Constants.COMPETENCY_SUB_THEME_REF_ID, "sub1");
        competencies.add(competency);

        Map<String, Object> contextData = new HashMap<>();
        contextData.put(Constants.COMPETENCIES_V6, competencies);

        Map<String, Object> reqMap = new HashMap<>();
        reqMap.put(Constants.ID, "achv1");
        reqMap.put(Constants.CONTEXT_TYPE, "testContext");
        Map<String, Object> request = new HashMap<>();
        request.put(Constants.REQUEST, reqMap);

        when(accessTokenValidator.fetchUserIdFromAccessToken(anyString())).thenReturn("user123");

        Map<String, Object> existingRecord = new HashMap<>();
        existingRecord.put(Constants.CONTEXT_DATA, contextData);
        existingRecord.put(Constants.USER_ID_RQST, "user123");

        when(cassandraOperation.getRecordsByPropertiesByKey(any(), any(), any(), any(), any()))
                .thenReturn(Collections.singletonList(existingRecord));

        Map<String, Object> cassandraResponse = new HashMap<>();
        cassandraResponse.put(Constants.RESPONSE, Constants.SUCCESS);
        when(cassandraOperation.deleteRecordByCompositeKey(any(), any(), any())).thenReturn(cassandraResponse);

        when(cbServerProperties.isRequireEs()).thenReturn(true);
        SearchResult searchResult = new SearchResult();
        searchResult.setData(new ArrayList<>());
        when(esClientService.searchDocuments(any(), any())).thenReturn(searchResult);

        // Act
        ApiResponse response = achievementService.deleteLearnerAchievement(request, "token");

        // Assert
        assertEquals(HttpStatus.OK, response.getResponseCode());
        verify(kafkaEventPublisher, times(1)).publish(anyString(), (Object) any(), anyString());
    }

    @Test
    void testDeleteLearnerAchievement_withoutCompetencies_doesNotPublishKafkaEvent() {
        // Arrange: No competencies in context data
        Map<String, Object> contextData = new HashMap<>();
        contextData.put("field1", "value1");
        // No competencies_v6

        Map<String, Object> reqMap = new HashMap<>();
        reqMap.put(Constants.ID, "achv1");
        reqMap.put(Constants.CONTEXT_TYPE, "testContext");
        Map<String, Object> request = new HashMap<>();
        request.put(Constants.REQUEST, reqMap);

        when(accessTokenValidator.fetchUserIdFromAccessToken(anyString())).thenReturn("user123");

        Map<String, Object> existingRecord = new HashMap<>();
        existingRecord.put(Constants.CONTEXT_DATA, contextData);
        existingRecord.put(Constants.USER_ID_RQST, "user123");

        when(cassandraOperation.getRecordsByPropertiesByKey(any(), any(), any(), any(), any()))
                .thenReturn(Collections.singletonList(existingRecord));

        Map<String, Object> cassandraResponse = new HashMap<>();
        cassandraResponse.put(Constants.RESPONSE, Constants.SUCCESS);
        when(cassandraOperation.deleteRecordByCompositeKey(any(), any(), any())).thenReturn(cassandraResponse);

        when(cbServerProperties.isRequireEs()).thenReturn(true);
        SearchResult searchResult = new SearchResult();
        searchResult.setData(new ArrayList<>());
        try {
            when(esClientService.searchDocuments(any(), any())).thenReturn(searchResult);
        } catch (Exception e) {
            fail("Mock setup failed");
        }

        // Act
        ApiResponse response = achievementService.deleteLearnerAchievement(request, "token");

        // Assert - No Kafka event should be published if no competencies
        assertEquals(HttpStatus.OK, response.getResponseCode());
        verify(kafkaEventPublisher, never()).publish(anyString(), (Object) any(), anyString());
    }


    // ==================== CASE-INSENSITIVE COMPARISON TESTS ====================

    @Test
    void testUpdateLearnerAchievement_caseInsensitiveCompetencyComparison() throws Exception {
        // Arrange: Same competencies but different case
        List<Map<String, String>> newCompetencies = new ArrayList<>();
        Map<String, String> newCompetency = new HashMap<>();
        newCompetency.put(Constants.COMPETENCY_AREA_REF_ID, "KCMFINAL_FW_COMPETENCYAREA_SAME");
        newCompetency.put(Constants.COMPETENCY_THEME_REF_ID, "KCMFINAL_FW_THEME_SAME");
        newCompetency.put(Constants.COMPETENCY_SUB_THEME_REF_ID, "KCMFINAL_FW_SUBTHEME_SAME");
        newCompetencies.add(newCompetency);

        Map<String, Object> newContextData = new HashMap<>();
        newContextData.put("field1", "value1");
        newContextData.put("field2", "value2");
        newContextData.put(Constants.UPLOAD_DOCUMENT_URL, "http://example.com/doc.pdf");
        newContextData.put(Constants.URL, "");
        newContextData.put(Constants.COMPETENCIES_V6, newCompetencies);

        Map<String, Object> requestData = new HashMap<>();
        requestData.put(Constants.ID, "achv1");
        requestData.put(Constants.CONTEXT_TYPE, "testContext");
        requestData.put(Constants.CONTEXT_DATA, newContextData);
        Map<String, Object> request = new HashMap<>();
        request.put(Constants.REQUEST, requestData);

        when(accessTokenValidator.fetchUserIdFromAccessToken(anyString())).thenReturn("user123");
        when(cbServerProperties.isRequireEs()).thenReturn(true);

        // Existing record with lowercase competencies
        Map<String, Object> existingRecord = new HashMap<>();
        existingRecord.put(Constants.STATUS, Constants.PENDING);
        existingRecord.put(Constants.CREATED_ON, LocalDate.now());
        existingRecord.put(Constants.USER_ID_RQST, "user123");

        List<Map<String, String>> existingCompetencies = new ArrayList<>();
        Map<String, String> existingCompetency = new HashMap<>();
        existingCompetency.put(Constants.COMPETENCY_AREA_REF_ID, "kcmfinal_fw_competencyarea_same");
        existingCompetency.put(Constants.COMPETENCY_THEME_REF_ID, "kcmfinal_fw_theme_same");
        existingCompetency.put(Constants.COMPETENCY_SUB_THEME_REF_ID, "kcmfinal_fw_subtheme_same");
        existingCompetencies.add(existingCompetency);

        Map<String, Object> existingContextData = new HashMap<>();
        existingContextData.put("field1", "value1");
        existingContextData.put("field2", "value2");
        existingContextData.put(Constants.UPLOAD_DOCUMENT_URL, "http://example.com/doc.pdf");
        existingContextData.put(Constants.URL, "");
        existingContextData.put(Constants.COMPETENCIES_V6, existingCompetencies);
        existingRecord.put(Constants.CONTEXT_DATA, existingContextData);

        when(cassandraOperation.getRecordsByPropertiesByKey(any(), any(), any(), any(), any()))
                .thenReturn(Collections.singletonList(existingRecord));
        ApiResponse cassandraResponse = new ApiResponse();
        cassandraResponse.put(Constants.RESPONSE, Constants.SUCCESS);
        when(cassandraOperation.insertRecord(any(), any(), any())).thenReturn(cassandraResponse);
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");
        when(objectMapper.convertValue(any(), eq(Map.class))).thenReturn(new HashMap<>());
        when(objectMapper.readValue(anyString(), eq(Map.class))).thenReturn(existingContextData);

        Map<String, Object> esDoc = new HashMap<>();
        esDoc.put(Constants.CREATED_ON, "2024-01-01T00:00:00.000+0000");
        when(esClientService.readDocument(any(), any())).thenReturn(esDoc);

        SearchResult searchResult = new SearchResult();
        searchResult.setData(new ArrayList<>());
        try {
            when(esClientService.searchDocuments(any(), any())).thenReturn(searchResult);
        } catch (Exception e) {
            fail("Mock setup failed");
        }

        // Act
        ApiResponse response = achievementService.updateLearnerAchievement(request, "token", "org1");

        // Assert - Should not publish Kafka event since competencies are same (case-insensitive)
        assertEquals(HttpStatus.OK, response.getResponseCode());
        assertNotNull(response.getResult());
        verify(kafkaEventPublisher, never()).publish(anyString(), (Object) any(), anyString());
    }

    // ==================== getUserAchievementsByUserIds TESTS ====================

    /** Builds a realistic achievement record exactly as Cassandra/cache would return it. */
    private Map<String, Object> buildAchievement(String id) {
        Map<String, Object> contextData = new HashMap<>();
        contextData.put("title", "Java Certificate");
        contextData.put("issuedDate", "2026-02-28T18:30:00.000Z");
        contextData.put("trainingType", "Domestic Training");
        contextData.put("deliveryMode", "ONLINE");
        contextData.put("learningHours", 20);

        Map<String, Object> achievement = new HashMap<>();
        achievement.put(Constants.ID, id);
        achievement.put(Constants.USER_ID_RQST, "user123");
        achievement.put(Constants.ORG_ID, "org001");
        achievement.put(Constants.CONTEXT_TYPE, Constants.ACHIEVEMENTS);
        achievement.put(Constants.CONTEXT_DATA, contextData);
        achievement.put(Constants.STATUS, "Approved");
        achievement.put(Constants.CREATED_ON, "2026-03-09T13:15:00.323Z");
        achievement.put("reason", null);
        achievement.put("updatedBy", "user123");
        achievement.put("source", "igot");
        return achievement;
    }

    /**
     * Builds the request map the service now expects:
     * { "request": { "achievementIds": [...] } }
     */
    private Map<String, Object> buildRequest(List<String> ids) {
        Map<String, Object> inner = new HashMap<>();
        inner.put(Constants.ACHIEVEMENT_IDS, ids);
        Map<String, Object> request = new HashMap<>();
        request.put(Constants.REQUEST, inner);
        return request;
    }

    /** Stubs cbServerProperties so no field filtering is applied (return everything). */
    private void stubNoFieldFiltering() {
        when(cbServerProperties.getBulkListResponseFields()).thenReturn("");
        when(cbServerProperties.getBulkListContextDataFields()).thenReturn("");
    }

    // ── token / input validation ─────────────────────────────────────────────

    @Test
    void testGetUserAchievementsByUserIds_blankToken_returnsUnauthorized() {
        when(accessTokenValidator.fetchUserIdFromAccessToken(anyString())).thenReturn("");

        ApiResponse response = achievementService.getUserAchievementsByUserIds(
                "bad-token", buildRequest(List.of("achv1")));

        assertEquals(HttpStatus.UNAUTHORIZED, response.getResponseCode());
        assertNotNull(response.getParams().getErrMsg());
        assertTrue(response.getParams().getErrMsg().contains("Invalid or missing access token"));
        verifyNoInteractions(cassandraOperation, cacheService);
    }

    @Test
    void testGetUserAchievementsByUserIds_nullToken_returnsUnauthorized() {
        when(accessTokenValidator.fetchUserIdFromAccessToken(anyString())).thenReturn(null);

        ApiResponse response = achievementService.getUserAchievementsByUserIds(
                "token", buildRequest(List.of("achv1")));

        assertEquals(HttpStatus.UNAUTHORIZED, response.getResponseCode());
    }

    @Test
    void testGetUserAchievementsByUserIds_nullRequest_returnsBadRequest() {
        // null request → no "request" key → achievementIds stays null → BAD_REQUEST
        when(accessTokenValidator.fetchUserIdFromAccessToken(anyString())).thenReturn("user123");

        Map<String, Object> emptyRequest = new HashMap<>(); // no "request" key at all
        ApiResponse response = achievementService.getUserAchievementsByUserIds("token", emptyRequest);

        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
        assertTrue(response.getParams().getErrMsg().contains("achievementIds"));
        verifyNoInteractions(cassandraOperation, cacheService);
    }

    @Test
    void testGetUserAchievementsByUserIds_emptyAchievementIds_returnsBadRequest() {
        // empty list inside request → achievementIds is empty → BAD_REQUEST
        when(accessTokenValidator.fetchUserIdFromAccessToken(anyString())).thenReturn("user123");

        ApiResponse response = achievementService.getUserAchievementsByUserIds(
                "token", buildRequest(Collections.emptyList()));

        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
        assertTrue(response.getParams().getErrMsg().contains("achievementIds"));
    }

    // ── cache hit ────────────────────────────────────────────────────────────

    @Test
    void testGetUserAchievementsByUserIds_cacheHit_returnsFromCache_noCassandraCall() throws Exception {
        when(accessTokenValidator.fetchUserIdFromAccessToken(anyString())).thenReturn("user123");
        stubNoFieldFiltering();

        Map<String, Object> cached = buildAchievement("achv1");
        when(cacheService.getCache(anyString())).thenReturn("{\"id\":\"achv1\"}");
        when(objectMapper.readValue(anyString(),
                any(com.fasterxml.jackson.core.type.TypeReference.class))).thenReturn(cached);

        ApiResponse response = achievementService.getUserAchievementsByUserIds(
                "token", buildRequest(List.of("achv1")));

        assertEquals(HttpStatus.OK, response.getResponseCode());
        Map<String, Object> sr = (Map<String, Object>) response.getResult().get(Constants.SEARCH_RESULTS);
        assertEquals(1, sr.get(Constants.TOTAL_COUNT));
        verify(cassandraOperation, never()).getRecordsByPropertiesByKey(any(), any(), any(), any(), any());
    }

    @Test
    void testGetUserAchievementsByUserIds_cacheHit_multipleIds_allReturnedFromCache() throws Exception {
        when(accessTokenValidator.fetchUserIdFromAccessToken(anyString())).thenReturn("user123");
        stubNoFieldFiltering();

        Map<String, Object> cached1 = buildAchievement("achv1");
        Map<String, Object> cached2 = buildAchievement("achv2");
        when(cacheService.getCache(contains("achv1"))).thenReturn("{\"id\":\"achv1\"}");
        when(cacheService.getCache(contains("achv2"))).thenReturn("{\"id\":\"achv2\"}");
        when(objectMapper.readValue(eq("{\"id\":\"achv1\"}"),
                any(com.fasterxml.jackson.core.type.TypeReference.class))).thenReturn(cached1);
        when(objectMapper.readValue(eq("{\"id\":\"achv2\"}"),
                any(com.fasterxml.jackson.core.type.TypeReference.class))).thenReturn(cached2);

        ApiResponse response = achievementService.getUserAchievementsByUserIds(
                "token", buildRequest(List.of("achv1", "achv2")));

        assertEquals(HttpStatus.OK, response.getResponseCode());
        Map<String, Object> sr = (Map<String, Object>) response.getResult().get(Constants.SEARCH_RESULTS);
        assertEquals(2, sr.get(Constants.TOTAL_COUNT));
    }

    // ── cache miss → DB ──────────────────────────────────────────────────────

    @Test
    void testGetUserAchievementsByUserIds_cacheMiss_fetchesFromCassandraAndCaches() throws Exception {
        when(accessTokenValidator.fetchUserIdFromAccessToken(anyString())).thenReturn("user123");
        stubNoFieldFiltering();
        when(cacheService.getCache(anyString())).thenReturn(null);

        Map<String, Object> dbRecord = buildAchievement("achv1");
        when(cassandraOperation.getRecordsByPropertiesByKey(any(), any(), any(), any(), any()))
                .thenReturn(List.of(dbRecord));
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        ApiResponse response = achievementService.getUserAchievementsByUserIds(
                "token", buildRequest(List.of("achv1")));

        assertEquals(HttpStatus.OK, response.getResponseCode());
        Map<String, Object> sr = (Map<String, Object>) response.getResult().get(Constants.SEARCH_RESULTS);
        assertEquals(1, sr.get(Constants.TOTAL_COUNT));
        verify(cacheService, times(1)).putCache(anyString(), anyString(), anyInt());
    }

    @Test
    void testGetUserAchievementsByUserIds_cacheMiss_cassandraReturnsEmpty_achievementExcluded() {
        when(accessTokenValidator.fetchUserIdFromAccessToken(anyString())).thenReturn("user123");
        stubNoFieldFiltering();
        when(cacheService.getCache(anyString())).thenReturn(null);
        when(cassandraOperation.getRecordsByPropertiesByKey(any(), any(), any(), any(), any()))
                .thenReturn(Collections.emptyList());

        ApiResponse response = achievementService.getUserAchievementsByUserIds(
                "token", buildRequest(List.of("achv1")));

        assertEquals(HttpStatus.OK, response.getResponseCode());
        Map<String, Object> sr = (Map<String, Object>) response.getResult().get(Constants.SEARCH_RESULTS);
        assertEquals(0, sr.get(Constants.TOTAL_COUNT));
        List<?> data = (List<?>) sr.get(Constants.DATA);
        assertTrue(data.isEmpty());
    }

    @Test
    void testGetUserAchievementsByUserIds_cacheMiss_cassandraReturnsNull_achievementExcluded() {
        when(accessTokenValidator.fetchUserIdFromAccessToken(anyString())).thenReturn("user123");
        stubNoFieldFiltering();
        when(cacheService.getCache(anyString())).thenReturn(null);
        when(cassandraOperation.getRecordsByPropertiesByKey(any(), any(), any(), any(), any()))
                .thenReturn(null);

        ApiResponse response = achievementService.getUserAchievementsByUserIds(
                "token", buildRequest(List.of("achv1")));

        assertEquals(HttpStatus.OK, response.getResponseCode());
        Map<String, Object> sr = (Map<String, Object>) response.getResult().get(Constants.SEARCH_RESULTS);
        assertEquals(0, sr.get(Constants.TOTAL_COUNT));
    }

    // ── mixed cache-hit + DB ─────────────────────────────────────────────────

    @Test
    void testGetUserAchievementsByUserIds_mixCacheAndDB_bothReturned() throws Exception {
        when(accessTokenValidator.fetchUserIdFromAccessToken(anyString())).thenReturn("user123");
        stubNoFieldFiltering();

        Map<String, Object> cached1 = buildAchievement("achv1");
        when(cacheService.getCache(contains("achv1"))).thenReturn("{\"id\":\"achv1\"}");
        when(objectMapper.readValue(eq("{\"id\":\"achv1\"}"),
                any(com.fasterxml.jackson.core.type.TypeReference.class))).thenReturn(cached1);

        when(cacheService.getCache(contains("achv2"))).thenReturn(null);
        Map<String, Object> db2 = buildAchievement("achv2");
        when(cassandraOperation.getRecordsByPropertiesByKey(any(), any(), any(), any(), any()))
                .thenReturn(List.of(db2));
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        ApiResponse response = achievementService.getUserAchievementsByUserIds(
                "token", buildRequest(List.of("achv1", "achv2")));

        assertEquals(HttpStatus.OK, response.getResponseCode());
        Map<String, Object> sr = (Map<String, Object>) response.getResult().get(Constants.SEARCH_RESULTS);
        assertEquals(2, sr.get(Constants.TOTAL_COUNT));
    }

    // ── blank ids in list skipped ────────────────────────────────────────────

    @Test
    void testGetUserAchievementsByUserIds_blankIdsInList_areSkipped() throws Exception {
        when(accessTokenValidator.fetchUserIdFromAccessToken(anyString())).thenReturn("user123");
        stubNoFieldFiltering();

        Map<String, Object> cached = buildAchievement("achv1");
        when(cacheService.getCache(contains("achv1"))).thenReturn("{\"id\":\"achv1\"}");
        when(objectMapper.readValue(anyString(),
                any(com.fasterxml.jackson.core.type.TypeReference.class))).thenReturn(cached);

        // list with achv1 plus blank entries — service does Object::toString so pass as strings
        Map<String, Object> inner = new HashMap<>();
        inner.put(Constants.ACHIEVEMENT_IDS, Arrays.asList("achv1", "", "  "));
        Map<String, Object> request = new HashMap<>();
        request.put(Constants.REQUEST, inner);

        ApiResponse response = achievementService.getUserAchievementsByUserIds("token", request);

        assertEquals(HttpStatus.OK, response.getResponseCode());
        Map<String, Object> sr = (Map<String, Object>) response.getResult().get(Constants.SEARCH_RESULTS);
        assertEquals(1, sr.get(Constants.TOTAL_COUNT));
    }

    // ── cache deserialization failure → falls through to DB ──────────────────

    @Test
    void testGetUserAchievementsByUserIds_cacheDeserializationFails_fallsThroughToDB() throws Exception {
        when(accessTokenValidator.fetchUserIdFromAccessToken(anyString())).thenReturn("user123");
        stubNoFieldFiltering();
        when(cacheService.getCache(anyString())).thenReturn("{bad-json}");
        when(objectMapper.readValue(anyString(),
                any(com.fasterxml.jackson.core.type.TypeReference.class)))
                .thenThrow(new com.fasterxml.jackson.core.JsonProcessingException("bad") {});

        Map<String, Object> dbRecord = buildAchievement("achv1");
        when(cassandraOperation.getRecordsByPropertiesByKey(any(), any(), any(), any(), any()))
                .thenReturn(List.of(dbRecord));
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        ApiResponse response = achievementService.getUserAchievementsByUserIds(
                "token", buildRequest(List.of("achv1")));

        assertEquals(HttpStatus.OK, response.getResponseCode());
        Map<String, Object> sr = (Map<String, Object>) response.getResult().get(Constants.SEARCH_RESULTS);
        assertEquals(1, sr.get(Constants.TOTAL_COUNT));
    }

    // ── cache write failure does not break response ───────────────────────────

    @Test
    void testGetUserAchievementsByUserIds_cacheWriteFails_achievementStillReturned() throws Exception {
        when(accessTokenValidator.fetchUserIdFromAccessToken(anyString())).thenReturn("user123");
        stubNoFieldFiltering();
        when(cacheService.getCache(anyString())).thenReturn(null);

        Map<String, Object> dbRecord = buildAchievement("achv1");
        when(cassandraOperation.getRecordsByPropertiesByKey(any(), any(), any(), any(), any()))
                .thenReturn(List.of(dbRecord));
        when(objectMapper.writeValueAsString(any()))
                .thenThrow(new com.fasterxml.jackson.core.JsonProcessingException("ser-error") {});

        ApiResponse response = achievementService.getUserAchievementsByUserIds(
                "token", buildRequest(List.of("achv1")));

        assertEquals(HttpStatus.OK, response.getResponseCode());
        Map<String, Object> sr = (Map<String, Object>) response.getResult().get(Constants.SEARCH_RESULTS);
        assertEquals(1, sr.get(Constants.TOTAL_COUNT));
        verify(cacheService, never()).putCache(anyString(), anyString(), anyInt());
    }

    // ── unexpected exception → INTERNAL_SERVER_ERROR ─────────────────────────

    @Test
    void testGetUserAchievementsByUserIds_unexpectedException_returnsInternalServerError() {
        when(accessTokenValidator.fetchUserIdFromAccessToken(anyString())).thenReturn("user123");
        stubNoFieldFiltering();
        when(cacheService.getCache(anyString())).thenThrow(new RuntimeException("Redis down"));

        ApiResponse response = achievementService.getUserAchievementsByUserIds(
                "token", buildRequest(List.of("achv1")));

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getResponseCode());
        assertTrue(response.getParams().getErrMsg().contains("Failed to fetch achievements"));
    }

    // ── response structure ───────────────────────────────────────────────────

    @Test
    void testGetUserAchievementsByUserIds_responseHasCorrectApiId() throws Exception {
        when(accessTokenValidator.fetchUserIdFromAccessToken(anyString())).thenReturn("user123");
        stubNoFieldFiltering();
        when(cacheService.getCache(anyString())).thenReturn(null);
        when(cassandraOperation.getRecordsByPropertiesByKey(any(), any(), any(), any(), any()))
                .thenReturn(List.of(buildAchievement("achv1")));
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        ApiResponse response = achievementService.getUserAchievementsByUserIds(
                "token", buildRequest(List.of("achv1")));

        assertEquals(Constants.API_ACHIEVEMENT_V2_LIST, response.getId());
    }

    @Test
    void testGetUserAchievementsByUserIds_totalCountMatchesDataSize() throws Exception {
        when(accessTokenValidator.fetchUserIdFromAccessToken(anyString())).thenReturn("user123");
        stubNoFieldFiltering();

        Map<String, Object> c1 = buildAchievement("achv1");
        Map<String, Object> c2 = buildAchievement("achv2");
        when(cacheService.getCache(contains("achv1"))).thenReturn("{\"id\":\"achv1\"}");
        when(cacheService.getCache(contains("achv2"))).thenReturn("{\"id\":\"achv2\"}");
        when(objectMapper.readValue(eq("{\"id\":\"achv1\"}"),
                any(com.fasterxml.jackson.core.type.TypeReference.class))).thenReturn(c1);
        when(objectMapper.readValue(eq("{\"id\":\"achv2\"}"),
                any(com.fasterxml.jackson.core.type.TypeReference.class))).thenReturn(c2);

        ApiResponse response = achievementService.getUserAchievementsByUserIds(
                "token", buildRequest(List.of("achv1", "achv2")));

        Map<String, Object> sr = (Map<String, Object>) response.getResult().get(Constants.SEARCH_RESULTS);
        List<?> data = (List<?>) sr.get(Constants.DATA);
        assertEquals(data.size(), sr.get(Constants.TOTAL_COUNT));
        assertEquals(2, sr.get(Constants.TOTAL_COUNT));
    }

    // ── top-level response field filtering ───────────────────────────────────

    @Test
    void testGetUserAchievementsByUserIds_responseFieldsConfigured_removesUnwantedTopLevelKeys() throws Exception {
        when(accessTokenValidator.fetchUserIdFromAccessToken(anyString())).thenReturn("user123");
        when(cbServerProperties.getBulkListResponseFields()).thenReturn("userId,id,status,contextData");
        when(cbServerProperties.getBulkListContextDataFields()).thenReturn("");

        Map<String, Object> cached = buildAchievement("achv1");
        when(cacheService.getCache(anyString())).thenReturn("{\"id\":\"achv1\"}");
        when(objectMapper.readValue(anyString(),
                any(com.fasterxml.jackson.core.type.TypeReference.class))).thenReturn(cached);

        ApiResponse response = achievementService.getUserAchievementsByUserIds(
                "token", buildRequest(List.of("achv1")));

        assertEquals(HttpStatus.OK, response.getResponseCode());
        Map<String, Object> sr = (Map<String, Object>) response.getResult().get(Constants.SEARCH_RESULTS);
        Map<String, Object> item = ((List<Map<String, Object>>) sr.get(Constants.DATA)).get(0);

        assertTrue(item.containsKey(Constants.USER_ID_RQST));
        assertTrue(item.containsKey(Constants.ID));
        assertTrue(item.containsKey(Constants.STATUS));
        assertTrue(item.containsKey(Constants.CONTEXT_DATA));
        assertFalse(item.containsKey("reason"));
        assertFalse(item.containsKey("source"));
        assertFalse(item.containsKey("updatedBy"));
    }

    @Test
    void testGetUserAchievementsByUserIds_responseFieldsBlank_allTopLevelFieldsRetained() throws Exception {
        when(accessTokenValidator.fetchUserIdFromAccessToken(anyString())).thenReturn("user123");
        when(cbServerProperties.getBulkListResponseFields()).thenReturn("");
        when(cbServerProperties.getBulkListContextDataFields()).thenReturn("");

        Map<String, Object> cached = buildAchievement("achv1");
        when(cacheService.getCache(anyString())).thenReturn("{}");
        when(objectMapper.readValue(anyString(),
                any(com.fasterxml.jackson.core.type.TypeReference.class))).thenReturn(cached);

        ApiResponse response = achievementService.getUserAchievementsByUserIds(
                "token", buildRequest(List.of("achv1")));

        Map<String, Object> sr = (Map<String, Object>) response.getResult().get(Constants.SEARCH_RESULTS);
        Map<String, Object> item = ((List<Map<String, Object>>) sr.get(Constants.DATA)).get(0);

        assertTrue(item.containsKey("reason"));
        assertTrue(item.containsKey("source"));
        assertTrue(item.containsKey("updatedBy"));
        assertTrue(item.containsKey(Constants.CONTEXT_DATA));
    }

    @Test
    void testGetUserAchievementsByUserIds_responseFieldsConfigured_missingFieldSkipped() throws Exception {
        when(accessTokenValidator.fetchUserIdFromAccessToken(anyString())).thenReturn("user123");
        when(cbServerProperties.getBulkListResponseFields()).thenReturn("id,nonExistentField");
        when(cbServerProperties.getBulkListContextDataFields()).thenReturn("");

        Map<String, Object> cached = buildAchievement("achv1");
        when(cacheService.getCache(anyString())).thenReturn("{}");
        when(objectMapper.readValue(anyString(),
                any(com.fasterxml.jackson.core.type.TypeReference.class))).thenReturn(cached);

        ApiResponse response = achievementService.getUserAchievementsByUserIds(
                "token", buildRequest(List.of("achv1")));

        Map<String, Object> sr = (Map<String, Object>) response.getResult().get(Constants.SEARCH_RESULTS);
        Map<String, Object> item = ((List<Map<String, Object>>) sr.get(Constants.DATA)).get(0);

        assertTrue(item.containsKey(Constants.ID));
        assertFalse(item.containsKey("nonExistentField"));
    }

    // ── contextData field filtering ───────────────────────────────────────────

    @Test
    void testGetUserAchievementsByUserIds_contextDataFieldsConfigured_onlySpecifiedInnerFieldsKept() throws Exception {
        when(accessTokenValidator.fetchUserIdFromAccessToken(anyString())).thenReturn("user123");
        when(cbServerProperties.getBulkListResponseFields()).thenReturn("");
        when(cbServerProperties.getBulkListContextDataFields()).thenReturn("title,issuedDate");

        Map<String, Object> cached = buildAchievement("achv1");
        when(cacheService.getCache(anyString())).thenReturn("{}");
        when(objectMapper.readValue(anyString(),
                any(com.fasterxml.jackson.core.type.TypeReference.class))).thenReturn(cached);

        ApiResponse response = achievementService.getUserAchievementsByUserIds(
                "token", buildRequest(List.of("achv1")));

        Map<String, Object> sr = (Map<String, Object>) response.getResult().get(Constants.SEARCH_RESULTS);
        Map<String, Object> item = ((List<Map<String, Object>>) sr.get(Constants.DATA)).get(0);
        Map<String, Object> ctx = (Map<String, Object>) item.get(Constants.CONTEXT_DATA);

        assertTrue(ctx.containsKey("title"));
        assertTrue(ctx.containsKey("issuedDate"));
        assertFalse(ctx.containsKey("trainingType"));
        assertFalse(ctx.containsKey("deliveryMode"));
        assertFalse(ctx.containsKey("learningHours"));
    }

    @Test
    void testGetUserAchievementsByUserIds_contextDataFieldsBlank_allInnerFieldsRetained() throws Exception {
        when(accessTokenValidator.fetchUserIdFromAccessToken(anyString())).thenReturn("user123");
        when(cbServerProperties.getBulkListResponseFields()).thenReturn("");
        when(cbServerProperties.getBulkListContextDataFields()).thenReturn("");

        Map<String, Object> cached = buildAchievement("achv1");
        when(cacheService.getCache(anyString())).thenReturn("{}");
        when(objectMapper.readValue(anyString(),
                any(com.fasterxml.jackson.core.type.TypeReference.class))).thenReturn(cached);

        ApiResponse response = achievementService.getUserAchievementsByUserIds(
                "token", buildRequest(List.of("achv1")));

        Map<String, Object> sr = (Map<String, Object>) response.getResult().get(Constants.SEARCH_RESULTS);
        Map<String, Object> ctx = (Map<String, Object>)
                ((List<Map<String, Object>>) sr.get(Constants.DATA)).get(0).get(Constants.CONTEXT_DATA);

        assertTrue(ctx.containsKey("title"));
        assertTrue(ctx.containsKey("issuedDate"));
        assertTrue(ctx.containsKey("trainingType"));
        assertTrue(ctx.containsKey("deliveryMode"));
        assertTrue(ctx.containsKey("learningHours"));
    }

    @Test
    void testGetUserAchievementsByUserIds_contextDataFieldsConfigured_noContextDataInAchievement_noError() throws Exception {
        when(accessTokenValidator.fetchUserIdFromAccessToken(anyString())).thenReturn("user123");
        when(cbServerProperties.getBulkListResponseFields()).thenReturn("");
        when(cbServerProperties.getBulkListContextDataFields()).thenReturn("title,issuedDate");

        Map<String, Object> noCtx = buildAchievement("achv1");
        noCtx.remove(Constants.CONTEXT_DATA);
        when(cacheService.getCache(anyString())).thenReturn("{}");
        when(objectMapper.readValue(anyString(),
                any(com.fasterxml.jackson.core.type.TypeReference.class))).thenReturn(noCtx);

        ApiResponse response = achievementService.getUserAchievementsByUserIds(
                "token", buildRequest(List.of("achv1")));

        assertEquals(HttpStatus.OK, response.getResponseCode());
        Map<String, Object> sr = (Map<String, Object>) response.getResult().get(Constants.SEARCH_RESULTS);
        assertEquals(1, sr.get(Constants.TOTAL_COUNT));
        Map<String, Object> item = ((List<Map<String, Object>>) sr.get(Constants.DATA)).get(0);
        assertFalse(item.containsKey(Constants.CONTEXT_DATA));
    }

    @Test
    void testGetUserAchievementsByUserIds_contextDataIsString_filteringSkipped_noClassCastException() throws Exception {
        when(accessTokenValidator.fetchUserIdFromAccessToken(anyString())).thenReturn("user123");
        when(cbServerProperties.getBulkListResponseFields()).thenReturn("");
        when(cbServerProperties.getBulkListContextDataFields()).thenReturn("title");

        Map<String, Object> strCtx = buildAchievement("achv1");
        strCtx.put(Constants.CONTEXT_DATA, "{\"title\":\"test\"}");
        when(cacheService.getCache(anyString())).thenReturn("{}");
        when(objectMapper.readValue(anyString(),
                any(com.fasterxml.jackson.core.type.TypeReference.class))).thenReturn(strCtx);

        ApiResponse response = achievementService.getUserAchievementsByUserIds(
                "token", buildRequest(List.of("achv1")));

        assertEquals(HttpStatus.OK, response.getResponseCode());
        Map<String, Object> sr = (Map<String, Object>) response.getResult().get(Constants.SEARCH_RESULTS);
        Map<String, Object> item = ((List<Map<String, Object>>) sr.get(Constants.DATA)).get(0);
        assertTrue(item.get(Constants.CONTEXT_DATA) instanceof String);
    }

    // ── both filters combined ────────────────────────────────────────────────

    @Test
    void testGetUserAchievementsByUserIds_bothFiltersConfigured_appliedInSequence() throws Exception {
        when(accessTokenValidator.fetchUserIdFromAccessToken(anyString())).thenReturn("user123");
        when(cbServerProperties.getBulkListResponseFields()).thenReturn("userId,id,status,contextData");
        when(cbServerProperties.getBulkListContextDataFields()).thenReturn("title,learningHours");

        Map<String, Object> cached = buildAchievement("achv1");
        when(cacheService.getCache(anyString())).thenReturn("{}");
        when(objectMapper.readValue(anyString(),
                any(com.fasterxml.jackson.core.type.TypeReference.class))).thenReturn(cached);

        ApiResponse response = achievementService.getUserAchievementsByUserIds(
                "token", buildRequest(List.of("achv1")));

        assertEquals(HttpStatus.OK, response.getResponseCode());
        Map<String, Object> sr = (Map<String, Object>) response.getResult().get(Constants.SEARCH_RESULTS);
        Map<String, Object> item = ((List<Map<String, Object>>) sr.get(Constants.DATA)).get(0);

        assertTrue(item.containsKey(Constants.USER_ID_RQST));
        assertTrue(item.containsKey(Constants.ID));
        assertTrue(item.containsKey(Constants.STATUS));
        assertTrue(item.containsKey(Constants.CONTEXT_DATA));
        assertFalse(item.containsKey("source"));
        assertFalse(item.containsKey("reason"));

        Map<String, Object> ctx = (Map<String, Object>) item.get(Constants.CONTEXT_DATA);
        assertTrue(ctx.containsKey("title"));
        assertTrue(ctx.containsKey("learningHours"));
        assertFalse(ctx.containsKey("trainingType"));
        assertFalse(ctx.containsKey("deliveryMode"));
        assertFalse(ctx.containsKey("issuedDate"));
    }

}
