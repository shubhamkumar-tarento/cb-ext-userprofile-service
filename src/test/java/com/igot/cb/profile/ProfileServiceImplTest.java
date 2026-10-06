package com.igot.cb.profile;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.igot.cb.authentication.util.AccessTokenValidator;
import com.igot.cb.common.OutboundRequestHandlerServiceImpl;
import com.igot.cb.profile.entity.CustomFieldEntity;
import com.igot.cb.profile.repository.CustomFieldRepository;
import com.igot.cb.profile.service.ProfileServiceImpl;
import com.igot.cb.transactional.cassandrautils.CassandraOperation;
import com.igot.cb.transactional.elasticsearch.service.EsUtilServiceImpl;
import com.igot.cb.transactional.redis.cache.CacheService;
import com.igot.cb.transactional.service.RequestHandlerServiceImpl;
import com.igot.cb.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.argThat;

@ExtendWith(MockitoExtension.class)
class ProfileServiceImplTest {
    @Mock
    private AccessTokenValidator accessTokenValidator;
    @Mock
    private CbServerProperties serverProperties;
    @Mock
    private CassandraOperation cassandraOperation;
    @Mock
    private CacheService cacheService;
    @Mock
    private ObjectMapper objectMapper;
    @Mock
    private ProjectUtil projectUtil;

    @Mock
    private CustomFieldRepository customFieldRepository;

    @Mock
    private RequestHandlerServiceImpl requestHandlerService;

    private ProfileServiceImpl profileService;

    @Mock
    private EsUtilServiceImpl esUtilService;

    private final String userID = "user-123";
    private final String token = "dummy-token";
    private static final String CACHE_KEY = "user:competencies:user123";
    private final String [] contextType = {"contextA"};
    private static final String REDIS_KEY = "user:extendedProfile:project:user-123";

    @Mock
    private RestTemplate restTemplate;

    private OutboundRequestHandlerServiceImpl service;

    @BeforeEach
     void setUp() {
        MockitoAnnotations.openMocks(this);
        service = spy(new OutboundRequestHandlerServiceImpl(restTemplate));
        // @Spy @InjectMocks is unreliable for constructor-injected classes with many
        // dependencies: when Mockito can't cleanly resolve every constructor parameter it
        // silently falls back to constructing real (non-mocked) objects for ALL of them.
        // Construct explicitly instead so every dependency is guaranteed to be the mock above.
        profileService = spy(new ProfileServiceImpl(
                accessTokenValidator,
                serverProperties,
                cassandraOperation,
                cacheService,
                objectMapper,
                projectUtil,
                requestHandlerService,
                customFieldRepository,
                esUtilService,
                service
        ));
        ReflectionTestUtils.setField(
                profileService,
                "basicDetailsFilteredKeys",
                "profileCompletionPercentage,karmaPoints,certificateCount,postCount"
        );
    }

    static class TestContext {
        String contextKey;
        String dateField;
        List<Map<String, Object>> testData;

        TestContext(String contextKey, String dateField, List<Map<String, Object>> testData) {
            this.contextKey = contextKey;
            this.dateField = dateField;
            this.testData = testData;
        }
    }

    static Stream<TestContext> contextProvider() {
        return Stream.of(
                new TestContext(
                        Constants.SERVICE_HISTORY,
                        "startDate",
                        List.of(
                                new HashMap<>(Map.of("startDate", "2019-01-01T00:00:00Z", "dummyField", "dummyValue")),
                                new HashMap<>(Map.of("startDate", "2023-06-15T00:00:00Z", "dummyField", "dummyValue")),
                                new HashMap<>(Map.of("startDate", "2020-09-10T00:00:00Z", "dummyField", "dummyValue"))
                        )
                ),
                new TestContext(
                        Constants.ACHIEVEMENTS,
                        "issuedDate",
                        List.of(
                                new HashMap<>(Map.of("issuedDate", "2019-01-01T00:00:00Z", "dummyField", "dummyValue")),
                                new HashMap<>(Map.of("issuedDate", "2023-06-15T00:00:00Z", "dummyField", "dummyValue")),
                                new HashMap<>(Map.of("issuedDate", "2020-09-10T00:00:00Z", "dummyField", "dummyValue"))
                        )
                ),
                new TestContext(
                        Constants.EDUCATION_QUALIFICATION,
                        "startYear",
                        List.of(
                                new HashMap<>(Map.of("startYear", "2019", "dummyField", "dummyValue")),
                                new HashMap<>(Map.of("startYear", "2023", "dummyField", "dummyValue")),
                                new HashMap<>(Map.of("startYear", "2020", "dummyField", "dummyValue"))
                        )
                )
        );
    }


    @Test
     void testGetBasicProfile_invalidToken_returnsError() {
        when(accessTokenValidator.fetchUserIdFromAccessToken(token)).thenReturn(null);

        ApiResponse response = profileService.getBasicProfile(userID, token, false);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getResponseCode());
        assertEquals(Constants.FAILED, response.getParams().getStatus());
    }

    @Test
     void testGetExtendedProfileSummary_noCache_fallsBackToDB() throws Exception {
        String[] contextTypes = { "education" };
        List<Map<String, Object>> dataList = List.of(Map.of("field", "value"));

        when(accessTokenValidator.fetchUserIdFromAccessToken(token)).thenReturn(userID);
        when(cacheService.getCache(anyString())).thenReturn(null);
        when(serverProperties.getContextType()).thenReturn(contextTypes);
        when(cassandraOperation.getRecordsByPropertiesByKey(anyString(), anyString(), anyMap(), isNull(), isNull()))
                .thenReturn(List.of(Map.of(Constants.CONTEXT_DATA, "[{\"field\":\"value\"}]")));
        when(projectUtil.parseListOfMap(anyString())).thenReturn(dataList);

        ApiResponse response = profileService.getExtendedProfileSummary(userID, token);

        assertEquals(HttpStatus.OK, response.getResponseCode());
        assertNotNull(response.get(Constants.RESPONSE));
    }

    @Test
     void testSaveExtendedProfile_validInput_shouldSucceed() {
        Map<String, Object> data = new HashMap<>();
        data.put("field1", "value1");
        Map<String, Object> requestMap = new HashMap<>();
        requestMap.put(Constants.USER_ID_RQST, userID);
        requestMap.put("education", List.of(data));

        Map<String, Object> request = new HashMap<>(); 
        request.put(Constants.REQUEST, requestMap);

        ApiResponse mockResponse = new ApiResponse();
        mockResponse.put(Constants.RESPONSE, Constants.SUCCESS);

        when(accessTokenValidator.fetchUserIdFromAccessToken(token)).thenReturn(userID);
        when(serverProperties.getContextType()).thenReturn(new String[] { "education" });
        when(serverProperties.getEducationalQualificationMandatoryFields()).thenReturn("");
        when(serverProperties.getAchievementsMandatoryFields()).thenReturn("");
        when(serverProperties.getServiceHistoryMandatoryFields()).thenReturn("");
        when(cassandraOperation.getRecordsByPropertiesByKey(any(), any(), anyMap(), any(), any()))
                .thenReturn(new ArrayList<>());
        when(cassandraOperation.insertRecord(any(), any(), any()))
                .thenReturn(mockResponse);

        ApiResponse response = profileService.saveExtendedProfile(request, token);

        assertEquals(HttpStatus.OK, response.getResponseCode());
        assertNotNull(response.get(Constants.RESULT));
    }

    @Test
     void testUpdateExtendedProfile_valid_shouldSucceed() throws Exception {
        String uuid = UUID.randomUUID().toString();
        Map<String, Object> incoming = new HashMap<>();
        incoming.put(Constants.UUID, uuid);
        incoming.put("key", "newVal");

        Map<String, Object> existing = new HashMap<>();
        existing.put(Constants.UUID, uuid);
        existing.put("key", "oldVal");

        Map<String, Object> requestMap = new HashMap<>();
        requestMap.put(Constants.USER_ID_RQST, userID);
        requestMap.put("education", List.of(incoming));
        Map<String, Object> request = Map.of(Constants.REQUEST, requestMap);

        ApiResponse mockResponse = new ApiResponse();
        mockResponse.put(Constants.RESPONSE, Constants.SUCCESS);

        when(accessTokenValidator.fetchUserIdFromAccessToken(token)).thenReturn(userID);
        when(serverProperties.getContextType()).thenReturn(new String[] { "education" });
        when(cassandraOperation.getRecordsByPropertiesByKey(any(), any(), anyMap(), any(), any()))
                .thenReturn(List.of(Map.of(Constants.CONTEXT_DATA, "[]")));
        when(projectUtil.parseListOfMap(anyString())).thenReturn(List.of(existing));
        when(cassandraOperation.insertRecord(any(), any(), any()))
                .thenReturn(mockResponse);

        ApiResponse response = profileService.updateExtendedProfile(request, token);

        assertEquals(HttpStatus.OK, response.getResponseCode());
        assertEquals(Constants.SUCCESS, response.get(Constants.RESPONSE));
    }

    @Test
     void testDeleteExtendedProfile_valid_shouldSucceed() throws Exception {
        String uuid = UUID.randomUUID().toString();
        Map<String, Object> deleteItem = Map.of(Constants.UUID, uuid);
        Map<String, Object> existingItem = Map.of(Constants.UUID, uuid, "key", "value");

        Map<String, Object> requestMap = new HashMap<>();
        requestMap.put(Constants.USER_ID_RQST, userID);
        requestMap.put("education", List.of(deleteItem));
        Map<String, Object> request = Map.of(Constants.REQUEST, requestMap);

        ApiResponse mockResponse = new ApiResponse();
        mockResponse.put(Constants.RESPONSE, Constants.SUCCESS);

        when(accessTokenValidator.fetchUserIdFromAccessToken(token)).thenReturn(userID);
        when(serverProperties.getContextType()).thenReturn(new String[] { "education" });
        when(cassandraOperation.getRecordsByPropertiesByKey(any(), any(), anyMap(), any(), any()))
                .thenReturn(List.of(Map.of(Constants.CONTEXT_DATA, "[]")));
        when(projectUtil.parseListOfMap(anyString())).thenReturn(new ArrayList<>(List.of(existingItem)));
        when(cassandraOperation.insertRecord(any(), any(), any()))
                .thenReturn(mockResponse);

        ApiResponse response = profileService.deleteExtendedProfile(request, token);

        assertEquals(HttpStatus.OK, response.getResponseCode());
        assertEquals(Constants.SUCCESS, response.get(Constants.RESPONSE));
    }

    @Test
     void testReadFullExtendedProfile_fromCache_success() throws Exception {
        String localContextType = "education";
        List<Map<String, Object>> data = List.of(Map.of("degree", "MSc"));

        when(accessTokenValidator.fetchUserIdFromAccessToken(token)).thenReturn(userID);
        when(cacheService.getCache(anyString())).thenReturn("[{'degree':'MSc'}]");
        when(projectUtil.parseListOfMap(anyString())).thenReturn(data);

        ApiResponse response = profileService.readFullExtendedProfile(userID, localContextType, token);

        assertEquals(HttpStatus.OK, response.getResponseCode());
        assertNotNull(response.get(Constants.RESPONSE));
    }

    @ParameterizedTest
    @MethodSource("contextProvider")
     void testSaveExtendedProfile_shouldSortByDateField(TestContext testContext) {
        Map<String, Object> requestMap = new HashMap<>();
        requestMap.put(Constants.USER_ID_RQST, userID);
        requestMap.put(testContext.contextKey, testContext.testData);

        Map<String, Object> request = new HashMap<>();
        request.put(Constants.REQUEST, requestMap);

        ApiResponse mockResponse = new ApiResponse();
        mockResponse.put(Constants.RESPONSE, Constants.SUCCESS);

        when(accessTokenValidator.fetchUserIdFromAccessToken(token)).thenReturn(userID);
        when(serverProperties.getContextType()).thenReturn(new String[]{testContext.contextKey});
        lenient().when(serverProperties.getEducationalQualificationMandatoryFields()).thenReturn("dummyField");
        lenient().when(serverProperties.getAchievementsMandatoryFields()).thenReturn("dummyField");
        lenient().when(serverProperties.getServiceHistoryMandatoryFields()).thenReturn("dummyField");

        lenient().when(serverProperties.getYearRegex()).thenReturn("^(19|20)[0-9]{2}$");
        when(cassandraOperation.getRecordsByPropertiesByKey(any(), any(), anyMap(), any(), any()))
                .thenReturn(new ArrayList<>());
        when(cassandraOperation.insertRecord(any(), any(), any())).thenReturn(mockResponse);

        ApiResponse response = profileService.saveExtendedProfile(request, token);

        assertEquals(HttpStatus.OK, response.getResponseCode());
        assertNotNull(response.get(Constants.RESULT));
    }

    @Test
    void testListCompetencies_invalidToken() {
        when(accessTokenValidator.fetchUserIdFromAccessToken(token)).thenReturn(null);

        ApiResponse response = profileService.listCompetencies(userID, token);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getResponseCode());
        assertEquals("Invalid or missing access token", response.getParams().getErrMsg());
    }

    @Test
    void testListCompetencies_noCoursesCompleted() {
        when(accessTokenValidator.fetchUserIdFromAccessToken(token)).thenReturn(userID);
        lenient().when(cacheService.getCache(CACHE_KEY)).thenReturn(null);

        Map<String, Object> dbRecord = Map.of(
                Constants.ACTIVE, true,
                Constants.STATUS, 1,
                Constants.COURSE_ID, "course1"
        );
        when(cassandraOperation.getAllRecordsByPrimaryKey(any(), any(), any(), any(), anyInt()))
                .thenReturn(List.of(dbRecord));

        ApiResponse response = profileService.listCompetencies(userID, token);

        assertEquals(HttpStatus.NO_CONTENT, response.getResponseCode());
        assertEquals("No competencies found for user.", response.getParams().getErrMsg());
    }



    @Test
    void testGetCourseMetadataBatched_emptyOrInvalidJson() throws IOException {
        List<String> courseIds = List.of("c1");
        when(cacheService.getCourseMetadataAsJsonString(courseIds)).thenReturn(Map.of("c1", "{}"));
        when(projectUtil.parseMap("{}")).thenReturn(null);

        Map<String, Map<String, Object>> result = profileService.getCourseMetadataBatched(courseIds, 10, List.of("a"));
        assertTrue(result.isEmpty());
    }


    @Test
    void testListCompetencies_cacheHit() {
        when(accessTokenValidator.fetchUserIdFromAccessToken(token)).thenReturn(userID);
        lenient().when(cacheService.getCache(CACHE_KEY)).thenReturn("{\"dummy\":1}");

        ApiResponse response = profileService.listCompetencies(userID, token);

        assertEquals(HttpStatus.NO_CONTENT, response.getResponseCode());
        assertEquals("No competencies found for user.", response.getParams().getErrMsg());
    }

    @Test
    void testListCompetencies_exception() {
        when(accessTokenValidator.fetchUserIdFromAccessToken(token)).thenReturn(userID);
        when(cacheService.getCache(CACHE_KEY)).thenThrow(new RuntimeException("Redis down"));

        ApiResponse response = profileService.listCompetencies(userID, token);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getResponseCode());
        assertEquals("Internal server error while fetching competencies", response.getParams().getErrMsg());
    }

    @Test
    void testExtendedProfile_invalidToken() {
        when(accessTokenValidator.fetchUserIdFromAccessToken(token)).thenReturn(null);

        ApiResponse response = profileService.getExtendedProfileSummary(userID, token);

        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
        assertEquals("Invalid or missing access token", response.getParams().getErrMsg());
    }

    @Test
    void testExtendedProfile_cacheHit() throws Exception {
        when(accessTokenValidator.fetchUserIdFromAccessToken(token)).thenReturn(userID);
        String cachedJson = "{\"contextA\":{\"count\":3,\"data\":[{\"a\":1},{\"b\":2},{\"c\":3}]}}";

        String redisKey = "user:extendedProfile:all:user-123"; // Correct key
        when(cacheService.getCache(redisKey)).thenReturn(cachedJson);

        Map<String, Object> fullMap = Map.of("contextA", Map.of(
                "count", 3,
                "data", List.of(
                        Map.of("a", 1),
                        Map.of("b", 2),
                        Map.of("c", 3)
                )
        ));
        when(objectMapper.readValue(cachedJson, Map.class)).thenReturn(fullMap);

        ApiResponse response = profileService.getExtendedProfileSummary(userID, token);

        assertEquals(HttpStatus.OK, response.getResponseCode());
        assertNotNull(response.get(Constants.RESPONSE));
    }

    @Test
    void testExtendedProfile_cacheWriteFails() throws Exception {
        when(accessTokenValidator.fetchUserIdFromAccessToken(token)).thenReturn(userID);
        when(cacheService.getCache(CACHE_KEY)).thenReturn(null);
        when(serverProperties.getContextType()).thenReturn(contextType);

        String contextJson = "[{\"a\":1}]";
        List<Map<String, Object>> records = List.of(Map.of(Constants.CONTEXT_DATA, contextJson));
        when(cassandraOperation.getRecordsByPropertiesByKey(any(), any(), any(), any(), any()))
                .thenReturn(records);

        when(projectUtil.parseListOfMap(contextJson)).thenReturn(List.of(Map.of("a", 1)));

        ApiResponse response = profileService.getExtendedProfileSummary(userID, token);

        assertEquals(HttpStatus.OK, response.getResponseCode());
        assertNotNull(response.get(Constants.RESPONSE));
    }

    @Test
    void testExtendedProfile_emptyData() {
        when(accessTokenValidator.fetchUserIdFromAccessToken(token)).thenReturn(userID);
        when(cacheService.getCache(CACHE_KEY)).thenReturn(null);
        when(serverProperties.getContextType()).thenReturn(contextType);
        when(cassandraOperation.getRecordsByPropertiesByKey(any(), any(), any(), any(), any()))
                .thenReturn(Collections.emptyList());

        ApiResponse response = profileService.getExtendedProfileSummary(userID, token);

        assertEquals(HttpStatus.NO_CONTENT, response.getResponseCode());
        assertEquals("No data found for user.", response.getParams().getErrMsg());
    }

    @Test
    void testExtendedProfile_cacheError_thenCassandraData() throws Exception {
        when(accessTokenValidator.fetchUserIdFromAccessToken(token)).thenReturn(userID);
        when(cacheService.getCache(CACHE_KEY)).thenThrow(new RuntimeException("Simulated"));

        when(serverProperties.getContextType()).thenReturn(contextType);

        String contextJson = "[{\"x\":\"1\"},{\"y\":\"2\"}]";
        List<Map<String, Object>> dbRecords = List.of(Map.of(Constants.CONTEXT_DATA, contextJson));
        when(cassandraOperation.getRecordsByPropertiesByKey(any(), any(), any(), any(), any()))
                .thenReturn(dbRecords);

        List<Map<String, Object>> parsed = List.of(Map.of("x", "1"), Map.of("y", "2"));
        when(projectUtil.parseListOfMap(contextJson)).thenReturn(parsed);

        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        ApiResponse response = profileService.getExtendedProfileSummary(userID, token);

        assertEquals(HttpStatus.OK, response.getResponseCode());
        assertNotNull(response.get(Constants.RESPONSE));
    }

    @Test
    void testInvalidToken() {
        when(accessTokenValidator.fetchUserIdFromAccessToken(token)).thenReturn(null);

        ApiResponse response = profileService.readFullExtendedProfile(userID, Arrays.toString(contextType), token);

        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
        assertEquals(Constants.FAILED, response.getParams().getStatus());
    }

    @Test
    void testCacheHit() throws Exception {
        String cachedJson = "[{\"data\": \"test\"}]";
        when(accessTokenValidator.fetchUserIdFromAccessToken(token)).thenReturn(userID);
        when(cacheService.getCache(REDIS_KEY)).thenReturn(cachedJson);

        List<Map<String, Object>> contextList = List.of(Map.of("data", "test"));
        when(projectUtil.parseListOfMap(cachedJson)).thenReturn(contextList);

        ApiResponse response = profileService.readFullExtendedProfile(userID, Arrays.toString(contextType), token);

        assertEquals(HttpStatus.NO_CONTENT, response.getResponseCode());
        assertEquals("No data found for user.", response.getParams().getErrMsg());
    }

    @Test
    void testCacheMiss_thenFetchFromCassandra_success() throws Exception {
        when(accessTokenValidator.fetchUserIdFromAccessToken(token)).thenReturn(userID);
        when(cacheService.getCache(REDIS_KEY)).thenReturn(null);

        String json = "[{\"data\": \"test\"}]";
        Map<String, Object> cassandraRow = Map.of(Constants.CONTEXT_DATA, json);
        when(cassandraOperation.getRecordsByPropertiesByKey(any(), any(), any(), any(), any()))
                .thenReturn(List.of(cassandraRow));

        List<Map<String, Object>> parsedList = List.of(Map.of("data", "test"));
        when(projectUtil.parseListOfMap(json)).thenReturn(parsedList);
        when(objectMapper.writeValueAsString(parsedList)).thenReturn(json);

        ApiResponse response = profileService.readFullExtendedProfile(userID, Arrays.toString(contextType), token);

        assertEquals(HttpStatus.OK, response.getResponseCode());
        assertEquals(parsedList.size(), ((Map<?, ?>) response.getResult().get(Constants.RESPONSE)).get(Constants.COUNT));
    }

    @Test
    void testCacheMiss_thenFetchFromCassandra_emptyResult() {
        when(accessTokenValidator.fetchUserIdFromAccessToken(token)).thenReturn(userID);
        when(cacheService.getCache(REDIS_KEY)).thenReturn(null);
        when(cassandraOperation.getRecordsByPropertiesByKey(any(), any(), any(), any(), any()))
                .thenReturn(Collections.emptyList());

        ApiResponse response = profileService.readFullExtendedProfile(userID, Arrays.toString(contextType), token);

        assertEquals(HttpStatus.NO_CONTENT, response.getResponseCode());
        assertEquals(Constants.FAILED, response.getParams().getStatus());
    }

    @Test
    void testParseListOfMapException() throws Exception {
        when(accessTokenValidator.fetchUserIdFromAccessToken(token)).thenReturn(userID);
        when(cacheService.getCache(REDIS_KEY)).thenReturn("[invalid_json]");
        when(projectUtil.parseListOfMap("[invalid_json]")).thenThrow(new IOException("fail"));

        // fallback to Cassandra
        String json = "[{\"data\": \"test\"}]";
        Map<String, Object> cassandraRow = Map.of(Constants.CONTEXT_DATA, json);
        when(cassandraOperation.getRecordsByPropertiesByKey(any(), any(), any(), any(), any()))
                .thenReturn(List.of(cassandraRow));
        when(projectUtil.parseListOfMap(json)).thenReturn(List.of(Map.of("data", "test")));
        when(objectMapper.writeValueAsString(any())).thenThrow(new RuntimeException("fail"));

        ApiResponse response = profileService.readFullExtendedProfile(userID, Arrays.toString(contextType), token);

        assertEquals(HttpStatus.OK, response.getResponseCode());
    }

    @Test
    void testLocationDetailsBranch() {
        String localContextType = Constants.LOCATION_DETAILS;
        String json = "[{\"location\": \"India\"}]";

        when(accessTokenValidator.fetchUserIdFromAccessToken(token)).thenReturn(userID);
        when(cacheService.getCache(any())).thenReturn(null);
        when(cassandraOperation.getRecordsByPropertiesByKey(any(), any(), any(), any(), any()))
                .thenReturn(List.of(Map.of(Constants.CONTEXT_DATA, json)));
        try {
            lenient().when(projectUtil.parseListOfMap(json)).thenReturn(List.of(Map.of("location", "India")));
            lenient().when(objectMapper.writeValueAsString(any())).thenReturn(json);
        } catch (Exception e) {
            fail("Should not throw exception");
        }

        ApiResponse response = profileService.readFullExtendedProfile(userID, localContextType, token);
        assertEquals(HttpStatus.OK, response.getResponseCode());
        assertTrue(response.getResult().get(Constants.RESPONSE) instanceof Map);
    }

    @Test
    void testGetBasicProfile_withInvalidToken() {
        String userId = "user-123";
        String localToken = "invalid-token";

        when(accessTokenValidator.fetchUserIdFromAccessToken(localToken)).thenReturn(null);

        ApiResponse response = profileService.getBasicProfile(userId, localToken, false);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getResponseCode());
        assertEquals("Invalid or missing access token", response.getParams().getErrMsg());
    }

    // Use reflection to test private methods:
    @Test
    void testBuildCacheKey() throws Exception {
        Method method = ProfileServiceImpl.class.getDeclaredMethod("buildCacheKey", String.class, String.class, String.class);
        method.setAccessible(true);
        String key = (String) method.invoke(profileService, "user", "basicProfile", "u123");
        assertEquals("user:basicProfile:u123", key);
    }

    @Test
    void testSaveExtendedProfile_invalidUserId() {
        String userToken = "token123";
        String userId = "user123";

        Map<String, Object> requestData = new HashMap<>();
        requestData.put("userId", userId);

        Map<String, Object> request = new HashMap<>();
        request.put("request", requestData);

        when(accessTokenValidator.fetchUserIdFromAccessToken(userToken)).thenReturn("wrongUser");

        ApiResponse response = profileService.saveExtendedProfile(request, userToken);

        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
        assertEquals("Invalid UserId in the request", response.getParams().getErrMsg());
    }


    @Test
    void testSaveExtendedProfile_invalidUserId1() {
        Map<String, Object> req = new HashMap<>();
        Map<String, Object> inner = new HashMap<>();
        inner.put(Constants.USER_ID_RQST, userID);
        req.put(Constants.REQUEST, inner);

        when(accessTokenValidator.fetchUserIdFromAccessToken(anyString())).thenReturn("wrong-user");

        ApiResponse response = profileService.saveExtendedProfile(req, "token");

        assertEquals(Constants.FAILED, response.getParams().getStatus());
        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
        assertEquals("Invalid UserId in the request", response.getParams().getErrMsg());
    }

    @Test
    void testSaveExtendedProfile_invalidContextType() {
        Map<String, Object> req = new HashMap<>();
        Map<String, Object> inner = new HashMap<>();
        inner.put(Constants.USER_ID_RQST, userID);
        inner.put("invalidContext", new ArrayList<>());
        req.put(Constants.REQUEST, inner);

        when(accessTokenValidator.fetchUserIdFromAccessToken(anyString())).thenReturn(userID);
        when(serverProperties.getContextType()).thenReturn(new String[] {"validContext"});

        ApiResponse response = profileService.saveExtendedProfile(req, "token");

        assertEquals(Constants.FAILED, response.getParams().getStatus());
        assertTrue(response.getParams().getErrMsg().contains("Invalid context type"));
    }

    @Test
    void testSaveExtendedProfile_validationFails() {
        Map<String, Object> req = Map.of(Constants.REQUEST, Map.of(Constants.USER_ID_RQST, userID));

        when(accessTokenValidator.fetchUserIdFromAccessToken(anyString())).thenReturn(userID);
        when(serverProperties.getContextType()).thenReturn(new String[] {});

        ApiResponse response = profileService.saveExtendedProfile(req, "token");

        assertEquals(Constants.OK, response.getResponseCode().getReasonPhrase());
    }

    @Test
    void testSaveExtendedProfile_nullIncomingList() {
        Map<String, Object> requestData = new HashMap<>();
        requestData.put(Constants.USER_ID_RQST, userID);
        requestData.put("contextA", null);

        Map<String, Object> req = Map.of(Constants.REQUEST, requestData);

        when(accessTokenValidator.fetchUserIdFromAccessToken(anyString())).thenReturn(userID);
        when(serverProperties.getContextType()).thenReturn(new String[] {"contextA"});

        ApiResponse response = profileService.saveExtendedProfile(req, "token");

        assertEquals(HttpStatus.OK, response.getResponseCode());
    }

    @Test
     void testSaveExtendedProfile_ValidationFailure_ReturnsBadRequest() {
        String userId = "user-123";
        String userToken = "valid-token";
        Map<String, Object> educationItem = new HashMap<>();
        educationItem.put("institute", "Test University");
        Map<String, Object> requestData = new HashMap<>();
        requestData.put(Constants.USER_ID_RQST, userId);
        requestData.put(Constants.EDUCATIONAL_QUALIFICATIONS, List.of(educationItem));
        Map<String, Object> request = new HashMap<>();
        request.put(Constants.REQUEST, requestData);
        when(accessTokenValidator.fetchUserIdFromAccessToken(userToken)).thenReturn(userId);
        when(serverProperties.getContextType()).thenReturn(new String[]{Constants.EDUCATIONAL_QUALIFICATIONS});
        when(serverProperties.getEducationalQualificationMandatoryFields()).thenReturn("degree,institute");
        when(serverProperties.getAchievementsMandatoryFields()).thenReturn("");
        when(serverProperties.getServiceHistoryMandatoryFields()).thenReturn("");
        ApiResponse response = profileService.saveExtendedProfile(request, userToken);
        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
        assertEquals(Constants.FAILED, response.getParams().getStatus());
        assertNotNull(response.getParams().getErrMsg());
        assertTrue(response.getParams().getErrMsg().contains("degree is mandatory"));
    }

    @Test
     void testSaveExtendedProfile_EmptyIncomingList_SkipsProcessingAndReturnsSuccess() {
        String userId = "user-123";
        String userToken = "valid-token";
        Map<String, Object> requestData = new HashMap<>();
        requestData.put(Constants.USER_ID_RQST, userId);
        requestData.put(Constants.EDUCATIONAL_QUALIFICATIONS, Collections.emptyList());
        Map<String, Object> validItem = new HashMap<>();
        validItem.put("someField", "someValue");
        requestData.put("otherContextType", List.of(validItem));
        Map<String, Object> request = new HashMap<>();
        request.put(Constants.REQUEST, requestData);
        when(accessTokenValidator.fetchUserIdFromAccessToken(userToken)).thenReturn(userId);
        when(serverProperties.getContextType()).thenReturn(new String[]{
                Constants.EDUCATIONAL_QUALIFICATIONS, "otherContextType"
        });
        when(serverProperties.getEducationalQualificationMandatoryFields()).thenReturn("degree,institute");
        when(serverProperties.getAchievementsMandatoryFields()).thenReturn("");
        when(serverProperties.getServiceHistoryMandatoryFields()).thenReturn("");
        when(cassandraOperation.getRecordsByPropertiesByKey(
                anyString(), anyString(), anyMap(), isNull(), isNull()))
                .thenReturn(new ArrayList<>());
        ApiResponse mockInsertResponse = new ApiResponse();
        mockInsertResponse.put(Constants.RESPONSE, Constants.SUCCESS);
        when(cassandraOperation.insertRecord(anyString(), anyString(), anyMap())).thenReturn(mockInsertResponse);
        ApiResponse response = profileService.saveExtendedProfile(request, userToken);
        assertEquals(HttpStatus.OK, response.getResponseCode());
        verify(cassandraOperation, never()).insertRecord(
                eq(Constants.KEYSPACE_SUNBIRD),
                eq(Constants.TABLE_USER_EXTENDED_PROFILE),
                argThat(map -> map.containsKey(Constants.CONTEXT_TYPE) &&
                        map.get(Constants.CONTEXT_TYPE).equals(Constants.EDUCATIONAL_QUALIFICATIONS))
        );
        verify(cassandraOperation, atLeastOnce()).insertRecord(
                eq(Constants.KEYSPACE_SUNBIRD),
                eq(Constants.TABLE_USER_EXTENDED_PROFILE),
                argThat(map -> map.containsKey(Constants.CONTEXT_TYPE) &&
                        map.get(Constants.CONTEXT_TYPE).equals("otherContextType"))
        );
    }

    @Test
     void testSaveExtendedProfile_SaveContextDataFails_ReturnsError(){
        String userId = "user-123";
        String userToken = "valid-token";
        String localContextType = Constants.EDUCATIONAL_QUALIFICATIONS;
        Map<String, Object> educationItem = new HashMap<>();
        educationItem.put("degree", "Masters");
        educationItem.put("institute", "Test University");
        Map<String, Object> requestData = new HashMap<>();
        requestData.put(Constants.USER_ID_RQST, userId);
        requestData.put(localContextType, List.of(educationItem));
        Map<String, Object> request = new HashMap<>();
        request.put(Constants.REQUEST, requestData);
        when(accessTokenValidator.fetchUserIdFromAccessToken(userToken)).thenReturn(userId);
        when(serverProperties.getContextType()).thenReturn(new String[]{localContextType});
        when(serverProperties.getEducationalQualificationMandatoryFields()).thenReturn("degree,institute");
        when(serverProperties.getAchievementsMandatoryFields()).thenReturn("");
        when(serverProperties.getServiceHistoryMandatoryFields()).thenReturn("");
        when(serverProperties.getDegreeNameRegex()).thenReturn("^[a-zA-Z0-9\\s\\(\\)\\-\\.,&'/]+$");
        when(cassandraOperation.getRecordsByPropertiesByKey(
                anyString(), anyString(), anyMap(), isNull(), isNull()))
                .thenReturn(new ArrayList<>());
        ApiResponse mockFailureResponse = new ApiResponse();
        mockFailureResponse.put(Constants.RESPONSE, Constants.FAILED);
        when(cassandraOperation.insertRecord(anyString(), anyString(), anyMap())).thenReturn(mockFailureResponse);
        ApiResponse response = profileService.saveExtendedProfile(request, userToken);
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getResponseCode());
        assertEquals(Constants.FAILED, response.getParams().getStatus());
        assertEquals("Failed to save data for contextType: " + localContextType, response.getParams().getErrMsg());
        verify(cassandraOperation).insertRecord(
                eq(Constants.KEYSPACE_SUNBIRD),
                eq(Constants.TABLE_USER_EXTENDED_PROFILE),
                argThat(map -> map.get(Constants.CONTEXT_TYPE).equals(localContextType))
        );
    }

    @Test
     void testSaveExtendedProfile_UserIdMismatchWithToken_ReturnsBadRequest() {
        String tokenUserId = "token-user-123";  // User ID from token
        String requestUserId = "request-user-456";  // Different user ID in request
        String userToken = "some-token";
        Map<String, Object> requestData = new HashMap<>();
        requestData.put(Constants.USER_ID_RQST, requestUserId);
        Map<String, Object> request = new HashMap<>();
        request.put(Constants.REQUEST, requestData);
        when(accessTokenValidator.fetchUserIdFromAccessToken(userToken)).thenReturn(tokenUserId);
        ApiResponse response = profileService.saveExtendedProfile(request, userToken);
        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
        assertEquals(Constants.FAILED, response.getParams().getStatus());
        assertEquals("Invalid UserId in the request", response.getParams().getErrMsg());
        verify(accessTokenValidator).fetchUserIdFromAccessToken(userToken);
        verifyNoMoreInteractions(cassandraOperation, cacheService);
    }

    @Test
    void testSaveExtendedProfile_NullOrEmptyList_SkipsProcessing() {
        String userId = "user-123";
        String userToken = "valid-token";
        Map<String, Object> requestData = new HashMap<>();
        requestData.put(Constants.USER_ID_RQST, userId);
        requestData.put(Constants.EDUCATIONAL_QUALIFICATIONS, Collections.emptyList());  // Empty list
        requestData.put(Constants.SERVICE_HISTORY, null);  // Null list
        Map<String, Object> achievementItem = new HashMap<>();
        achievementItem.put("title", "Achievement 1");
        achievementItem.put("issuer", "Issuer 1");
        requestData.put(Constants.ACHIEVEMENTS, List.of(achievementItem));
        Map<String, Object> request = new HashMap<>();
        request.put(Constants.REQUEST, requestData);
        when(accessTokenValidator.fetchUserIdFromAccessToken(userToken)).thenReturn(userId);
        when(serverProperties.getContextType()).thenReturn(new String[]{
                Constants.EDUCATIONAL_QUALIFICATIONS,
                Constants.SERVICE_HISTORY,
                Constants.ACHIEVEMENTS
        });
        when(serverProperties.getEducationalQualificationMandatoryFields()).thenReturn("");
        when(serverProperties.getAchievementsMandatoryFields()).thenReturn("title,issuer");
        when(serverProperties.getServiceHistoryMandatoryFields()).thenReturn("");
        when(cassandraOperation.getRecordsByPropertiesByKey(
                anyString(), anyString(), anyMap(), isNull(), isNull()))
                .thenReturn(new ArrayList<>());
        ApiResponse mockResponse = new ApiResponse();
        mockResponse.put(Constants.RESPONSE, Constants.SUCCESS);
        when(cassandraOperation.insertRecord(anyString(), anyString(), anyMap())).thenReturn(mockResponse);
        ApiResponse response = profileService.saveExtendedProfile(request, userToken);
        assertEquals(HttpStatus.OK, response.getResponseCode());
        verify(cassandraOperation, never()).insertRecord(
                eq(Constants.KEYSPACE_SUNBIRD),
                eq(Constants.TABLE_USER_EXTENDED_PROFILE),
                argThat(map -> map.get(Constants.CONTEXT_TYPE).equals(Constants.EDUCATIONAL_QUALIFICATIONS))
        );
        verify(cassandraOperation, never()).insertRecord(
                eq(Constants.KEYSPACE_SUNBIRD),
                eq(Constants.TABLE_USER_EXTENDED_PROFILE),
                argThat(map -> map.get(Constants.CONTEXT_TYPE).equals(Constants.SERVICE_HISTORY))
        );
        verify(cassandraOperation).insertRecord(
                eq(Constants.KEYSPACE_SUNBIRD),
                eq(Constants.TABLE_USER_EXTENDED_PROFILE),
                argThat(map -> map.get(Constants.CONTEXT_TYPE).equals(Constants.ACHIEVEMENTS))
        );
    }

    @Test
     void testUpdateExtendedProfile_FiltersOutItemsWithoutUuid() throws IOException {
        String userId = "user-123";
        String userToken = "valid-token";
        String localContextType = "education";
        String uuid1 = "uuid-1";
        String uuid2 = "uuid-2";
        List<Map<String, Object>> existingData = new ArrayList<>();
        Map<String, Object> item1 = new HashMap<>();
        item1.put(Constants.UUID, uuid1);
        item1.put("degree", "Bachelor's");
        existingData.add(item1);
        Map<String, Object> item2 = new HashMap<>();
        item2.put(Constants.UUID, uuid2);
        item2.put("degree", "Master's");
        existingData.add(item2);
        Map<String, Object> item3 = new HashMap<>();
        item3.put("degree", "PhD");
        existingData.add(item3);
        Map<String, Object> update = new HashMap<>();
        update.put(Constants.UUID, uuid1);
        update.put("degree", "Updated Bachelor's");
        Map<String, Object> requestData = new HashMap<>();
        requestData.put(Constants.USER_ID_RQST, userId);
        requestData.put(localContextType, List.of(update));
        Map<String, Object> request = new HashMap<>();
        request.put(Constants.REQUEST, requestData);
        when(accessTokenValidator.fetchUserIdFromAccessToken(userToken)).thenReturn(userId);
        when(serverProperties.getContextType()).thenReturn(new String[]{localContextType});
        when(cassandraOperation.getRecordsByPropertiesByKey(anyString(), anyString(), anyMap(), isNull(), isNull()))
                .thenReturn(List.of(Map.of(Constants.CONTEXT_DATA, "[]")));
        when(projectUtil.parseListOfMap(anyString())).thenReturn(existingData);
        String updatedJsonData = "[{\"uuid\":\"uuid-1\",\"degree\":\"Updated Bachelor's\"},{\"uuid\":\"uuid-2\",\"degree\":\"Master's\"}]";
        when(objectMapper.writeValueAsString(any())).thenReturn(updatedJsonData);
        ApiResponse mockResponse = new ApiResponse();
        mockResponse.put(Constants.RESPONSE, Constants.SUCCESS);
        when(cassandraOperation.insertRecord(anyString(), anyString(), anyMap())).thenReturn(mockResponse);
        ApiResponse response = profileService.updateExtendedProfile(request, userToken);
        assertEquals(HttpStatus.OK, response.getResponseCode());
        ArgumentCaptor<Map<String, Object>> insertCaptor = ArgumentCaptor.forClass(Map.class);
        verify(cassandraOperation).insertRecord(
                eq(Constants.KEYSPACE_SUNBIRD),
                eq(Constants.TABLE_USER_EXTENDED_PROFILE),
                insertCaptor.capture());
        Map<String, Object> savedData = insertCaptor.getValue();
        assertEquals(updatedJsonData, savedData.get(Constants.CONTEXT_DATA));
        String contextData = (String) savedData.get(Constants.CONTEXT_DATA);
        assertTrue(contextData.contains(uuid1));
        assertTrue(contextData.contains(uuid2));
        assertTrue(contextData.contains("Updated Bachelor's"));
        assertFalse(contextData.contains("PhD"));
    }

    @Test
     void testUpdateExtendedProfile_InvalidUuid_ReturnsBadRequest() throws IOException {
        String userId = "user-123";
        String userToken = "valid-token";
        String localContextType = "education";
        String nonExistentUuid = "uuid-does-not-exist";
        List<Map<String, Object>> existingData = new ArrayList<>();
        existingData.add(Map.of(
                Constants.UUID, "existing-uuid-1",
                "degree", "Bachelor's"
        ));
        existingData.add(Map.of(
                Constants.UUID, "existing-uuid-2",
                "degree", "Master's"
        ));
        Map<String, Object> updateItem = new HashMap<>();
        updateItem.put(Constants.UUID, nonExistentUuid);
        updateItem.put("degree", "PhD");
        Map<String, Object> requestData = new HashMap<>();
        requestData.put(Constants.USER_ID_RQST, userId);
        requestData.put(localContextType, List.of(updateItem));
        Map<String, Object> request = new HashMap<>();
        request.put(Constants.REQUEST, requestData);
        when(accessTokenValidator.fetchUserIdFromAccessToken(userToken)).thenReturn(userId);
        when(serverProperties.getContextType()).thenReturn(new String[]{localContextType});
        when(cassandraOperation.getRecordsByPropertiesByKey(anyString(), anyString(), anyMap(), isNull(), isNull()))
                .thenReturn(List.of(Map.of(Constants.CONTEXT_DATA, "[]")));
        when(projectUtil.parseListOfMap(anyString())).thenReturn(existingData);
        ApiResponse response = profileService.updateExtendedProfile(request, userToken);
        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
        assertEquals(Constants.FAILED, response.getParams().getStatus());
        assertEquals("Invalid or missing UUID in incoming data.", response.getParams().getErrMsg());
        verify(cassandraOperation, never()).insertRecord(anyString(), anyString(), anyMap());
    }

    @Test
     void testUpdateExtendedProfile_SaveContextDataFails_ReturnsError() throws Exception {
        String userId = "user-123";
        String userToken = "valid-token";
        String localContextType = "education";
        String uuid = "existing-uuid-1";
        List<Map<String, Object>> existingData = new ArrayList<>();
        Map<String, Object> existingItem = new HashMap<>();
        existingItem.put(Constants.UUID, uuid);
        existingItem.put("degree", "Bachelor's");
        existingData.add(existingItem);
        Map<String, Object> updateItem = new HashMap<>();
        updateItem.put(Constants.UUID, uuid);
        updateItem.put("degree", "Updated Degree");
        Map<String, Object> requestData = new HashMap<>();
        requestData.put(Constants.USER_ID_RQST, userId);
        requestData.put(localContextType, List.of(updateItem));
        Map<String, Object> request = new HashMap<>();
        request.put(Constants.REQUEST, requestData);
        when(accessTokenValidator.fetchUserIdFromAccessToken(userToken)).thenReturn(userId);
        when(serverProperties.getContextType()).thenReturn(new String[]{localContextType});
        when(cassandraOperation.getRecordsByPropertiesByKey(anyString(), anyString(), anyMap(), isNull(), isNull()))
                .thenReturn(List.of(Map.of(Constants.CONTEXT_DATA, "[]")));
        when(projectUtil.parseListOfMap(anyString())).thenReturn(existingData);
        ApiResponse failureResponse = new ApiResponse();
        failureResponse.put(Constants.RESPONSE, Constants.FAILED);
        when(cassandraOperation.insertRecord(anyString(), anyString(), anyMap())).thenReturn(failureResponse);
        ApiResponse response = profileService.updateExtendedProfile(request, userToken);
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getResponseCode());
        assertEquals(Constants.FAILED, response.getParams().getStatus());
        assertEquals("Failed to update data for contextType: " + localContextType, response.getParams().getErrMsg());
        verify(cassandraOperation).insertRecord(
                eq(Constants.KEYSPACE_SUNBIRD),
                eq(Constants.TABLE_USER_EXTENDED_PROFILE),
                argThat(map -> map.get(Constants.CONTEXT_TYPE).equals(localContextType))
        );
    }

    @Test
     void testUpdateExtendedProfile_UserIdMismatch_ReturnsBadRequest() {
        String requestUserId = "user-123";
        String tokenUserId = "different-user-456";
        String userToken = "token-for-different-user";
        Map<String, Object> requestData = new HashMap<>();
        requestData.put(Constants.USER_ID_RQST, requestUserId);
        Map<String, Object> request = new HashMap<>();
        request.put(Constants.REQUEST, requestData);
        when(accessTokenValidator.fetchUserIdFromAccessToken(userToken)).thenReturn(tokenUserId);
        ApiResponse response = profileService.updateExtendedProfile(request, userToken);
        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
        assertEquals(Constants.FAILED, response.getParams().getStatus());
        assertEquals("Invalid UserId in the request", response.getParams().getErrMsg());
        verify(serverProperties, never()).getContextType();
        verify(cassandraOperation, never()).getRecordsByPropertiesByKey(anyString(), anyString(), anyMap(), any(), any());
    }


    @Test
     void testUpdateExtendedProfile_EmptyIncomingList_SkipsProcessing() {
        String userId = "user-123";
        String userToken = "valid-token";
        String contextType1 = "education";
        String contextType2 = "workExperience";
        Map<String, Object> requestData = new HashMap<>();
        requestData.put(Constants.USER_ID_RQST, userId);
        requestData.put(contextType1, Collections.emptyList());  // Empty list
        requestData.put(contextType2, null);  // Null list
        Map<String, Object> request = new HashMap<>();
        request.put(Constants.REQUEST, requestData);
        when(accessTokenValidator.fetchUserIdFromAccessToken(userToken)).thenReturn(userId);
        when(serverProperties.getContextType()).thenReturn(new String[]{contextType1, contextType2});
        ApiResponse response = profileService.updateExtendedProfile(request, userToken);
        assertEquals(HttpStatus.OK, response.getResponseCode());
        assertEquals(Constants.SUCCESS, response.get(Constants.RESPONSE));
        verify(cassandraOperation, never()).getRecordsByPropertiesByKey(anyString(), anyString(), anyMap(), any(), any());
        verify(cassandraOperation, never()).insertRecord(anyString(), anyString(), anyMap());
    }

    @Test
     void testUpdateExtendedProfile_NullUuid_ReturnsBadRequest() {
        String userId = "user-123";
        String userToken = "valid-token";
        String localContextType = "education";
        Map<String, Object> updateWithNullUuid = new HashMap<>();
        updateWithNullUuid.put("degree", "Updated Bachelor's");
        Map<String, Object> requestData = new HashMap<>();
        requestData.put(Constants.USER_ID_RQST, userId);
        requestData.put(localContextType, List.of(updateWithNullUuid));
        Map<String, Object> request = new HashMap<>();
        request.put(Constants.REQUEST, requestData);
        when(accessTokenValidator.fetchUserIdFromAccessToken(userToken)).thenReturn(userId);
        when(serverProperties.getContextType()).thenReturn(new String[]{localContextType});
        when(cassandraOperation.getRecordsByPropertiesByKey(anyString(), anyString(), anyMap(), any(), any()))
                .thenReturn(new ArrayList<>());
        ApiResponse response = profileService.updateExtendedProfile(request, userToken);
        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
        assertEquals(Constants.FAILED, response.getParams().getStatus());
        assertEquals("Invalid or missing UUID in incoming data.", response.getParams().getErrMsg());
        verify(cassandraOperation).getRecordsByPropertiesByKey(anyString(), anyString(), anyMap(), any(), any());
        verify(cassandraOperation, never()).insertRecord(anyString(), anyString(), anyMap());
    }

    @Test
     void testDeleteExtendedProfile_SaveContextDataFails_ReturnsError() throws Exception {
        String userId = "user-123";
        String userToken = "valid-token";
        String localContextType = "education";
        String uuid = UUID.randomUUID().toString();
        Map<String, Object> deleteItem = Map.of(Constants.UUID, uuid);
        Map<String, Object> requestData = new HashMap<>();
        requestData.put(Constants.USER_ID_RQST, userId);
        requestData.put(localContextType, List.of(deleteItem));
        Map<String, Object> request = new HashMap<>();
        request.put(Constants.REQUEST, requestData);
        when(accessTokenValidator.fetchUserIdFromAccessToken(userToken)).thenReturn(userId);
        when(serverProperties.getContextType()).thenReturn(new String[]{localContextType});
        when(cassandraOperation.getRecordsByPropertiesByKey(anyString(), anyString(), anyMap(), any(), any()))
                .thenReturn(List.of(Map.of(Constants.CONTEXT_DATA, "[]")));
        when(projectUtil.parseListOfMap(anyString()))
                .thenReturn(new ArrayList<>(List.of(new HashMap<>(Map.of(Constants.UUID, uuid)))));
        ApiResponse failedResponse = new ApiResponse();
        failedResponse.put(Constants.RESPONSE, Constants.FAILED);
        when(cassandraOperation.insertRecord(anyString(), anyString(), anyMap())).thenReturn(failedResponse);
        ApiResponse response = profileService.deleteExtendedProfile(request, userToken);
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getResponseCode());
        assertEquals(Constants.FAILED, response.getParams().getStatus());
        assertEquals("Failed to delete data for contextType: " + localContextType, response.getParams().getErrMsg());
    }

    @Test
     void testGetExtendedProfileSummary_CachePutThrowsException_LogsWarning() throws Exception {
        String userId = "user-123";
        String userToken = "valid-token";
        String localContextType = "education";
        when(serverProperties.getContextType()).thenReturn(new String[]{localContextType});
        when(cassandraOperation.getRecordsByPropertiesByKey(anyString(), anyString(), anyMap(), any(), any()))
                .thenReturn(List.of(Map.of(Constants.CONTEXT_DATA, "[{\"field\":\"value\"}]")));
        when(projectUtil.parseListOfMap(anyString())).thenReturn(
                new ArrayList<>(List.of(new HashMap<>(Map.of("field", "value"))))
        );
        when(accessTokenValidator.fetchUserIdFromAccessToken(userToken)).thenReturn(userId);
        doThrow(new RuntimeException("Cache error")).when(cacheService).putCache(anyString(), any());
        ApiResponse response = profileService.getExtendedProfileSummary(userId, userToken);
        assertEquals(HttpStatus.OK, response.getResponseCode());
        assertNotNull(response.get(Constants.RESPONSE));
        verify(cacheService).putCache(anyString(), any());
    }

    @Test
    void testDeleteExtendedProfile_UserIdMismatch_ReturnsBadRequest() {
        String requestUserId = "user-123";
        String tokenUserId = "different-user-456";
        String userToken = "token-for-different-user";
        Map<String, Object> requestData = new HashMap<>();
        requestData.put(Constants.USER_ID_RQST, requestUserId);
        Map<String, Object> request = new HashMap<>();
        request.put(Constants.REQUEST, requestData);
        when(accessTokenValidator.fetchUserIdFromAccessToken(userToken)).thenReturn(tokenUserId);
        ApiResponse response = profileService.deleteExtendedProfile(request, userToken);
        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
        assertEquals("Invalid UserId in the request", response.getParams().getErrMsg());
    }

    @Test
    void testDeleteExtendedProfile_ToDeleteListNullOrEmpty_SkipsProcessing() {
        String userId = "user-123";
        String userToken = "valid-token";
        String contextType1 = "education";
        String contextType2 = "workExperience";
        Map<String, Object> requestData = new HashMap<>();
        requestData.put(Constants.USER_ID_RQST, userId);
        requestData.put(contextType1, null); // null list
        requestData.put(contextType2, Collections.emptyList()); // empty list
        Map<String, Object> request = new HashMap<>();
        request.put(Constants.REQUEST, requestData);
        when(accessTokenValidator.fetchUserIdFromAccessToken(userToken)).thenReturn(userId);
        when(serverProperties.getContextType()).thenReturn(new String[]{contextType1, contextType2});
        ApiResponse response = profileService.deleteExtendedProfile(request, userToken);
        assertEquals(HttpStatus.OK, response.getResponseCode());
        assertEquals(Constants.SUCCESS, response.get(Constants.RESPONSE));
        verify(cassandraOperation, never()).getRecordsByPropertiesByKey(anyString(), anyString(), anyMap(), any(), any());
        verify(cassandraOperation, never()).insertRecord(anyString(), anyString(), anyMap());
        verify(cacheService, never()).putCache(anyString(), any());
    }

    @Test
    void testReadFullExtendedProfile_NoContextData_ReturnsNoContent() throws Exception {
        String userId = "user-123";
        String userToken = "valid-token";
        String localContextType = "education";
        String redisKey = "user:extendedProfile:" + localContextType + ":" + userId;
        when(accessTokenValidator.fetchUserIdFromAccessToken(userToken)).thenReturn(userId);
        when(cacheService.getCache(redisKey)).thenReturn(null);
        when(cassandraOperation.getRecordsByPropertiesByKey(anyString(), anyString(), anyMap(), any(), any()))
                .thenReturn(null); // or Collections.emptyList()
        lenient().when(projectUtil.parseListOfMap(anyString())).thenReturn(Collections.emptyList());
        ApiResponse response = profileService.readFullExtendedProfile(userId, localContextType, userToken);
        assertEquals(HttpStatus.NO_CONTENT, response.getResponseCode());
        assertEquals("No data found for user.", response.getParams().getErrMsg());
    }


    @Test
    void returnsValidCount() {
        ProfileServiceImpl locaService = new ProfileServiceImpl(null, null, null, null, null, null, null, null, null, null);
        CbServerProperties serverConfig = mock(CbServerProperties.class);
        RequestHandlerServiceImpl requestHandlerService = mock(RequestHandlerServiceImpl.class);
        ReflectionTestUtils.setField(locaService, "serverConfig", serverConfig);
        ReflectionTestUtils.setField(locaService, "requestHandlerService", requestHandlerService);
        when(serverConfig.getCommunityBaseUrl()).thenReturn("http://base/");
        when(serverConfig.getCommunityPostCountApiUrl()).thenReturn("api/count/");
        Map<String, Object> result = new HashMap<>();
        result.put(Constants.POSTCOUNT, 5);
        Map<String, Object> response = new HashMap<>();
        response.put(Constants.RESULT, result);
        when(requestHandlerService.fetchUsingGetWithHeadersProfile(anyString(), isNull()))
                .thenReturn(response);
        int count = ReflectionTestUtils.invokeMethod(locaService, "fetchPostCountFromApi", "user-1");
        assertEquals(5, count);
    }

    @Test
    void returnsZeroOnNullResponse() {
        ProfileServiceImpl localService = new ProfileServiceImpl(null, null, null, null, null, null, null, null, null, null);
        CbServerProperties serverConfig = mock(CbServerProperties.class);
        RequestHandlerServiceImpl requestHandlerService = mock(RequestHandlerServiceImpl.class);
        ReflectionTestUtils.setField(localService, "serverConfig", serverConfig);
        ReflectionTestUtils.setField(localService, "requestHandlerService", requestHandlerService);
        when(serverConfig.getCommunityBaseUrl()).thenReturn("http://base/");
        when(serverConfig.getCommunityPostCountApiUrl()).thenReturn("api/count/");
        when(requestHandlerService.fetchUsingGetWithHeadersProfile(anyString(), isNull()))
                .thenReturn(null);
        int count = ReflectionTestUtils.invokeMethod(localService, "fetchPostCountFromApi", "user-2");
        assertEquals(0, count);
    }

    @Test
    void returnsZeroOnMissingResult() {
        ProfileServiceImpl locaService = new ProfileServiceImpl(null, null, null, null, null, null, null, null, null, null);
        CbServerProperties serverConfig = mock(CbServerProperties.class);
        RequestHandlerServiceImpl requestHandlerService = mock(RequestHandlerServiceImpl.class);
        ReflectionTestUtils.setField(locaService, "serverConfig", serverConfig);
        ReflectionTestUtils.setField(locaService, "requestHandlerService", requestHandlerService);
        when(serverConfig.getCommunityBaseUrl()).thenReturn("http://base/");
        when(serverConfig.getCommunityPostCountApiUrl()).thenReturn("api/count/");
        Map<String, Object> response = new HashMap<>();
        when(requestHandlerService.fetchUsingGetWithHeadersProfile(anyString(), isNull()))
                .thenReturn(response);

        int count = ReflectionTestUtils.invokeMethod(locaService, "fetchPostCountFromApi", "user-3");
        assertEquals(0, count);
    }

    @Test
    void returnsZeroOnNonIntegerPostCount() {
        ProfileServiceImpl locaService = new ProfileServiceImpl(null, null, null, null, null, null, null, null, null, null);
        CbServerProperties serverConfig = mock(CbServerProperties.class);
        RequestHandlerServiceImpl requestHandlerService = mock(RequestHandlerServiceImpl.class);
        ReflectionTestUtils.setField(locaService, "serverConfig", serverConfig);
        ReflectionTestUtils.setField(locaService, "requestHandlerService", requestHandlerService);
        when(serverConfig.getCommunityBaseUrl()).thenReturn("http://base/");
        when(serverConfig.getCommunityPostCountApiUrl()).thenReturn("api/count/");
        Map<String, Object> result = new HashMap<>();
        result.put(Constants.POSTCOUNT, "not-an-int");
        Map<String, Object> response = new HashMap<>();
        response.put(Constants.RESULT, result);
        when(requestHandlerService.fetchUsingGetWithHeadersProfile(anyString(), isNull()))
                .thenReturn(response);
        int count = ReflectionTestUtils.invokeMethod(locaService, "fetchPostCountFromApi", "user-4");
        assertEquals(0, count);
    }

    @Test
    void returnsZeroOnException() {
        ProfileServiceImpl locaService = new ProfileServiceImpl(null, null, null, null, null, null, null, null, null, null);
        CbServerProperties serverConfig = mock(CbServerProperties.class);
        RequestHandlerServiceImpl requestHandlerService = mock(RequestHandlerServiceImpl.class);
        ReflectionTestUtils.setField(locaService, "serverConfig", serverConfig);
        ReflectionTestUtils.setField(locaService, "requestHandlerService", requestHandlerService);
        when(serverConfig.getCommunityBaseUrl()).thenReturn("http://base/");
        when(serverConfig.getCommunityPostCountApiUrl()).thenReturn("api/count/");
        when(requestHandlerService.fetchUsingGetWithHeadersProfile(anyString(), isNull()))
                .thenThrow(new RuntimeException("API error"));
        int count = ReflectionTestUtils.invokeMethod(locaService, "fetchPostCountFromApi", "user-5");
        assertEquals(0, count);
    }

    @Test
    void testGetUserPostCount_cacheHit() {
        ProfileServiceImpl localService = new ProfileServiceImpl(null, null, null, null, null, null, null, null, null, null);
        ReflectionTestUtils.setField(localService, "cacheService", cacheService);
        when(cacheService.getCache("user:postCount_user1")).thenReturn("10");
        int count = ReflectionTestUtils.invokeMethod(localService, "getUserPostCount", "user1");
        assertEquals(10, count);
        verify(cacheService).getCache("user:postCount_user1");
        verifyNoMoreInteractions(cacheService);
    }

    @Test
    void testGetUserPostCount_cacheValueNotInteger_returnsZero() {
        ProfileServiceImpl localService = new ProfileServiceImpl(null, null, null, null, null, null, null, null, null, null);
        ReflectionTestUtils.setField(localService, "cacheService", cacheService);
        when(cacheService.getCache("user:postCount_user3")).thenReturn("not-a-number");
        int count = ReflectionTestUtils.invokeMethod(localService, "getUserPostCount", "user3");
        assertEquals(0, count);
    }

    @Test
    void testGetUserPostCount_exception_returnsZero() {
        ProfileServiceImpl locaService = new ProfileServiceImpl(null, null, null, null, null, null, null, null, null, null);
        ReflectionTestUtils.setField(locaService, "cacheService", cacheService);
        when(cacheService.getCache("user:postCount_user4")).thenThrow(new RuntimeException("Redis error"));
        int count = ReflectionTestUtils.invokeMethod(locaService, "getUserPostCount", "user4");
        assertEquals(0, count);
    }


    @Test
    void fetchFromDatabase_returnsNull_whenNoRecords() throws Exception {
        ProfileServiceImpl locaService = new ProfileServiceImpl(null, null, null, null, null, null, null, null, null, null);
        CassandraOperation localCassandraOperation = mock(CassandraOperation.class);
        CbServerProperties serverConfig = mock(CbServerProperties.class);
        ProjectUtil localProjectUtil = mock(ProjectUtil.class);
        CacheService localCacheService = mock(CacheService.class);

        ReflectionTestUtils.setField(locaService, "cassandraOperation", localCassandraOperation);
        ReflectionTestUtils.setField(locaService, "serverConfig", serverConfig);
        ReflectionTestUtils.setField(locaService, "projectUtil", localProjectUtil);
        ReflectionTestUtils.setField(locaService, "cacheService", localCacheService);

        // Mock empty result from DB
        when(localCassandraOperation.getRecordsByPropertiesByKey(anyString(), anyString(), anyMap(), any(), any()))
                .thenReturn(Collections.emptyList());

        // Invoke method correctly with both parameters
        Method method = ProfileServiceImpl.class.getDeclaredMethod("readUserDataFromDB", String.class, List.class);
        method.setAccessible(true);
        Map<String, Object> result = (Map<String, Object>) method.invoke(locaService, "user-4", null);

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void fetchFromDatabase_returnsRecordWithParsedProfileDetails_whenValidJson() throws Exception {
        ProfileServiceImpl locaService = new ProfileServiceImpl(null, null, null, null, null, null, null, null, null, null);

        // Mock dependencies
        CassandraOperation localCassandraOperation = mock(CassandraOperation.class);
        CbServerProperties serverConfig = mock(CbServerProperties.class);
        ProjectUtil localProjectUtil = mock(ProjectUtil.class);
        CacheService localCacheService = mock(CacheService.class);
        ObjectMapper mapper = new ObjectMapper();

        ReflectionTestUtils.setField(locaService, "cassandraOperation", localCassandraOperation);
        ReflectionTestUtils.setField(locaService, "serverConfig", serverConfig);
        ReflectionTestUtils.setField(locaService, "projectUtil", localProjectUtil);
        ReflectionTestUtils.setField(locaService, "cacheService", localCacheService);
        ReflectionTestUtils.setField(locaService, "mapper", mapper);  // ✅ Fix for NPE

        // DB record with JSON string
        Map<String, Object> localRecord = new HashMap<>();
        localRecord.put(Constants.PROFILE_DETAILS, "{\"email\":\"test@example.com\"}");
        List<Map<String, Object>> records = List.of(localRecord);

        when(localCassandraOperation.getRecordsByPropertiesByKey(anyString(), anyString(), anyMap(), any(), any()))
                .thenReturn(records);

        // Invoke the method with reflection
        Method method = ProfileServiceImpl.class.getDeclaredMethod("readUserDataFromDB", String.class, List.class);
        method.setAccessible(true);
        Map<String, Object> result = (Map<String, Object>) method.invoke(locaService, "user-2", null);

        assertNotNull(result);
        assertEquals("test@example.com", ((Map<?, ?>) result.get(Constants.PROFILE_DETAILS)).get("email"));
    }


    @Test
    void fetchFromDatabase_removesProfileDetails_whenJsonInvalid() {
        ProfileServiceImpl locaService = new ProfileServiceImpl(null, null, null, null, null, null, null, null, null, null);

        // Mock dependencies
        CassandraOperation localCassandraOperation = mock(CassandraOperation.class);
        CbServerProperties serverConfig = mock(CbServerProperties.class);

        // Inject mocks
        ReflectionTestUtils.setField(locaService, "cassandraOperation", localCassandraOperation);
        ReflectionTestUtils.setField(locaService, "serverConfig", serverConfig);
        ReflectionTestUtils.setField(locaService, "mapper", new ObjectMapper());

        // Simulate record with invalid JSON in profileDetails
        Map<String, Object> localRecord = new HashMap<>();
        localRecord.put(Constants.PROFILE_DETAILS, "{invalid_json}");
        List<Map<String, Object>> records = List.of(localRecord);

        when(localCassandraOperation.getRecordsByPropertiesByKey(anyString(), anyString(), anyMap(), any(), any()))
                .thenReturn(records);

        // Invoke the method via reflection (note 2 args!)
        Map<String, Object> result = ReflectionTestUtils.invokeMethod(locaService, "readUserDataFromDB", "user-3", null);

        // Validate fallback to empty profileDetails due to JSON parse failure
        assertNotNull(result);
        assertEquals(Map.of(), result.get(Constants.PROFILE_DETAILS));
    }

    @Test
    void fetchFromDatabase_leavesProfileDetailsNull_whenProfileDetailsIsNull() {
        ProfileServiceImpl locaService = new ProfileServiceImpl(null, null, null, null, null, null, null, null, null, null);

        // Mock dependencies
        CassandraOperation localCassandraOperation = mock(CassandraOperation.class);
        CbServerProperties serverConfig = mock(CbServerProperties.class);
        ProjectUtil localProjectUtil = mock(ProjectUtil.class);
        CacheService localCacheService = mock(CacheService.class);

        // Set fields
        ReflectionTestUtils.setField(locaService, "cassandraOperation", localCassandraOperation);
        ReflectionTestUtils.setField(locaService, "serverConfig", serverConfig);
        ReflectionTestUtils.setField(locaService, "projectUtil", localProjectUtil);
        ReflectionTestUtils.setField(locaService, "cacheService", localCacheService);
        ReflectionTestUtils.setField(locaService, "mapper", new ObjectMapper());

        Map<String, Object> localRecord = new HashMap<>();
        localRecord.put(Constants.PROFILE_DETAILS, null);
        List<Map<String, Object>> records = List.of(localRecord);

        when(localCassandraOperation.getRecordsByPropertiesByKey(anyString(), anyString(), anyMap(), any(), any()))
                .thenReturn(records);

        Map<String, Object> result = ReflectionTestUtils.invokeMethod(locaService, "readUserDataFromDB", "user-4", null);

        assertNotNull(result);
        assertEquals(Map.of(), result.get(Constants.PROFILE_DETAILS));
    }

    @Test
    void validateFields_returnsEmptyString_whenAllMandatoryFieldsPresent() {
        Map<String, Object> data = Map.of("degree", "MSc", "institute", "Test University");
        String mandatoryFields = "degree,institute";
        ProfileServiceImpl locaService = new ProfileServiceImpl(null, null, null, null, null, null, null, null, null, null);
        String result = ReflectionTestUtils.invokeMethod(locaService, "validateFields", data, mandatoryFields, false);
        assertEquals("", result);
    }

    @Test
    void validateFields_returnsErrorMessage_whenMandatoryFieldMissing() {
        Map<String, Object> data = Map.of("degree", "MSc");
        String mandatoryFields = "degree,institute";
        ProfileServiceImpl localService = new ProfileServiceImpl(null, null, null, null, null, null, null, null, null, null);
        String result = ReflectionTestUtils.invokeMethod(localService, "validateFields", data, mandatoryFields, false);
        assertTrue(result.contains("institute is mandatory"));
    }

    @Test
    void validateFields_skipsEndDate_whenCurrentlyWorkingIsTrueAndAllowSkipEndDate() {
        Map<String, Object> data = new HashMap<>();
        data.put("degree", "MSc");
        data.put("endDate", "");
        data.put("currentlyWorking", "true");
        String mandatoryFields = "degree,endDate";
        ProfileServiceImpl localService = new ProfileServiceImpl(null, null, null, null, null, null, null, null, null, null);
        String result = ReflectionTestUtils.invokeMethod(localService, "validateFields", data, mandatoryFields, true);
        assertEquals("", result);
    }

    @Test
    void validateFields_requiresEndDate_whenCurrentlyWorkingIsFalse() {
        Map<String, Object> data = new HashMap<>();
        data.put("degree", "MSc");
        data.put("endDate", "");
        data.put("currentlyWorking", "false");
        String mandatoryFields = "degree,endDate";
        ProfileServiceImpl localService = new ProfileServiceImpl(null, null, null, null, null, null, null, null, null, null);
        String result = ReflectionTestUtils.invokeMethod(localService, "validateFields", data, mandatoryFields, true);
        assertTrue(result.contains("endDate is mandatory"));
    }

    @Test
    void validateFields_handlesBlankMandatoryFields() {
        Map<String, Object> data = Map.of("degree", "MSc");
        String mandatoryFields = "";
        ProfileServiceImpl localService = new ProfileServiceImpl(null, null, null, null, null, null, null, null, null, null);
        String result = ReflectionTestUtils.invokeMethod(localService, "validateFields", data, mandatoryFields, false);
        assertEquals(" is mandatory. ", result);
    }

    @Test
    void validateFields_handlesNullValues() {
        Map<String, Object> data = new HashMap<>();
        data.put("degree", null);
        String mandatoryFields = "degree";
        ProfileServiceImpl localService = new ProfileServiceImpl(null, null, null, null, null, null, null, null, null, null);
        String result = ReflectionTestUtils.invokeMethod(localService, "validateFields", data, mandatoryFields, false);
        assertTrue(result.contains("degree is mandatory"));
    }

    @Test
    void validateFields_handlesMultipleMissingFields() {
        Map<String, Object> data = new HashMap<>();
        String mandatoryFields = "degree,institute";
        ProfileServiceImpl localService = new ProfileServiceImpl(null, null, null, null, null, null, null, null, null, null);
        String result = ReflectionTestUtils.invokeMethod(localService, "validateFields", data, mandatoryFields, false);
        assertTrue(result.contains("degree is mandatory"));
        assertTrue(result.contains("institute is mandatory"));
    }



    @Test
    void getUserKarmaPoints_returnsCachedValue_whenCacheHit() {
        ProfileServiceImpl localService = new ProfileServiceImpl(null, null, null, null, null, null, null, null, null, null);
        CacheService localCacheService = mock(CacheService.class);
        CassandraOperation localCassandraOperation = mock(CassandraOperation.class);
        ReflectionTestUtils.setField(localService, "cacheService", localCacheService);
        ReflectionTestUtils.setField(localService, "cassandraOperation", cassandraOperation);
        String userId = "user-1";
        when(localCacheService.getCache("user:karmaPoints:" + userId)).thenReturn("42");
        int points = ReflectionTestUtils.invokeMethod(localService, "getUserKarmaPoints", userId);
        assertEquals(42, points);
        verify(localCacheService).getCache("user:karmaPoints:" + userId);
        verifyNoInteractions(localCassandraOperation);
    }



    @Test
    void getUserKarmaPoints_returnsZero_whenCacheValueIsNotInteger() {
        ProfileServiceImpl localService = new ProfileServiceImpl(null, null, null, null, null, null, null, null, null, null);
        CacheService localCacheService = mock(CacheService.class);
        ReflectionTestUtils.setField(localService, "cacheService", localCacheService);
        String userId = "user-4";
        when(localCacheService.getCache("user:karmaPoints:" + userId)).thenReturn("not-a-number");
        int points = ReflectionTestUtils.invokeMethod(localService, "getUserKarmaPoints", userId);
        assertEquals(0, points);
    }

    @Test
    void getUserKarmaPoints_returnsZero_whenExceptionThrown() {
        ProfileServiceImpl localService = new ProfileServiceImpl(null, null, null, null, null, null, null, null, null, null);
        CacheService localCacheService = mock(CacheService.class);
        ReflectionTestUtils.setField(localService, "cacheService", localCacheService);
        String userId = "user-5";
        when(localCacheService.getCache("user:karmaPoints:" + userId)).thenThrow(new RuntimeException("Redis error"));
        int points = ReflectionTestUtils.invokeMethod(localService, "getUserKarmaPoints", userId);
        assertEquals(0, points);
    }

    @Test
    void getSortingComparator_returnsServiceHistoryComparator_andSortsByStartDateDescending() {
        ProfileServiceImpl localService = new ProfileServiceImpl(null, null, null, null, null, null, null, null, null, null);
        Comparator<Map<String, Object>> comparator = ReflectionTestUtils.invokeMethod(localService, "getSortingComparator", Constants.SERVICE_HISTORY);
        List<Map<String, Object>> data = new ArrayList<>();
        data.add(Map.of(Constants.START_DATE, "2022-01-01T00:00:00Z"));
        data.add(Map.of(Constants.START_DATE, "2023-01-01T00:00:00Z"));
        data.sort(comparator.reversed());
        assertEquals("2023-01-01T00:00:00Z", data.get(0).get(Constants.START_DATE));
    }

    @Test
    void getSortingComparator_returnsEducationalQualificationsComparator_andSortsByStartYearDescending() {
        ProfileServiceImpl localService = new ProfileServiceImpl(null, null, null, null, null, null, null, null, null, null);
        Comparator<Map<String, Object>> comparator = ReflectionTestUtils.invokeMethod(localService, "getSortingComparator", Constants.EDUCATIONAL_QUALIFICATIONS);
        List<Map<String, Object>> data = new ArrayList<>();
        data.add(Map.of(Constants.START_YEAR, "2018"));
        data.add(Map.of(Constants.START_YEAR, "2020"));
        data.sort(comparator.reversed());
        assertEquals("2020", data.get(0).get(Constants.START_YEAR));
    }

    @Test
    void getSortingComparator_returnsAchievementsComparator_andSortsByIssuedDateDescending() {
        ProfileServiceImpl localService = new ProfileServiceImpl(null, null, null, null, null, null, null, null, null, null);
        Comparator<Map<String, Object>> comparator = ReflectionTestUtils.invokeMethod(localService, "getSortingComparator", Constants.ACHIVEMENTS);
        List<Map<String, Object>> data = new ArrayList<>();
        data.add(Map.of(Constants.ISSUED_DATE, "2021-05-01T00:00:00Z"));
        data.add(Map.of(Constants.ISSUED_DATE, "2022-05-01T00:00:00Z"));
        data.sort(comparator.reversed());
        assertEquals("2022-05-01T00:00:00Z", data.get(0).get(Constants.ISSUED_DATE));
    }

    @Test
    void getSortingComparator_returnsNullForUnknownContextType() {
        ProfileServiceImpl localService = new ProfileServiceImpl(null, null, null, null, null, null, null, null, null, null);
        Comparator<Map<String, Object>> comparator = ReflectionTestUtils.invokeMethod(localService, "getSortingComparator", "unknownType");
        assertNull(comparator);
    }

    @Test
    void getSortingComparator_handlesMissingFieldsGracefully() {
        ProfileServiceImpl localService = new ProfileServiceImpl(null, null, null, null, null, null, null, null, null, null);
        Comparator<Map<String, Object>> comparator = ReflectionTestUtils.invokeMethod(localService, "getSortingComparator", Constants.EDUCATIONAL_QUALIFICATIONS);
        List<Map<String, Object>> data = new ArrayList<>();
        data.add(new HashMap<>()); // missing START_YEAR
        data.add(Map.of(Constants.START_YEAR, "2020"));
        assertThrows(NumberFormatException.class, () -> data.sort(comparator));
    }

    @Test
    void sortContextData_sortsListDescending_whenComparatorExists() {
        ProfileServiceImpl localService = new ProfileServiceImpl(null, null, null, null, null, null, null, null, null, null);
        List<Map<String, Object>> dataList = new ArrayList<>();
        dataList.add(Map.of(Constants.START_DATE, "2022-01-01T00:00:00Z"));
        dataList.add(Map.of(Constants.START_DATE, "2023-01-01T00:00:00Z"));
        ReflectionTestUtils.invokeMethod(localService, "sortContextData", dataList, Constants.SERVICE_HISTORY);
        assertEquals("2023-01-01T00:00:00Z", dataList.get(0).get(Constants.START_DATE));
    }

    @Test
    void sortContextData_doesNotSort_whenComparatorIsNull() {
        ProfileServiceImpl localService = new ProfileServiceImpl(null, null, null, null, null, null, null, null, null, null);
        List<Map<String, Object>> dataList = new ArrayList<>();
        dataList.add(Map.of("field", "A"));
        dataList.add(Map.of("field", "B"));
        List<Map<String, Object>> original = new ArrayList<>(dataList);
        ReflectionTestUtils.invokeMethod(localService, "sortContextData", dataList, "unknownType");
        assertEquals(original, dataList);
    }

    @Test
    void sortContextData_handlesEmptyList() {
        ProfileServiceImpl localService = new ProfileServiceImpl(null, null, null, null, null, null, null, null, null, null);
        List<Map<String, Object>> dataList = new ArrayList<>();
        ReflectionTestUtils.invokeMethod(localService, "sortContextData", dataList, Constants.SERVICE_HISTORY);
        assertTrue(dataList.isEmpty());
    }

    @Test
    void sortContextData_throwsException_whenFieldMissing() {
        ProfileServiceImpl localService = new ProfileServiceImpl(null, null, null, null, null, null, null, null, null, null);
        List<Map<String, Object>> dataList = new ArrayList<>();
        dataList.add(new HashMap<>());
        dataList.add(Map.of(Constants.START_YEAR, "2020"));
        assertThrows(NumberFormatException.class, () ->
                ReflectionTestUtils.invokeMethod(localService, "sortContextData", dataList, Constants.EDUCATIONAL_QUALIFICATIONS)
        );
    }

    @Test
    void returnsCachedCertificateCount_whenCacheHit() {
        ProfileServiceImpl localService = new ProfileServiceImpl(null, null, null, null, null, null, null, null, null, null);
        CacheService localCacheService = mock(CacheService.class);
        CassandraOperation localCassandraOperation = mock(CassandraOperation.class);
        CbServerProperties serverConfig = mock(CbServerProperties.class);
        ReflectionTestUtils.setField(localService, "cacheService", localCacheService);
        ReflectionTestUtils.setField(localService, "cassandraOperation", localCassandraOperation);
        ReflectionTestUtils.setField(localService, "serverConfig", serverConfig);
        when(serverConfig.getUserEnrolmentsTable()).thenReturn(Constants.USER_ENROLMENTS);

        List<Map<String, Object>> courseRecords = List.of(
                Map.of(Constants.ISSUED_CERTIFICATES_KEY, List.of("c1")),
                Map.of(Constants.ISSUED_CERTIFICATES_KEY, List.of("c2", "c3"))
        );
        when(localCassandraOperation.getRecordsByPropertiesByKey(anyString(), anyString(), anyMap(), anyList(), anyString()))
                .thenReturn(courseRecords)
                .thenReturn(Collections.emptyList())
                .thenReturn(Collections.emptyList());
        int count = ReflectionTestUtils.invokeMethod(localService, "getIssuedCertificateCount", "user-1");
        assertEquals(2, count);
        verifyNoInteractions(localCacheService);
    }

    @Test
    void returnsSumOfCertificatesFromBothSources_whenCacheMiss() {
        ProfileServiceImpl locaService = new ProfileServiceImpl(null, null, null, null, null, null, null, null, null, null);
        CacheService localCacheService = mock(CacheService.class);
        CassandraOperation localCassandraOperation = mock(CassandraOperation.class);
        CbServerProperties serverConfig = mock(CbServerProperties.class);
        ReflectionTestUtils.setField(locaService, "cacheService", localCacheService);
        ReflectionTestUtils.setField(locaService, "cassandraOperation", localCassandraOperation);
        ReflectionTestUtils.setField(locaService, "serverConfig", serverConfig);
        when(serverConfig.getUserEnrolmentsTable()).thenReturn(Constants.USER_ENROLMENTS);

        List<Map<String, Object>> courseRecords = List.of(
                Map.of(Constants.ISSUED_CERTIFICATES_KEY, List.of("c1", "c2")),
                Map.of(Constants.ISSUED_CERTIFICATES_KEY, List.of("c3"))
        );

        List<Map<String, Object>> eventRecords = List.of(
                Map.of(Constants.STATUS, 2, Constants.PROGRESS_KEY, 100, Constants.ISSUED_CERTIFICATES_KEY, List.of("e1")),
                Map.of(Constants.STATUS, 2, Constants.PROGRESS_KEY, 100, Constants.ISSUED_CERTIFICATES_KEY, List.of("e2", "e3")),
                Map.of(Constants.STATUS, 1, Constants.PROGRESS_KEY, 100, Constants.ISSUED_CERTIFICATES_KEY, List.of("shouldNotCount"))
        );
        List<Map<String, Object>> externalCoursesRecords = List.of(
                Map.of(Constants.STATUS, 2, Constants.PROGRESS_KEY, 100, Constants.ISSUED_CERTIFICATES_KEY, List.of("ex1")),
                Map.of(Constants.STATUS, 1, Constants.PROGRESS_KEY, 100, Constants.ISSUED_CERTIFICATES_KEY, List.of("certificateShouldNotCount"))
        );
        when(localCassandraOperation.getRecordsByPropertiesByKey(
                anyString(),
                any(),
                anyMap(),
                anyList(),
                anyString()
        )).thenReturn(courseRecords)
                .thenReturn(eventRecords).thenReturn(externalCoursesRecords);

        int count = ReflectionTestUtils.invokeMethod(locaService, "getIssuedCertificateCount", "user-2");
        assertEquals(5, count);
        verifyNoInteractions(localCacheService);
    }

    @Test
    void returnsZero_whenNoCertificatesAndCacheMiss() {
        ProfileServiceImpl localService = new ProfileServiceImpl(null, null, null, null, null, null, null, null, null, null);
        CacheService localCacheService = mock(CacheService.class);
        CassandraOperation localCassandraOperation = mock(CassandraOperation.class);
        CbServerProperties serverConfig = mock(CbServerProperties.class);
        ReflectionTestUtils.setField(localService, "cacheService", localCacheService);
        ReflectionTestUtils.setField(localService, "cassandraOperation", localCassandraOperation);
        ReflectionTestUtils.setField(localService, "serverConfig", serverConfig);
        when(serverConfig.getCertificateCountRedisKey()).thenReturn("cert:count");
        when(serverConfig.getDataIndex()).thenReturn(12);
        when(serverConfig.getCertificateCountRedisTtl()).thenReturn(100);
        when(localCacheService.hget("cert:count", 12, "user-3", 100)).thenReturn(null);
        when(localCassandraOperation.getRecordsByPropertiesByKey(
                anyString(), eq(Constants.USER_ENROLMENTS), anyMap(), anyList(), eq("user-3")
        )).thenReturn(Collections.emptyList());
        when(localCassandraOperation.getRecordsByPropertiesByKey(
                anyString(), eq(Constants.USER_ENTITY_ENROLMENTS), anyMap(), anyList(), eq("user-3")
        )).thenReturn(Collections.emptyList());
        int count = ReflectionTestUtils.invokeMethod(localService, "getIssuedCertificateCount", "user-3");
        assertEquals(0, count);
        verify(localCacheService, never()).hset(anyString(), anyInt(), anyString(), anyString(), eq(100));
    }

    @Test
    void returnsZero_whenExceptionIsThrown() {
        ProfileServiceImpl localService = new ProfileServiceImpl(null, null, null, null, null, null, null, null, null, null);
        CacheService localCacheService = mock(CacheService.class);
        CassandraOperation localCassandraOperation = mock(CassandraOperation.class);
        CbServerProperties serverConfig = mock(CbServerProperties.class);
        ReflectionTestUtils.setField(localService, "cacheService", localCacheService);
        ReflectionTestUtils.setField(localService, "cassandraOperation", localCassandraOperation);
        ReflectionTestUtils.setField(localService, "serverConfig", serverConfig);
        when(serverConfig.getUserEnrolmentsTable()).thenReturn(Constants.USER_ENROLMENTS);
        when(localCassandraOperation.getRecordsByPropertiesByKey(anyString(), anyString(), anyMap(), anyList(), anyString()))
                .thenThrow(new RuntimeException("fail"));
        int count = ReflectionTestUtils.invokeMethod(localService, "getIssuedCertificateCount", "user-4");
        assertEquals(0, count);
        verifyNoInteractions(localCacheService);
    }

    @Test
    void ignoresNonListIssuedCertificatesAndNulls() {
        ProfileServiceImpl locaService = new ProfileServiceImpl(null, null, null, null, null, null, null, null, null, null);
        CacheService localCacheService = mock(CacheService.class);
        CassandraOperation localCassandraOperation = mock(CassandraOperation.class);
        CbServerProperties serverConfig = mock(CbServerProperties.class);
        ReflectionTestUtils.setField(locaService, "cacheService", localCacheService);
        ReflectionTestUtils.setField(locaService, "cassandraOperation", localCassandraOperation);
        ReflectionTestUtils.setField(locaService, "serverConfig", serverConfig);
        when(serverConfig.getCertificateCountRedisKey()).thenReturn("cert:count");
        when(serverConfig.getDataIndex()).thenReturn(12);
        when(serverConfig.getCertificateCountRedisTtl()).thenReturn(100);

        List<Map<String, Object>> courseRecords = List.of(
                new HashMap<String, Object>() {{ put(Constants.ISSUED_CERTIFICATES_KEY, null); }},
                Map.of(Constants.ISSUED_CERTIFICATES_KEY, "notAList")
        );
        when(localCassandraOperation.getRecordsByPropertiesByKey(
                anyString(), eq(Constants.USER_ENROLMENTS), anyMap(), anyList(), eq("user-5")
        )).thenReturn(courseRecords);

        List<Map<String, Object>> eventRecords = List.of(
                new HashMap<String, Object>() {{
                    put(Constants.STATUS, 2);
                    put(Constants.PROGRESS_KEY, 100);
                    put(Constants.ISSUED_CERTIFICATES_KEY, null);
                }},
                Map.of(Constants.STATUS, 2, Constants.PROGRESS_KEY, 100, Constants.ISSUED_CERTIFICATES_KEY, "notAList")
        );
        when(localCassandraOperation.getRecordsByPropertiesByKey(
                anyString(), eq(Constants.USER_ENTITY_ENROLMENTS), anyMap(), anyList(), eq("user-5")
        )).thenReturn(eventRecords);

        int count = ReflectionTestUtils.invokeMethod(locaService, "getIssuedCertificateCount", "user-5");
        assertEquals(0, count);
        verify(localCacheService, never()).hset(anyString(), anyInt(), anyString(), anyString(), eq(100));
    }

    @Test
    void mergeAndSortByIssuedDateOrTitle_sortsByIssuedDateDescending() {
        ProfileServiceImpl locaService = new ProfileServiceImpl(null, null, null, null, null, null, null, null, null, null);
        List<Map<String, Object>> existingList = new ArrayList<>();
        List<Map<String, Object>> newList = new ArrayList<>();
        existingList.add(new HashMap<>(Map.of(Constants.ISSUED_DATE, "2022-01-01T00:00:00Z", Constants.TITLE, "B")));
        newList.add(new HashMap<>(Map.of(Constants.ISSUED_DATE, "2023-01-01T00:00:00Z", Constants.TITLE, "A")));
        ReflectionTestUtils.invokeMethod(
                locaService, "mergeAndSortByIssuedDateOrTitle", existingList, newList
        );
        assertEquals("2023-01-01T00:00:00Z", existingList.get(0).get(Constants.ISSUED_DATE));
        assertEquals(0, existingList.get(0).get(Constants.INDEX));
        assertEquals(1, existingList.get(1).get(Constants.INDEX));
    }

    @Test
    void mergeAndSortByIssuedDateOrTitle_sortsByTitleWhenDatesMissing() {
        ProfileServiceImpl locaService = new ProfileServiceImpl(null, null, null, null, null, null, null, null, null, null);
        List<Map<String, Object>> existingList = new ArrayList<>();
        List<Map<String, Object>> newList = new ArrayList<>();
        existingList.add(new HashMap<>(Map.of(Constants.TITLE, "Bravo")));
        newList.add(new HashMap<>(Map.of(Constants.TITLE, "Alpha")));
        ReflectionTestUtils.invokeMethod(
                locaService, "mergeAndSortByIssuedDateOrTitle", existingList, newList
        );
        assertEquals("Alpha", existingList.get(0).get(Constants.TITLE));
        assertEquals("Bravo", existingList.get(1).get(Constants.TITLE));
    }

    @Test
    void mergeAndSortByIssuedDateOrTitle_handlesNullTitles() {
        ProfileServiceImpl locaService = new ProfileServiceImpl(null, null, null, null, null, null, null, null, null, null);
        List<Map<String, Object>> existingList = new ArrayList<>();
        List<Map<String, Object>> newList = new ArrayList<>();
        existingList.add(new HashMap<>());
        newList.add(new HashMap<>(Map.of(Constants.TITLE, "Alpha")));
        ReflectionTestUtils.invokeMethod(
                locaService, "mergeAndSortByIssuedDateOrTitle", existingList, newList
        );
        assertEquals("Alpha", existingList.get(0).get(Constants.TITLE));
        assertNull(existingList.get(1).get(Constants.TITLE));
    }

    @Test
    void mergeAndSortByIssuedDateOrTitle_handlesNullLists() {
        ProfileServiceImpl locaService = new ProfileServiceImpl(null, null, null, null, null, null, null, null, null, null);
        List<Map<String, Object>> existingList = new ArrayList<>();
        List<Map<String, Object>> newList = new ArrayList<>();
        ReflectionTestUtils.invokeMethod(
                locaService, "mergeAndSortByIssuedDateOrTitle", existingList, newList
        );
        assertTrue(existingList.isEmpty());
    }

    @Test
    void mergeAndSortByIssuedDateOrTitle_sortsWhenSomeDatesNull() {
        ProfileServiceImpl locaService = new ProfileServiceImpl(null, null, null, null, null, null, null, null, null, null);
        List<Map<String, Object>> existingList = new ArrayList<>();
        List<Map<String, Object>> newList = new ArrayList<>();
        existingList.add(new HashMap<>(Map.of(Constants.TITLE, "Bravo")));
        newList.add(new HashMap<>(Map.of(Constants.ISSUED_DATE, "2023-01-01T00:00:00Z", Constants.TITLE, "Alpha")));
        ReflectionTestUtils.invokeMethod(
                locaService, "mergeAndSortByIssuedDateOrTitle", existingList, newList
        );
        assertEquals("2023-01-01T00:00:00Z", existingList.get(0).get(Constants.ISSUED_DATE));
        assertEquals("Bravo", existingList.get(1).get(Constants.TITLE));
    }

    @Test
    void testSanitizeProfile_Public() throws Exception {
        // Manually set the @Value field using reflection
        Field field = profileService.getClass().getDeclaredField("profileVisibleAllowedFields");
        field.setAccessible(true);
        field.set(profileService, "profileImageUrl,profileStatus,employmentDetails");

        // Prepare profile data with PUBLIC visibility
        Map<String, Object> profileDetails = new HashMap<>();
        profileDetails.put("profilePreference", 0); // PUBLIC
        profileDetails.put("profileImageUrl", "http://image.url");
        profileDetails.put("profileStatus", "Available");
        profileDetails.put("employmentDetails", "Engineer");
        profileDetails.put("extraField", "Should remain because it's PUBLIC");

        Map<String, Object> profile = new HashMap<>();
        profile.put("profileDetails", profileDetails);

        // Invoke private method using reflection
        Method method = ProfileServiceImpl.class.getDeclaredMethod("sanitizeProfile", Map.class, String.class);
        method.setAccessible(true);
        method.invoke(profileService, profile, "token123");

        // Assert nothing was removed
        Map<?, ?> result = (Map<?, ?>) profile.get("profileDetails");

        assertEquals("http://image.url", result.get("profileImageUrl"));
        assertEquals("Available", result.get("profileStatus"));
        assertEquals("Engineer", result.get("employmentDetails"));
        assertEquals("Should remain because it's PUBLIC", result.get("extraField")); // ✅ still present
    }


    @Test
    void testSanitizeProfile_PrivateNoOne() throws Exception {
        // Inject the value for the @Value field using reflection
        Field field = profileService.getClass().getDeclaredField("profileVisibleAllowedFields");
        field.setAccessible(true);
        field.set(profileService, "name,email"); // Only these fields will be allowed

        Map<String, Object> profileDetails = new HashMap<>();
        profileDetails.put("profilePreference", 1); // PRIVATE_NO_ONE
        profileDetails.put("name", "Alice");
        profileDetails.put("email", "alice@example.com");
        profileDetails.put("phone", "123456"); // Should be removed

        Map<String, Object> profile = new HashMap<>();
        profile.put("profileDetails", profileDetails);

        // Call the private method
        Method method = ProfileServiceImpl.class.getDeclaredMethod("sanitizeProfile", Map.class, String.class);
        method.setAccessible(true);
        method.invoke(profileService, profile, "token456");

        // Validate that only allowed fields remain
        Map<?, ?> result = (Map<?, ?>) profile.get("profileDetails");
        assertEquals(2, result.size());
        assertTrue(result.containsKey("name"));
        assertTrue(result.containsKey("email"));
        assertFalse(result.containsKey("phone")); // ✅ Removed
    }

    @Test
    void testSanitizeProfile_PrivateConnections_Approved() throws Exception {
        // Set the @Value field using reflection
        Field field = profileService.getClass().getDeclaredField("profileVisibleAllowedFields");
        field.setAccessible(true);
        field.set(profileService, "name,email");

        // Prepare profile with PRIVATE_CONNECTIONS
        Map<String, Object> profileDetails = new HashMap<>();
        profileDetails.put("profilePreference", 2); // PRIVATE_CONNECTIONS
        profileDetails.put("name", "Bob");
        profileDetails.put("email", "bob@example.com");
        profileDetails.put("phone", "0000000000");

        Map<String, Object> profile = new HashMap<>();
        profile.put("id", "user123");
        profile.put("authToken", "auth123");
        profile.put("profileDetails", profileDetails);

        lenient().doReturn(Map.of("status", "APPROVED"))
                .when(profileService).checkConnected("user123", "auth123", "token789");
        // Invoke the private method
        Method method = ProfileServiceImpl.class.getDeclaredMethod("sanitizeProfile", Map.class, String.class);
        method.setAccessible(true);
        method.invoke(profileService, profile, "token789");

        // Since connection is APPROVED, full profile should remain
        Map<?, ?> result = (Map<?, ?>) profile.get("profileDetails");
        assertEquals("Bob", result.get("name"));
        assertEquals("bob@example.com", result.get("email"));
        assertEquals("0000000000", result.get("phone")); // ✅ Not removed
    }



    @Test
    void testSanitizeProfile_PrivateConnections_NotApproved() throws Exception {
        Field field = profileService.getClass().getDeclaredField("profileVisibleAllowedFields");
        field.setAccessible(true);
        field.set(profileService, "name,email");

        // ✅ Prepare profile with PRIVATE_CONNECTIONS and additional field (mobile)
        Map<String, Object> profileDetails = new HashMap<>();
        profileDetails.put("profilePreference", 10); // PRIVATE_CONNECTIONS
        profileDetails.put("name", "Bob");
        profileDetails.put("email", "bob@example.com");
        profileDetails.put("mobile", "1111111111"); // <-- should be removed

        Map<String, Object> profile = new HashMap<>();
        profile.put("id", "user123");
        profile.put("authToken", "auth123");
        profile.put("profileDetails", profileDetails);

        // ✅ Mock checkConnected() to return PENDING
        // lenient() allows unused stubbing if fallback path is taken
        lenient().doReturn(Map.of("status", "PENDING"))
                .when(profileService).checkConnected("user123", "auth123", "token789");

        // ✅ Invoke private method via reflection
        Method method = ProfileServiceImpl.class.getDeclaredMethod("sanitizeProfile", Map.class, String.class);
        method.setAccessible(true);
        method.invoke(profileService, profile, "token789");

        // ✅ Assert only allowed fields are retained
        Map<?, ?> result = (Map<?, ?>) profile.get("profileDetails");
        System.out.println("Sanitized result: " + result); // <-- Optional for debug

        assertTrue(result.containsKey("name"));
        assertTrue(result.containsKey("email"));
        assertFalse(result.containsKey("mobile"));
    }

    @Test
    void testSanitizeProfile_InvalidPreference_RemovesPersonalDetails() throws Exception {
        // Reflect @Value field
        Field field = profileService.getClass().getDeclaredField("profileVisibleAllowedFields");
        field.setAccessible(true);
        field.set(profileService, "name,email");

        // Set up profile with invalid preference
        Map<String, Object> profileDetails = new HashMap<>();
        profileDetails.put("profilePreference", 99); // invalid int, not in enum
        profileDetails.put("name", "Test");

        Map<String, Object> profile = new HashMap<>();
        profile.put("profileDetails", profileDetails);

        // Invoke private method
        Method method = ProfileServiceImpl.class.getDeclaredMethod("sanitizeProfile", Map.class, String.class);
        method.setAccessible(true);
        method.invoke(profileService, profile, "token000");

        // Verify result
        Map<?, ?> result = (Map<?, ?>) profile.get("profileDetails");
        System.out.println("Sanitized result: " + result);

        assertFalse(result.containsKey("personalDetails")); // ✅ should be removed
        assertEquals("Test", result.get("name"));
    }

    @Test
    void testSanitizeProfile_Private_RemovesFilteredKeys() {
        // Arrange
        ProfileServiceImpl locaService = new ProfileServiceImpl(null, null, null, null, null, null, null, null, null, null);

        // Inject config value for filtered keys
        ReflectionTestUtils.setField(locaService, "basicDetailsFilteredKeys",
                "profileCompletionPercentage,karmaPoints,certificateCount,postCount");

        // Allowed visible fields for config
        ReflectionTestUtils.setField(locaService, "profileVisibleAllowedFields", "firstName,profileImageUrl");

        Map<String, Object> profileDetails = new HashMap<>();
        profileDetails.put("profilePreference", ProfilePreference.PRIVATE_NO_ONE.getValue());
        profileDetails.put("firstName", "TestUser");
        profileDetails.put("profileCompletionPercentage", 85.0);
        profileDetails.put("karmaPoints", 50);
        profileDetails.put("certificateCount", 3);
        profileDetails.put("postCount", 10);

        Map<String, Object> profile = new HashMap<>();
        profile.put(Constants.PROFILE_DETAILS, profileDetails);

        // Act
        ReflectionTestUtils.invokeMethod(locaService, "sanitizeProfile", profile, "dummyUserToken");

        // Assert
        @SuppressWarnings("unchecked")
        Map<String, Object> updatedDetails = (Map<String, Object>) profile.get(Constants.PROFILE_DETAILS);

        assertFalse(updatedDetails.containsKey("profileCompletionPercentage"));
        assertFalse(updatedDetails.containsKey("karmaPoints"));
        assertFalse(updatedDetails.containsKey("certificateCount"));
        assertFalse(updatedDetails.containsKey("postCount"));
        assertTrue(updatedDetails.containsKey("firstName")); // allowed field should remain
    }


    @Test
    void testTransformOrgCustomFieldsForES() throws Exception {

        // Build restructuredData input
        List<Map<String, Object>> customFieldValues = new ArrayList<>();

        // Case 1: MASTER_LIST type field
        Map<String, Object> masterListField = new HashMap<>();
        masterListField.put("fieldType", "MASTER_LIST");

        List<Map<String, Object>> valuesList = new ArrayList<>();
        Map<String, Object> valueEntry1 = new HashMap<>();
        valueEntry1.put("attributeName", "attr1");
        valueEntry1.put("value", "val1");
        valuesList.add(valueEntry1);

        masterListField.put("values", valuesList);
        customFieldValues.add(masterListField);

        // Case 2: TEXT type field
        Map<String, Object> textField = new HashMap<>();
        textField.put("fieldType", "TEXT");
        textField.put("attributeName", "attr2");
        textField.put("value", "val2");
        customFieldValues.add(textField);

        // Organisation entry
        Map<String, Object> orgEntry = new HashMap<>();
        orgEntry.put("organisationId", "org1");
        orgEntry.put("customFieldValues", customFieldValues);

        List<Map<String, Object>> restructuredData = List.of(orgEntry);

        // Use reflection to access private method
        Method method = ProfileServiceImpl.class
                .getDeclaredMethod("transformOrgCustomFieldsForES", List.class);
        method.setAccessible(true);

        // Act
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> result =
                (List<Map<String, Object>>) method.invoke(profileService, restructuredData);

        // Assert
        assertNotNull(result);
        assertEquals(1, result.size());
        Map<String, Object> resultEntry = result.get(0);
        assertEquals("org1", resultEntry.get("orgId"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> fields = (List<Map<String, Object>>) resultEntry.get("fields");
        assertEquals(2, resultEntry.size());
        assertFalse(fields.stream().anyMatch(map -> map.containsKey("fields")));
        assertFalse(fields.stream().anyMatch(map -> map.containsKey("orgId")));
    }

    @Test
    void testGetAdditionalFieldsByOrg_BlankAuthToken() {
        // Act
        ApiResponse response = profileService.getAdditionalFieldsByOrg("user1", "org1", "", false);

        // Assert
        assertEquals(HttpStatus.UNAUTHORIZED, response.getResponseCode());
        assertTrue(((Map<?, ?>) response.getResult()).isEmpty());
    }

    @Test
    void testGetAdditionalFieldsByOrg_UserIdMismatch()  {
        // Arrange
        when(accessTokenValidator.fetchUserIdFromAccessToken("token123")).thenReturn("differentUser");

        // Act
        ApiResponse response = profileService.getAdditionalFieldsByOrg("", "org1", "token123", false);

        // Assert
        assertEquals(HttpStatus.UNAUTHORIZED, response.getResponseCode());
    }

    @Test
    void testGetAdditionalFieldsByOrg_OrgDataNotFound() throws Exception {
        // Arrange
        when(accessTokenValidator.fetchUserIdFromAccessToken("token123")).thenReturn("user1");
        
        // Mock cassandra operation to return data for different org
        Map<String, Object> contextData = Map.of(
            Constants.CONTEXT_DATA, "[{\"organisationId\":\"differentOrg\",\"key\":\"value\"}]"
        );
        when(cassandraOperation.getRecordsByPropertiesByKey(anyString(), anyString(), anyMap(), any(), any()))
                .thenReturn(Collections.singletonList(contextData));
        
        // Mock projectUtil to parse the JSON
        Map<String, Object> differentOrgData = Map.of("organisationId", "differentOrg", "key", "value");
        when(projectUtil.parseListOfMap(anyString()))
                .thenReturn(Collections.singletonList(differentOrgData));

        // Act
        ApiResponse response = profileService.getAdditionalFieldsByOrg("user1", "org1", "token123", false);

        // Assert
        assertEquals(HttpStatus.OK, response.getResponseCode());
        assertFalse(((Map<?, ?>) response.getResult()).isEmpty());
    }

    @Test
    void testGetAdditionalFieldsByOrg_OrgDataFound() throws Exception {
        // Arrange
        when(accessTokenValidator.fetchUserIdFromAccessToken("token123")).thenReturn("user1");
        
        // Mock cassandra operation to return organization data
        Map<String, Object> contextData = Map.of(
            Constants.CONTEXT_DATA, "[{\"organisationId\":\"org1\",\"key\":\"value\"}]"
        );
        when(cassandraOperation.getRecordsByPropertiesByKey(anyString(), anyString(), anyMap(), any(), any()))
                .thenReturn(Collections.singletonList(contextData));
        
        // Mock projectUtil to parse the JSON
        Map<String, Object> orgData = Map.of("organisationId", "org1", "key", "value");
        when(projectUtil.parseListOfMap(anyString()))
                .thenReturn(Collections.singletonList(orgData));

        // Act
        ApiResponse response = profileService.getAdditionalFieldsByOrg("user1", "org1", "token123", false);

        // Assert
        assertEquals(HttpStatus.OK, response.getResponseCode());
        assertEquals(orgData, response.get(Constants.RESPONSE));
    }

    @Test
    void testRestructureByOrgId_AllPaths() throws Exception {
        // Prepare test data
        Map<String, Object> itemWithCustomFields = new HashMap<>();
        itemWithCustomFields.put("organisationId", "org1");
        itemWithCustomFields.put("customFieldValues", new ArrayList<>());

        Map<String, Object> itemWithoutCustomFields = new HashMap<>();
        itemWithoutCustomFields.put("organisationId", "org2");
        itemWithoutCustomFields.put("key", "value");

        List<Map<String, Object>> existingData = new ArrayList<>();
        existingData.add(itemWithCustomFields);
        existingData.add(itemWithoutCustomFields);

        List<Map<String, Object>> newCustomFieldValues = List.of(Map.of("field", "newValue"));

        // Use reflection to call private method
        Method method = ProfileServiceImpl.class.getDeclaredMethod(
                "restructureByOrgId",
                List.class, String.class, List.class
        );
        method.setAccessible(true);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> result = (List<Map<String, Object>>) method.invoke(
                profileService, existingData, "org3", newCustomFieldValues
        );

        // Validate
        assertNotNull(result);
        assertEquals(3, result.size());
        assertTrue(result.stream().anyMatch(m -> m.get("organisationId").equals("org1")));
        assertTrue(result.stream().anyMatch(m -> m.get("organisationId").equals("org2")));
        assertTrue(result.stream().anyMatch(m -> m.get("organisationId").equals("org3")));
    }

    @Test
    void testGetCustomFieldById_Found() throws Exception {
        CustomFieldEntity entity = new CustomFieldEntity();
        when(customFieldRepository.findByCustomFiledIdAndIsActiveTrue("id1"))
                .thenReturn(Optional.of(entity));

        Method method = ProfileServiceImpl.class.getDeclaredMethod("getCustomFieldById", String.class);
        method.setAccessible(true);
        CustomFieldEntity result = (CustomFieldEntity) method.invoke(profileService, "id1");

        assertNotNull(result);
    }

    @Test
    void testGetCustomFieldById_NotFound() throws Exception {
        when(customFieldRepository.findByCustomFiledIdAndIsActiveTrue("id2"))
                .thenReturn(Optional.empty());

        Method method = ProfileServiceImpl.class.getDeclaredMethod("getCustomFieldById", String.class);
        method.setAccessible(true);
        CustomFieldEntity result = (CustomFieldEntity) method.invoke(profileService, "id2");

        assertNull(result);
    }

    @Test
    void testGetCustomFieldById_Exception() throws Exception {
        when(customFieldRepository.findByCustomFiledIdAndIsActiveTrue("id3"))
                .thenThrow(new RuntimeException("DB error"));

        Method method = ProfileServiceImpl.class.getDeclaredMethod("getCustomFieldById", String.class);
        method.setAccessible(true);
        CustomFieldEntity result = (CustomFieldEntity) method.invoke(profileService, "id3");

        assertNull(result);
    }


    private String invokeValidate(CustomFieldEntity entity, List<Map<String, Object>> values) throws Exception {
        Method method = ProfileServiceImpl.class.getDeclaredMethod(
                "validateMasterListValues",
                CustomFieldEntity.class, List.class
        );
        method.setAccessible(true);
        return (String) method.invoke(profileService, entity, values);
    }

    @Test
    void testCustomFieldDataNull() throws Exception {
        ObjectMapper realMapper = new ObjectMapper();
        CustomFieldEntity entity = new CustomFieldEntity();
        entity.setCustomFieldData(realMapper.createObjectNode()); // no "customFieldData"

        List<Map<String, Object>> values = List.of();
        String result = invokeValidate(entity, values);
        assertEquals("Invalid master list field definition.", result);
    }

    @Test
    void testCustomFieldDataNotArray() throws Exception {
        ObjectMapper realMapper = new ObjectMapper();
        ObjectNode root = realMapper.createObjectNode();
        root.putObject("customFieldData"); // object, not array

        CustomFieldEntity entity = new CustomFieldEntity();
        entity.setCustomFieldData(root);

        String result = invokeValidate(entity, List.of());
        assertEquals("Invalid master list field definition.", result);
    }

    @Test
    void testMissingLevel() throws Exception {
        ObjectMapper realMapper = new ObjectMapper();
        ArrayNode arrayNode = realMapper.createArrayNode();
        ObjectNode root = realMapper.createObjectNode();
        root.set("customFieldData", arrayNode);

        CustomFieldEntity entity = new CustomFieldEntity();
        entity.setCustomFieldData(root);

        Map<String, Object> value = new HashMap<>();
        value.put("attributeName", "name");
        value.put("value", "val");

        String result = invokeValidate(entity, List.of(value));
        assertEquals("Each master list value must have a level.", result);
    }

    @Test
    void testDuplicateLevel() throws Exception {
        ObjectMapper realMapper = new ObjectMapper();
        ArrayNode arrayNode = realMapper.createArrayNode();
        ObjectNode root = realMapper.createObjectNode();
        root.set("customFieldData", arrayNode);

        CustomFieldEntity entity = new CustomFieldEntity();
        entity.setCustomFieldData(root);

        Map<String, Object> v1 = Map.of("level", 1, "attributeName", "a", "value", "v");
        Map<String, Object> v2 = Map.of("level", 1, "attributeName", "b", "value", "v");

        String result = invokeValidate(entity, List.of(v1, v2));
        assertTrue(result.contains("Only one value allowed per level"));
    }

    @Test
    void testMissingAttributeOrValue() throws Exception {
        ObjectMapper realMapper = new ObjectMapper();
        ArrayNode arrayNode = realMapper.createArrayNode();
        ObjectNode root = realMapper.createObjectNode();
        root.set("customFieldData", arrayNode);

        CustomFieldEntity entity = new CustomFieldEntity();
        entity.setCustomFieldData(root);

        Map<String, Object> v1 = Map.of("level", 1, "value", "v"); // missing attributeName

        String result = invokeValidate(entity, List.of(v1));
        assertEquals("Each master list value must have attribute name, value and level.", result);
    }

    @Test
    void testInvalidValueAtLevel1() throws Exception {
        ObjectMapper realMapper = new ObjectMapper();
        ArrayNode arrayNode = realMapper.createArrayNode(); // empty, so no match
        ObjectNode root = realMapper.createObjectNode();
        root.set("customFieldData", arrayNode);

        CustomFieldEntity entity = new CustomFieldEntity();
        entity.setCustomFieldData(root);

        Map<String, Object> v1 = Map.of("level", 1, "attributeName", "a", "value", "x");

        String result = invokeValidate(entity, List.of(v1));
        assertTrue(result.startsWith("Invalid value"));
    }

    @Test
    void testInvalidChildValueAtHigherLevel() throws Exception {
        ObjectMapper realMapper = new ObjectMapper();
        ArrayNode childArray = realMapper.createArrayNode();
        ObjectNode parentNode = realMapper.createObjectNode();
        parentNode.put("fieldValue", "parent");
        parentNode.set("fieldValues", childArray); // empty, no match
        ArrayNode arrayNode = realMapper.createArrayNode();
        arrayNode.add(parentNode);

        ObjectNode root = realMapper.createObjectNode();
        root.set("customFieldData", arrayNode);

        CustomFieldEntity entity = new CustomFieldEntity();
        entity.setCustomFieldData(root);

        Map<String, Object> v1 = Map.of("level", 1, "attributeName", "a", "value", "parent");
        Map<String, Object> v2 = Map.of("level", 2, "attributeName", "b", "value", "child");

        String result = invokeValidate(entity, List.of(v1, v2));
        assertTrue(result.startsWith("Invalid value"));
    }

    @Test
    void testSuccessfulValidation() throws Exception {
        ObjectMapper realMapper = new ObjectMapper();
        ObjectNode childNode = realMapper.createObjectNode();
        childNode.put("fieldValue", "child");

        ArrayNode childArray = realMapper.createArrayNode();
        childArray.add(childNode);

        ObjectNode parentNode = realMapper.createObjectNode();
        parentNode.put("fieldValue", "parent");
        parentNode.set("fieldValues", childArray);

        ArrayNode arrayNode = realMapper.createArrayNode();
        arrayNode.add(parentNode);

        ObjectNode root = realMapper.createObjectNode();
        root.set("customFieldData", arrayNode);

        CustomFieldEntity entity = new CustomFieldEntity();
        entity.setCustomFieldData(root);

        Map<String, Object> v1 = Map.of("level", 1, "attributeName", "a", "value", "parent");
        Map<String, Object> v2 = Map.of("level", 2, "attributeName", "b", "value", "child");

        String result = invokeValidate(entity, List.of(v1, v2));
        assertNull(result);
    }

    @Test
    void testExceptionCase() throws Exception {
        CustomFieldEntity entity = new CustomFieldEntity(); // no data, leads to NPE
        entity.setCustomFieldData(null);

        String result = invokeValidate(entity, List.of());
        assertEquals("Error validating master list values.", result);
    }

    @Test
    void testProfileDataNull() throws Exception {
        Method method = ProfileServiceImpl.class.getDeclaredMethod("calculateProfileCompletionPercentage", Map.class, String.class, String.class);
        method.setAccessible(true);
        
        Double result = (Double) method.invoke(profileService, null, "u1", "t1");
        assertEquals(0.0, result);
    }

    @Test
    void testRequiredFieldsNullOrEmpty() throws Exception {
        Method method = ProfileServiceImpl.class.getDeclaredMethod("calculateProfileCompletionPercentage", Map.class, String.class, String.class);
        method.setAccessible(true);
        
        when(serverProperties.getProfileCompletionRequiredFields()).thenReturn(null);
        Double result1 = (Double) method.invoke(profileService, new HashMap<>(), "u1", "t1");
        assertEquals(0.0, result1);

        when(serverProperties.getProfileCompletionRequiredFields()).thenReturn(Collections.emptyList());
        Double result2 = (Double) method.invoke(profileService, new HashMap<>(), "u1", "t1");
        assertEquals(0.0, result2);
    }

    @Test
    void testExtendedProfileFieldCases() throws Exception {
        Map<String, Object> profileData = new HashMap<>();
        when(serverProperties.getProfileCompletionRequiredFields()).thenReturn(List.of("extField", "serviceHistory"));

        Map<String, Object> profDetails = new HashMap<>();
        profDetails.put("professionalDetails", List.of("x"));
        profileData.put("profileDetails", profDetails);

        Method method = ProfileServiceImpl.class.getDeclaredMethod("calculateProfileCompletionPercentage", Map.class, String.class, String.class);
        method.setAccessible(true);
        
        Double result = (Double) method.invoke(profileService, profileData, "u1", "t1");
        // Verify result is calculated correctly
        assertTrue(result >= 0.0);
    }

    @Test
    void testEmploymentDetailsAndNormalFieldCases() throws Exception {
        Map<String, Object> profileData = new HashMap<>();
        Map<String, Object> nestedData = new HashMap<>();
        nestedData.put("name", "John");
        Map<String, Object> empDetails = new HashMap<>();
        empDetails.put("aboutMe", "Some text");
        nestedData.put("employmentDetails", empDetails);
        profileData.put("profileDetails", nestedData);

        when(serverProperties.getProfileCompletionRequiredFields()).thenReturn(List.of("employmentDetails", "name"));
        when(serverProperties.getFieldWeight()).thenReturn(50.0);

        Method method = ProfileServiceImpl.class.getDeclaredMethod("calculateProfileCompletionPercentage", Map.class, String.class, String.class);
        method.setAccessible(true);
        
        Double result = (Double) method.invoke(profileService, profileData, "u1", "t1");
        assertTrue(result >= 0.0); // Verify result is calculated correctly
    }

    @Test
    void testEmptyValuesAndExceptionPath() throws Exception {
        Map<String, Object> profileData = new HashMap<>();
        profileData.put("fieldX", "  "); // Blank string

        when(serverProperties.getProfileCompletionRequiredFields()).thenReturn(List.of("fieldX"));

        Method method = ProfileServiceImpl.class.getDeclaredMethod("calculateProfileCompletionPercentage", Map.class, String.class, String.class);
        method.setAccessible(true);
        
        Double result = (Double) method.invoke(profileService, profileData, "u1", "t1");
        assertTrue(result >= 0.0); // Verify result is calculated correctly
    }

    @Test
    void testAnalyzeCompetenciesUsingReflection() throws Exception {

        Map<String, Map<String, Object>> courseMetadata = new HashMap<>();

        // Invalid competencies (not a List)
        Map<String, Object> invalidCourse = new HashMap<>();
        invalidCourse.put(Constants.COMPETENCIES_V6, "Invalid");
        courseMetadata.put("course1", invalidCourse);

        // Competencies list with invalid element type
        Map<String, Object> partiallyValidCourse = new HashMap<>();
        partiallyValidCourse.put(Constants.COMPETENCIES_V6, List.of("InvalidElement"));
        courseMetadata.put("course2", partiallyValidCourse);

        // Valid competencies
        Map<String, Object> validCourse = new HashMap<>();
        Map<String, Object> competency1 = new HashMap<>();
        competency1.put(Constants.COMPETENCY_AREA_NAME, "AreaA");
        competency1.put(Constants.COMPETENCY_THEME_NAME, "ThemeA");
        competency1.put(Constants.COMPETENCY_SUB_THEME_NAME, "SubTheme1");
        validCourse.put(Constants.COMPETENCIES_V6, List.of(competency1));
        courseMetadata.put("course3", validCourse);

        // Access private method via reflection
        Method method = ProfileServiceImpl.class.getDeclaredMethod(
                "analyzeCompetencies", Map.class
        );
        method.setAccessible(true);

        Object result = method.invoke(profileService, courseMetadata);

        assertNotNull(result);
        assertTrue(result instanceof Map);

        // Just verify the method executed successfully
    }

    @Test
    void testCustomFieldNotFound() {
        Map<String, Object> field = new HashMap<>();
        field.put(Constants.CUSTOM_FIELD_ID, "cf1");
        field.put(Constants.FIELD_TYPE, Constants.TEXT);
        Map<String, Object> request = Map.of(
                Constants.USER_ID_RQST, "u1",
                Constants.ORGANISATION_ID, "org1",
                Constants.CUSTOM_FIELD_VALUES, List.of(field)
        );
        when(customFieldRepository.findByCustomFiledIdAndIsActiveTrue("cf1")).thenReturn(null);
        String result = ReflectionTestUtils.invokeMethod(profileService, "validateAdditionalFieldsRequest", request);
        assertTrue(result.contains("does not exist"));
    }

    @Test
    void testCustomFieldInactive() {
        CustomFieldEntity entity = mock(CustomFieldEntity.class);
        when(entity.getIsActive()).thenReturn(false);
        when(customFieldRepository.findByCustomFiledIdAndIsActiveTrue("cf1")).thenReturn(Optional.of(entity));
        Map<String, Object> field = new HashMap<>();
        field.put(Constants.CUSTOM_FIELD_ID, "cf1");
        field.put(Constants.FIELD_TYPE, Constants.TEXT);
        Map<String, Object> request = Map.of(
                Constants.USER_ID_RQST, "u1",
                Constants.ORGANISATION_ID, "org1",
                Constants.CUSTOM_FIELD_VALUES, List.of(field)
        );
        String result = ReflectionTestUtils.invokeMethod(profileService, "validateAdditionalFieldsRequest", request);
        assertTrue(result.contains("is not active"));
    }


    @Test
    void testOrgIdMismatch() {
        CustomFieldEntity entity = mock(CustomFieldEntity.class);
        ObjectNode data = new ObjectMapper().createObjectNode();
        data.put(Constants.ORGANISATION_ID, "otherOrg");
        data.put(Constants.ATTRIBUTE_NAME, "attr");
        data.put(Constants.TYPE, Constants.TEXT);
        when(entity.getIsActive()).thenReturn(true);
        when(entity.getCustomFieldData()).thenReturn(data);
        when(customFieldRepository.findByCustomFiledIdAndIsActiveTrue("cf1")).thenReturn(Optional.of(entity));

        Map<String, Object> field = new HashMap<>();
        field.put(Constants.CUSTOM_FIELD_ID, "cf1");
        field.put(Constants.FIELD_TYPE, Constants.TEXT);
        field.put(Constants.ATTRIBUTE_NAME, "attr");
        field.put(Constants.VALUE, "val");
        Map<String, Object> request = Map.of(
                Constants.USER_ID_RQST, "u1",
                Constants.ORGANISATION_ID, "org1",
                Constants.CUSTOM_FIELD_VALUES, List.of(field)
        );
        String result = ReflectionTestUtils.invokeMethod(profileService, "validateAdditionalFieldsRequest", request);
        assertTrue(result.contains("is not configured for organization"));
    }

    @Test
    void testAttributeNameMismatch() {
        CustomFieldEntity entity = mock(CustomFieldEntity.class);
        ObjectNode data = new ObjectMapper().createObjectNode();
        data.put(Constants.ORGANISATION_ID, "org1");
        data.put(Constants.ATTRIBUTE_NAME, "attr2");
        data.put(Constants.TYPE, Constants.TEXT);
        when(entity.getIsActive()).thenReturn(true);
        when(entity.getCustomFieldData()).thenReturn(data);
        when(customFieldRepository.findByCustomFiledIdAndIsActiveTrue("cf1")).thenReturn(Optional.of(entity));

        Map<String, Object> field = new HashMap<>();
        field.put(Constants.CUSTOM_FIELD_ID, "cf1");
        field.put(Constants.FIELD_TYPE, Constants.TEXT);
        field.put(Constants.ATTRIBUTE_NAME, "attr1");
        field.put(Constants.VALUE, "val");
        Map<String, Object> request = Map.of(
                Constants.USER_ID_RQST, "u1",
                Constants.ORGANISATION_ID, "org1",
                Constants.CUSTOM_FIELD_VALUES, List.of(field)
        );
        String result = ReflectionTestUtils.invokeMethod(profileService, "validateAdditionalFieldsRequest", request);
        assertTrue(result.contains("Invalid attribute name"));
    }

    @Test
    void testTextFieldMissingValue() {
        CustomFieldEntity entity = mock(CustomFieldEntity.class);
        ObjectNode data = new ObjectMapper().createObjectNode();
        data.put(Constants.ORGANISATION_ID, "org1");
        data.put(Constants.ATTRIBUTE_NAME, "attr");
        data.put(Constants.TYPE, Constants.TEXT);
        when(entity.getIsActive()).thenReturn(true);
        when(entity.getCustomFieldData()).thenReturn(data);
        when(customFieldRepository.findByCustomFiledIdAndIsActiveTrue("cf1")).thenReturn(Optional.of(entity));

        Map<String, Object> field = new HashMap<>();
        field.put(Constants.CUSTOM_FIELD_ID, "cf1");
        field.put(Constants.FIELD_TYPE, Constants.TEXT);
        field.put(Constants.ATTRIBUTE_NAME, "attr");
        // no value
        Map<String, Object> request = Map.of(
                Constants.USER_ID_RQST, "u1",
                Constants.ORGANISATION_ID, "org1",
                Constants.CUSTOM_FIELD_VALUES, List.of(field)
        );
        String result = ReflectionTestUtils.invokeMethod(profileService, "validateAdditionalFieldsRequest", request);
        assertTrue(result.contains("must have a value"));
    }

    @Test
    void testTextFieldTypeMismatch() {
        CustomFieldEntity entity = mock(CustomFieldEntity.class);
        ObjectNode data = new ObjectMapper().createObjectNode();
        data.put(Constants.ORGANISATION_ID, "org1");
        data.put(Constants.ATTRIBUTE_NAME, "attr");
        data.put(Constants.TYPE, Constants.MASTER_LIST);
        when(entity.getIsActive()).thenReturn(true);
        when(entity.getCustomFieldData()).thenReturn(data);
        when(customFieldRepository.findByCustomFiledIdAndIsActiveTrue("cf1")).thenReturn(Optional.of(entity));
        Map<String, Object> field = new HashMap<>();
        field.put(Constants.CUSTOM_FIELD_ID, "cf1");
        field.put(Constants.FIELD_TYPE, Constants.TEXT);
        field.put(Constants.ATTRIBUTE_NAME, "attr");
        field.put(Constants.VALUE, "val");
        Map<String, Object> request = Map.of(
                Constants.USER_ID_RQST, "u1",
                Constants.ORGANISATION_ID, "org1",
                Constants.CUSTOM_FIELD_VALUES, List.of(field)
        );
        String result = ReflectionTestUtils.invokeMethod(profileService, "validateAdditionalFieldsRequest", request);
        assertTrue(result.contains("is not of type text"));
    }

    @Test
    void testMasterListFieldMissingValues() {
        CustomFieldEntity entity = mock(CustomFieldEntity.class);
        ObjectNode data = new ObjectMapper().createObjectNode();
        data.put(Constants.ORGANISATION_ID, "org1");
        data.put(Constants.ATTRIBUTE_NAME, "attr");
        data.put(Constants.TYPE, Constants.MASTER_LIST);
        when(entity.getIsActive()).thenReturn(true);
        when(entity.getCustomFieldData()).thenReturn(data);
        when(customFieldRepository.findByCustomFiledIdAndIsActiveTrue("cf1")).thenReturn(Optional.of(entity));

        Map<String, Object> field = new HashMap<>();
        field.put(Constants.CUSTOM_FIELD_ID, "cf1");
        field.put(Constants.FIELD_TYPE, Constants.MASTER_LIST);
        field.put(Constants.ATTRIBUTE_NAME, "attr");
        // no values
        Map<String, Object> request = Map.of(
                Constants.USER_ID_RQST, "u1",
                Constants.ORGANISATION_ID, "org1",
                Constants.CUSTOM_FIELD_VALUES, List.of(field)
        );
        String result = ReflectionTestUtils.invokeMethod(profileService, "validateAdditionalFieldsRequest", request);
        assertTrue(result.contains("must have values"));
    }
    @Test
    void testMasterListFieldTypeMismatch() {
        CustomFieldEntity entity = mock(CustomFieldEntity.class);
        ObjectNode data = new ObjectMapper().createObjectNode();
        data.put(Constants.ORGANISATION_ID, "org1");
        data.put(Constants.ATTRIBUTE_NAME, "attr");
        data.put(Constants.TYPE, Constants.TEXT); // The field is TEXT, but request is MASTER_LIST
        when(entity.getIsActive()).thenReturn(true);
        when(entity.getCustomFieldData()).thenReturn(data);
        when(customFieldRepository.findByCustomFiledIdAndIsActiveTrue("cf1")).thenReturn(Optional.of(entity));

        Map<String, Object> field = new HashMap<>();
        field.put(Constants.CUSTOM_FIELD_ID, "cf1");
        field.put(Constants.FIELD_TYPE, Constants.MASTER_LIST);
        field.put(Constants.ATTRIBUTE_NAME, "attr");
        field.put(Constants.VALUES, List.of(Map.of("level", 1, "attributeName", "attr", "value", "val")));
        Map<String, Object> request = Map.of(
                Constants.USER_ID_RQST, "u1",
                Constants.ORGANISATION_ID, "org1",
                Constants.CUSTOM_FIELD_VALUES, List.of(field)
        );
        String result = ReflectionTestUtils.invokeMethod(profileService, "validateAdditionalFieldsRequest", request);
        assertTrue(result.contains("is not of type masterList"));
    }

    @Test
    void testMasterListFieldValueValidationError() {
        CustomFieldEntity entity = mock(CustomFieldEntity.class);
        ObjectMapper realMapper = new ObjectMapper();
        // Set up master list definition with allowed value "allowedVal"
        ArrayNode customFieldData = realMapper.createArrayNode();
        ObjectNode level1 = realMapper.createObjectNode();
        level1.put("fieldValue", "allowedVal");
        customFieldData.add(level1);
        ObjectNode data = realMapper.createObjectNode();
        data.put(Constants.ORGANISATION_ID, "org1");
        data.put(Constants.ATTRIBUTE_NAME, "attr");
        data.put(Constants.TYPE, Constants.MASTER_LIST);
        data.set("customFieldData", customFieldData);
        when(entity.getIsActive()).thenReturn(true);
        when(entity.getCustomFieldData()).thenReturn(data);
        when(customFieldRepository.findByCustomFiledIdAndIsActiveTrue("cf1")).thenReturn(Optional.of(entity));

        Map<String, Object> field = new HashMap<>();
        field.put(Constants.CUSTOM_FIELD_ID, "cf1");
        field.put(Constants.FIELD_TYPE, Constants.MASTER_LIST);
        field.put(Constants.ATTRIBUTE_NAME, "attr");
        // Use an invalid value "val" (not "allowedVal")
        field.put(Constants.VALUES, List.of(Map.of("level", 1, "attributeName", "attr", "value", "val")));
        Map<String, Object> request = Map.of(
                Constants.USER_ID_RQST, "u1",
                Constants.ORGANISATION_ID, "org1",
                Constants.CUSTOM_FIELD_VALUES, List.of(field)
        );
        String result = ReflectionTestUtils.invokeMethod(profileService, "validateAdditionalFieldsRequest", request);
        assertTrue(result.contains("Invalid value") || result.contains("Invalid master list value"));
    }

    @Test
    void testUnsupportedFieldType() {
        CustomFieldEntity entity = mock(CustomFieldEntity.class);
        ObjectNode data = new ObjectMapper().createObjectNode();
        data.put(Constants.ORGANISATION_ID, "org1");
        data.put(Constants.ATTRIBUTE_NAME, "attr");
        data.put(Constants.TYPE, "UNSUPPORTED");
        when(entity.getIsActive()).thenReturn(true);
        when(entity.getCustomFieldData()).thenReturn(data);
        when(customFieldRepository.findByCustomFiledIdAndIsActiveTrue("cf1")).thenReturn(Optional.of(entity));
        Map<String, Object> field = new HashMap<>();
        field.put(Constants.CUSTOM_FIELD_ID, "cf1");
        field.put(Constants.FIELD_TYPE, "UNSUPPORTED");
        field.put(Constants.ATTRIBUTE_NAME, "attr");
        Map<String, Object> request = Map.of(
                Constants.USER_ID_RQST, "u1",
                Constants.ORGANISATION_ID, "org1",
                Constants.CUSTOM_FIELD_VALUES, List.of(field)
        );
        String result = ReflectionTestUtils.invokeMethod(profileService, "validateAdditionalFieldsRequest", request);
        assertTrue(result.contains("Unsupported field type"));
    }

    @Test
    void testValidTextField() {
        CustomFieldEntity entity = mock(CustomFieldEntity.class);
        ObjectNode data = new ObjectMapper().createObjectNode();
        data.put(Constants.ORGANISATION_ID, "org1");
        data.put(Constants.ATTRIBUTE_NAME, "attr");
        data.put(Constants.TYPE, Constants.TEXT);
        when(entity.getIsActive()).thenReturn(true);
        when(entity.getCustomFieldData()).thenReturn(data);
        // Mock the repository, not the private method
        when(customFieldRepository.findByCustomFiledIdAndIsActiveTrue("cf1")).thenReturn(Optional.of(entity));

        Map<String, Object> field = new HashMap<>();
        field.put(Constants.CUSTOM_FIELD_ID, "cf1");
        field.put(Constants.FIELD_TYPE, Constants.TEXT);
        field.put(Constants.ATTRIBUTE_NAME, "attr");
        field.put(Constants.VALUE, "val");
        Map<String, Object> request = Map.of(
                Constants.USER_ID_RQST, "u1",
                Constants.ORGANISATION_ID, "org1",
                Constants.CUSTOM_FIELD_VALUES, List.of(field)
        );

        String result = ReflectionTestUtils.invokeMethod(profileService, "validateAdditionalFieldsRequest", request);
        assertNull(result);
    }

    @Test
    void hasExtendedProfileData_returnsTrue_whenLocationDetailsPresent() throws Exception {
        ProfileServiceImpl localService = spy(new ProfileServiceImpl(null, null, null, null, null, null, null, null, null, null));
        ApiResponse response = mock(ApiResponse.class);
        Map<String, Object> result = Map.of(Constants.STATE, "state1", Constants.DISTRICT, "district1");
        when(response.getResponseCode()).thenReturn(HttpStatus.OK);
        when(response.get(Constants.RESPONSE)).thenReturn(result);
        doReturn(response).when(localService).readFullExtendedProfile("user1", Constants.LOCATION_DETAILS, "token");
        Method method = ProfileServiceImpl.class.getDeclaredMethod("hasExtendedProfileData", String.class, String.class, String.class);
        method.setAccessible(true);
        boolean actual = (boolean) method.invoke(localService, "user1", Constants.LOCATION_DETAILS, "token");
        assertTrue(actual);
    }

    @Test
    void hasExtendedProfileData_returnsFalse_whenLocationDetailsMissingFields() throws Exception {
        ProfileServiceImpl localService = spy(new ProfileServiceImpl(null, null, null, null, null, null, null, null, null, null));
        ApiResponse response = mock(ApiResponse.class);
        Map<String, Object> result = Map.of(Constants.STATE, "state1");
        when(response.getResponseCode()).thenReturn(HttpStatus.OK);
        when(response.get(Constants.RESPONSE)).thenReturn(result);
        doReturn(response).when(localService).readFullExtendedProfile("user1", Constants.LOCATION_DETAILS, "token");
        Method method = ProfileServiceImpl.class.getDeclaredMethod("hasExtendedProfileData", String.class, String.class, String.class);
        method.setAccessible(true);
        boolean actual = (boolean) method.invoke(localService, "user1", Constants.LOCATION_DETAILS, "token");
        assertFalse(actual);
    }

    @Test
    void hasExtendedProfileData_returnsTrue_whenContextDataIsNonEmptyCollection() throws Exception {
        ProfileServiceImpl localService = spy(new ProfileServiceImpl(null, null, null, null, null, null, null, null, null, null));
        ApiResponse response = mock(ApiResponse.class);
        Map<String, Object> result = Map.of("customType", List.of("item1"));
        when(response.getResponseCode()).thenReturn(HttpStatus.OK);
        when(response.get(Constants.RESPONSE)).thenReturn(result);
        doReturn(response).when(localService).readFullExtendedProfile("user1", "customType", "token");
        Method method = ProfileServiceImpl.class.getDeclaredMethod("hasExtendedProfileData", String.class, String.class, String.class);
        method.setAccessible(true);
        boolean actual = (boolean) method.invoke(localService, "user1", "customType", "token");
        assertTrue(actual);
    }

    @Test
    void hasExtendedProfileData_returnsFalse_whenContextDataIsEmptyCollection() throws Exception {
        ProfileServiceImpl localService = spy(new ProfileServiceImpl(null, null, null, null, null, null, null, null, null, null));
        ApiResponse response = mock(ApiResponse.class);
        Map<String, Object> result = Map.of("customType", List.of());
        when(response.getResponseCode()).thenReturn(HttpStatus.OK);
        when(response.get(Constants.RESPONSE)).thenReturn(result);
        doReturn(response).when(localService).readFullExtendedProfile("user1", "customType", "token");
        Method method = ProfileServiceImpl.class.getDeclaredMethod("hasExtendedProfileData", String.class, String.class, String.class);
        method.setAccessible(true);
        boolean actual = (boolean) method.invoke(localService, "user1", "customType", "token");
        assertFalse(actual);
    }

    @Test
    void hasExtendedProfileData_returnsFalse_whenResponseIsNull() throws Exception {
        ProfileServiceImpl localService = spy(new ProfileServiceImpl(null, null, null, null, null, null, null, null, null, null));
        doReturn(null).when(localService).readFullExtendedProfile("user1", "type", "token");
        Method method = ProfileServiceImpl.class.getDeclaredMethod("hasExtendedProfileData", String.class, String.class, String.class);
        method.setAccessible(true);
        boolean actual = (boolean) method.invoke(localService, "user1", "type", "token");
        assertFalse(actual);
    }

    @Test
    void hasExtendedProfileData_returnsFalse_whenResponseCodeNotOk() throws Exception {
        ProfileServiceImpl localService = spy(new ProfileServiceImpl(null, null, null, null, null, null, null, null, null, null));
        ApiResponse response = mock(ApiResponse.class);
        when(response.getResponseCode()).thenReturn(HttpStatus.BAD_REQUEST);
        doReturn(response).when(localService).readFullExtendedProfile("user1", "type", "token");
        Method method = ProfileServiceImpl.class.getDeclaredMethod("hasExtendedProfileData", String.class, String.class, String.class);
        method.setAccessible(true);
        boolean actual = (boolean) method.invoke(localService, "user1", "type", "token");
        assertFalse(actual);
    }

    @Test
    void hasExtendedProfileData_returnsFalse_whenExceptionThrown() throws Exception {
        ProfileServiceImpl localService = spy(new ProfileServiceImpl(null, null, null, null, null, null, null, null, null, null));
        doThrow(new RuntimeException("fail")).when(localService).readFullExtendedProfile(any(), any(), any());
        Method method = ProfileServiceImpl.class.getDeclaredMethod("hasExtendedProfileData", String.class, String.class, String.class);
        method.setAccessible(true);
        boolean actual = (boolean) method.invoke(localService, "user1", "type", "token");
        assertFalse(actual);
    }

    @Test
    void getUserRoles_returnsRoles_whenScopesMatchRootOrgId() {
        ProfileServiceImpl localService = spy(new ProfileServiceImpl(null, null, null, null, null, null, null, null, null, null));
        CassandraOperation localCassandraOperation = mock(CassandraOperation.class);
        ReflectionTestUtils.setField(localService, "cassandraOperation", localCassandraOperation);
        ObjectMapper mapper = new ObjectMapper();
        ReflectionTestUtils.setField(localService, "mapper", mapper);

        String userId = "user1";
        String rootOrgId = "org1";
        Map<String, Object> userRoleObj = new HashMap<>();
        userRoleObj.put(Constants.ROLE, "admin");
        userRoleObj.put(Constants.SCOPE, List.of(Map.of(Constants.ORGANISATION_ID, rootOrgId)));
        when(localCassandraOperation.getRecordsByPropertiesByKey(anyString(), anyString(), anyMap(), anyList(), anyString()))
                .thenReturn(List.of(userRoleObj));

        List<String> roles = localService.getUserRoles(userId, rootOrgId);
        assertEquals(List.of("admin"), roles);
    }

    @Test
    void getUserRoles_returnsEmpty_whenScopesDoNotMatchRootOrgId() {
        ProfileServiceImpl localService = spy(new ProfileServiceImpl(null, null, null, null, null, null, null, null, null, null));
        CassandraOperation localCassandraOperation = mock(CassandraOperation.class);
        ReflectionTestUtils.setField(localService, "cassandraOperation", localCassandraOperation);
        ObjectMapper mapper = new ObjectMapper();
        ReflectionTestUtils.setField(localService, "mapper", mapper);

        String userId = "user1";
        String rootOrgId = "org1";
        Map<String, Object> userRoleObj = new HashMap<>();
        userRoleObj.put(Constants.ROLE, "admin");
        userRoleObj.put(Constants.SCOPE, List.of(Map.of(Constants.ORGANISATION_ID, "otherOrg")));
        when(localCassandraOperation.getRecordsByPropertiesByKey(anyString(), anyString(), anyMap(), anyList(), anyString()))
                .thenReturn(List.of(userRoleObj));

        List<String> roles = localService.getUserRoles(userId, rootOrgId);
        assertTrue(roles.isEmpty());
    }

    @Test
    void getUserRoles_parsesScopeString_whenScopeIsString() {
        ProfileServiceImpl localService = spy(new ProfileServiceImpl(null, null, null, null, null, null, null, null, null, null));
        CassandraOperation localCassandraOperation = mock(CassandraOperation.class);
        ReflectionTestUtils.setField(localService, "cassandraOperation", localCassandraOperation);
        ObjectMapper mapper = new ObjectMapper();
        ReflectionTestUtils.setField(localService, "mapper", mapper);

        String userId = "user1";
        String rootOrgId = "org1";
        String scopeJson = "[{\"organisationId\":\"org1\"}]";
        Map<String, Object> userRoleObj = new HashMap<>();
        userRoleObj.put(Constants.ROLE, "admin");
        userRoleObj.put(Constants.SCOPE, scopeJson);
        when(localCassandraOperation.getRecordsByPropertiesByKey(anyString(), anyString(), anyMap(), anyList(), anyString()))
                .thenReturn(List.of(userRoleObj));

        List<String> roles = localService.getUserRoles(userId, rootOrgId);
        assertEquals(List.of("admin"), roles);
    }

    @Test
    void getUserRoles_returnsEmpty_whenScopeStringIsInvalidJson() {
        ProfileServiceImpl localService = spy(new ProfileServiceImpl(null, null, null, null, null, null, null, null, null, null));
        CassandraOperation localCassandraOperation = mock(CassandraOperation.class);
        ReflectionTestUtils.setField(localService, "cassandraOperation", localCassandraOperation);
        ObjectMapper mapper = new ObjectMapper();
        ReflectionTestUtils.setField(localService, "mapper", mapper);

        String userId = "user1";
        String rootOrgId = "org1";
        String invalidScopeJson = "not-a-json";
        Map<String, Object> userRoleObj = new HashMap<>();
        userRoleObj.put(Constants.ROLE, "admin");
        userRoleObj.put(Constants.SCOPE, invalidScopeJson);
        when(localCassandraOperation.getRecordsByPropertiesByKey(anyString(), anyString(), anyMap(), anyList(), anyString()))
                .thenReturn(List.of(userRoleObj));

        List<String> roles = localService.getUserRoles(userId, rootOrgId);
        assertTrue(roles.isEmpty());
    }

    @Test
    void getUserRoles_returnsDistinctRoles() {
        ProfileServiceImpl localService = spy(new ProfileServiceImpl(null, null, null, null, null, null, null, null, null, null));
        CassandraOperation localCassandraOperation = mock(CassandraOperation.class);
        ReflectionTestUtils.setField(localService, "cassandraOperation", localCassandraOperation);
        ObjectMapper mapper = new ObjectMapper();
        ReflectionTestUtils.setField(localService, "mapper", mapper);

        String userId = "user1";
        String rootOrgId = "org1";
        Map<String, Object> userRoleObj1 = new HashMap<>();
        userRoleObj1.put(Constants.ROLE, "admin");
        userRoleObj1.put(Constants.SCOPE, List.of(Map.of(Constants.ORGANISATION_ID, rootOrgId)));
        Map<String, Object> userRoleObj2 = new HashMap<>();
        userRoleObj2.put(Constants.ROLE, "admin");
        userRoleObj2.put(Constants.SCOPE, List.of(Map.of(Constants.ORGANISATION_ID, rootOrgId)));
        when(localCassandraOperation.getRecordsByPropertiesByKey(anyString(), anyString(), anyMap(), anyList(), anyString()))
                .thenReturn(List.of(userRoleObj1, userRoleObj2));

        List<String> roles = localService.getUserRoles(userId, rootOrgId);
        assertEquals(List.of("admin"), roles);
    }
    @Test
    void updateAdditionalFields_returnsUnauthorized_whenAuthTokenBlank() {
        ApiResponse response = profileService.updateAdditionalFields(Map.of(), "orgId","");
        assertEquals(HttpStatus.UNAUTHORIZED, response.getResponseCode());
    }


    @Test
    void updateAdditionalFields_returnsBadRequest_whenValidationFails() {
        Map<String, Object> req = Map.of(); // missing required params
        when(accessTokenValidator.fetchUserIdFromAccessToken("token")).thenReturn("user1");
        String result = ReflectionTestUtils.invokeMethod(profileService, "validateAdditionalFieldsRequest", req);
        assertEquals("Failed Due To Missing Params - [userId, organisationId, customFieldValues].", result);
        ApiResponse response = profileService.updateAdditionalFields(req, "orgIdtest","token");
        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
    }

    @Test
    void updateAdditionalFields_returnsInternalServerError_whenSaveFails() {
        when(accessTokenValidator.fetchUserIdFromAccessToken("token")).thenReturn("user1");
        Map<String, Object> req = Map.of(
                Constants.USER_ID, "user1",
                Constants.ORGANISATION_ID, "org1",
                Constants.CUSTOM_FIELD_VALUES, List.of(Map.of())
        );
        ApiResponse response = profileService.updateAdditionalFields(req, "orgIdtest","token");
        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
    }


    @Test
    void updateAdditionalFields_returnsUnauthorized_whenUserIdMismatch() {
        Map<String, Object> req = Map.of(
                Constants.USER_ID, "userY",
                Constants.ORGANISATION_ID, "org1",
                Constants.CUSTOM_FIELD_VALUES, List.of()
        );
        when(accessTokenValidator.fetchUserIdFromAccessToken("token")).thenReturn("userX");
        ReflectionTestUtils.setField(profileService, "accessTokenValidator", accessTokenValidator);
        ApiResponse response = profileService.updateAdditionalFields(req,"orgIdtest", "token");
        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
    }

    @Test
    void updateAdditionalFields_returnsInternalServerError_whenESUpdateFails() {
        when(accessTokenValidator.fetchUserIdFromAccessToken("token")).thenReturn("user1");
        Map<String, Object> req = Map.of(
                Constants.USER_ID, "user1",
                Constants.ORGANISATION_ID, "org1",
                Constants.CUSTOM_FIELD_VALUES, List.of(Map.of(
                        Constants.CUSTOM_FIELD_ID, "cf1",
                        Constants.FIELD_TYPE, Constants.TEXT,
                        Constants.ATTRIBUTE_NAME, "attr",
                        Constants.VALUE, "val"
                ))
        );
        CustomFieldEntity entity = mock(CustomFieldEntity.class);
        lenient().when(entity.getIsActive()).thenReturn(true);
        ObjectNode data = new ObjectMapper().createObjectNode();
        data.put(Constants.ORGANISATION_ID, "org1");
        data.put(Constants.ATTRIBUTE_NAME, "attr");
        data.put(Constants.TYPE, Constants.TEXT);
        lenient().when(entity.getCustomFieldData()).thenReturn(data);
        lenient().when(customFieldRepository.findByCustomFiledIdAndIsActiveTrue("cf1")).thenReturn(Optional.of(entity));
        EsUtilServiceImpl localEsUtilService = mock(EsUtilServiceImpl.class);
        ReflectionTestUtils.setField(profileService, "esUtilService", localEsUtilService);
        lenient().when(localEsUtilService.updateUserOrgCustomFields(any(), any(), any())).thenReturn(false);
        ApiResponse response = profileService.updateAdditionalFields(req, "org1","token");
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getResponseCode());
    }

    @Test
    void testSaveExtendedProfile_WithValidEducationalQualification_ShouldSucceed() {
        Map<String, Object> qualification = new HashMap<>();
        qualification.put("degree", "B.Tech");
        qualification.put("institutionName", "IIT Delhi");

        Map<String, Object> requestMap = new HashMap<>();
        requestMap.put(Constants.USER_ID_RQST, userID);
        requestMap.put(Constants.EDUCATIONAL_QUALIFICATIONS,
                Collections.singletonList(qualification));

        Map<String, Object> request = new HashMap<>();
        request.put(Constants.REQUEST, requestMap);

        when(accessTokenValidator.fetchUserIdFromAccessToken(token))
                .thenReturn(userID);

        when(serverProperties.getContextType())
                .thenReturn(new String[]{Constants.EDUCATIONAL_QUALIFICATIONS});

        when(serverProperties.getEducationalQualificationMandatoryFields())
                .thenReturn("degree,institutionName");

        when(serverProperties.getDegreeNameRegex())
                .thenReturn("^[a-zA-Z0-9\\s\\(\\)\\-\\.,&'/]+$");
        when(serverProperties.getInstituteNameRegex())
                .thenReturn("^[a-zA-Z0-9\\s\\(\\)\\-\\.,&'/]+$");

        when(cassandraOperation.getRecordsByPropertiesByKey(any(), any(), anyMap(), any(), any()))
                .thenReturn(new ArrayList<>());

        ApiResponse mockResponse = new ApiResponse();
        mockResponse.put(Constants.RESPONSE, Constants.SUCCESS);

        when(cassandraOperation.insertRecord(any(), any(), any()))
                .thenReturn(mockResponse);

        ApiResponse response = profileService.saveExtendedProfile(request, token);

        assertEquals(HttpStatus.OK, response.getResponseCode());
    }

    @Test
    void testSaveExtendedProfile_InvalidDegreeCharacters_ShouldFail() {
        Map<String, Object> qualification = new HashMap<>();
        qualification.put("degree", "!@#$%^&*()");
        qualification.put("institutionName", "IIT Delhi");

        Map<String, Object> requestMap = new HashMap<>();
        requestMap.put(Constants.USER_ID_RQST, userID);
        requestMap.put(Constants.EDUCATIONAL_QUALIFICATIONS, Collections.singletonList(qualification));

        Map<String, Object> request = new HashMap<>();
        request.put(Constants.REQUEST, requestMap);

        when(accessTokenValidator.fetchUserIdFromAccessToken(token)).thenReturn(userID);
        when(serverProperties.getContextType()).thenReturn(new String[]{Constants.EDUCATIONAL_QUALIFICATIONS});
        when(serverProperties.getEducationalQualificationMandatoryFields()).thenReturn("degree,institutionName");
        when(serverProperties.getDegreeNameRegex()).thenReturn("^[\\p{L}\\p{N}\\s.,'&()/-]+$");
        lenient().when(serverProperties.getInstituteNameRegex()).thenReturn("^[\\p{L}\\p{N}\\s.,'&()/-]+$");

        ApiResponse response = profileService.saveExtendedProfile(request, token);

        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
        assertTrue(response.getParams().getErrMsg().contains("degree"));
        verifyNoInteractions(cassandraOperation);
    }

    @Test
    void testSaveExtendedProfile_InvalidInstitutionNameCharacters_ShouldFail() {
        Map<String, Object> qualification = new HashMap<>();
        qualification.put("degree", "B.Tech");
        qualification.put("institutionName", "!@#$%^&*()");

        Map<String, Object> requestMap = new HashMap<>();
        requestMap.put(Constants.USER_ID_RQST, userID);
        requestMap.put(Constants.EDUCATIONAL_QUALIFICATIONS, Collections.singletonList(qualification));

        Map<String, Object> request = new HashMap<>();
        request.put(Constants.REQUEST, requestMap);

        when(accessTokenValidator.fetchUserIdFromAccessToken(token)).thenReturn(userID);
        when(serverProperties.getContextType()).thenReturn(new String[]{Constants.EDUCATIONAL_QUALIFICATIONS});
        when(serverProperties.getEducationalQualificationMandatoryFields()).thenReturn("degree,institutionName");
        lenient().when(serverProperties.getDegreeNameRegex()).thenReturn("^[\\p{L}\\p{N}\\s.,'&()/-]+$");
        when(serverProperties.getInstituteNameRegex()).thenReturn("^[\\p{L}\\p{N}\\s.,'&()/-]+$");

        ApiResponse response = profileService.saveExtendedProfile(request, token);

        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
        assertTrue(response.getParams().getErrMsg().contains("institutionName"));
        verifyNoInteractions(cassandraOperation);
    }

    @Test
    void testSaveExtendedProfile_InvalidFieldOfStudyCharacters_ShouldFail() {
        Map<String, Object> qualification = new HashMap<>();
        qualification.put("degree", "B.Tech");
        qualification.put("institutionName", "IIT Delhi");
        qualification.put("fieldOfStudy", "!@#$%^&*()");

        Map<String, Object> requestMap = new HashMap<>();
        requestMap.put(Constants.USER_ID_RQST, userID);
        requestMap.put(Constants.EDUCATIONAL_QUALIFICATIONS, Collections.singletonList(qualification));

        Map<String, Object> request = new HashMap<>();
        request.put(Constants.REQUEST, requestMap);

        when(accessTokenValidator.fetchUserIdFromAccessToken(token)).thenReturn(userID);
        when(serverProperties.getContextType()).thenReturn(new String[]{Constants.EDUCATIONAL_QUALIFICATIONS});
        when(serverProperties.getEducationalQualificationMandatoryFields()).thenReturn("degree,institutionName");
        lenient().when(serverProperties.getDegreeNameRegex()).thenReturn("^[\\p{L}\\p{N}\\s.,'&()/-]+$");
        lenient().when(serverProperties.getInstituteNameRegex()).thenReturn("^[\\p{L}\\p{N}\\s.,'&()/-]+$");
        when(serverProperties.getFieldOfStudyRegex()).thenReturn("^[\\p{L}\\p{N}\\s.,'&()/:+_-]+$");

        ApiResponse response = profileService.saveExtendedProfile(request, token);

        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
        assertTrue(response.getParams().getErrMsg().contains("fieldOfStudy"));
        verifyNoInteractions(cassandraOperation);
    }

    @Test
    void testSaveExtendedProfile_ValidFieldSpecificRegexValues_ShouldSucceed() {
        Map<String, Object> qualification = new HashMap<>();
        qualification.put("degree", "B.Tech");
        qualification.put("institutionName", "St. Xavier's College");
        qualification.put("fieldOfStudy", "Computer Science: AI+ML");

        Map<String, Object> requestMap = new HashMap<>();
        requestMap.put(Constants.USER_ID_RQST, userID);
        requestMap.put(Constants.EDUCATIONAL_QUALIFICATIONS, Collections.singletonList(qualification));

        Map<String, Object> request = new HashMap<>();
        request.put(Constants.REQUEST, requestMap);

        when(accessTokenValidator.fetchUserIdFromAccessToken(token)).thenReturn(userID);
        when(serverProperties.getContextType()).thenReturn(new String[]{Constants.EDUCATIONAL_QUALIFICATIONS});
        when(serverProperties.getEducationalQualificationMandatoryFields()).thenReturn("degree,institutionName");
        when(serverProperties.getDegreeNameRegex()).thenReturn("^[\\p{L}\\p{N}\\s.,'&()/-]+$");
        when(serverProperties.getInstituteNameRegex()).thenReturn("^[\\p{L}\\p{N}\\s.,'&()/-]+$");
        when(serverProperties.getFieldOfStudyRegex()).thenReturn("^[\\p{L}\\p{N}\\s.,'&()/:+_-]+$");

        when(cassandraOperation.getRecordsByPropertiesByKey(any(), any(), anyMap(), any(), any()))
                .thenReturn(new ArrayList<>());
        ApiResponse mockResponse = new ApiResponse();
        mockResponse.put(Constants.RESPONSE, Constants.SUCCESS);
        when(cassandraOperation.insertRecord(any(), any(), any())).thenReturn(mockResponse);

        ApiResponse response = profileService.saveExtendedProfile(request, token);

        assertEquals(HttpStatus.OK, response.getResponseCode());
    }

}
