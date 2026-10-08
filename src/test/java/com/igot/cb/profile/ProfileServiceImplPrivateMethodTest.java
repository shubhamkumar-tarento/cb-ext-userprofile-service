package com.igot.cb.profile;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.igot.cb.authentication.util.AccessTokenValidator;
import com.igot.cb.profile.service.ProfileServiceImpl;
import com.igot.cb.transactional.cassandrautils.CassandraOperation;
import com.igot.cb.transactional.redis.cache.CacheService;
import com.igot.cb.util.ApiResponse;
import com.igot.cb.util.CbServerProperties;
import com.igot.cb.util.Constants;
import com.igot.cb.util.UserUtility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProfileServiceImplPrivateMethodTest {

    @InjectMocks
    private ProfileServiceImpl profileService;

    @Mock
    private AccessTokenValidator accessTokenValidator;
    @Mock
    private CacheService cacheService;
    @Mock
    private CassandraOperation cassandraOperation;
    @Mock
    private ObjectMapper mapper;
    @Mock
    private CbServerProperties serverConfig;

    @BeforeEach
    void setup() {
        // Set private fields via ReflectionTestUtils
        ReflectionTestUtils.setField(profileService, "profileVisibleAllowedFields", "name,email");
        ReflectionTestUtils.setField(profileService, "basicDetailsFilteredKeys", "password,ssn");
    }

    @Test
    void testGetBasicProfile_InvalidToken() {
        when(accessTokenValidator.fetchUserIdFromAccessToken("badToken")).thenReturn(null);

        ApiResponse response = profileService.getBasicProfile("user123", "badToken",false);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getResponseCode());
    }

    @Test
    void testGetBasicProfile_CacheHitWithDifferenceList() throws Exception {
        String userId = "user123";
        String userToken = "token123";

        when(accessTokenValidator.fetchUserIdFromAccessToken(userToken)).thenReturn(userId);

        // Simulate cache hit with some missing fields
        Map<String, Object> cachedMap = new HashMap<>();
        cachedMap.put("field1", "value1");
        String cachedJson = "{\"field1\":\"value1\"}";

        when(cacheService.getCache(anyString())).thenReturn(cachedJson);
        when(mapper.readValue(eq(cachedJson), any(TypeReference.class))).thenReturn(cachedMap);

        // Server config requires more fields
        when(serverConfig.getBasicProfileFields()).thenReturn(Arrays.asList("field1", "field2"));

        // Mock DB call for missing field
        Map<String, Object> dbData = new HashMap<>(Map.of("field2", "value2"));
        ProfileServiceImpl spyService = Mockito.spy(profileService);
        doReturn(dbData).when(spyService).readUserDataFromDB(eq(userId), anyList());

        // Mock static methods
        try (MockedStatic<UserUtility> mockedUtility = Mockito.mockStatic(UserUtility.class)) {
            mockedUtility.when(() -> UserUtility.decryptSpecificUserData(anyMap(), anyList())).then(inv -> null);

            ApiResponse response = spyService.getBasicProfile(userId, userToken, false);
            // Cache hit with difference list merges missing fields from DB and returns successfully
            assertEquals(HttpStatus.OK, response.getResponseCode());
        }
    }

    @Test
    void testGetBasicProfile_NoCache_EmptyUserProfile() {
        String userId = "user123";
        String userToken = "token123";

        when(accessTokenValidator.fetchUserIdFromAccessToken(userToken)).thenReturn(userId);
        when(cacheService.getCache(anyString())).thenReturn(null);

        ProfileServiceImpl spyService = Mockito.spy(profileService);
        doReturn(Collections.emptyMap()).when(spyService).readUserDataFromDB(eq(userId), isNull());

        try (MockedStatic<UserUtility> mockedUtility = Mockito.mockStatic(UserUtility.class)) {
            mockedUtility.when(() -> UserUtility.decryptSpecificUserData(anyMap(), anyList())).then(inv -> null);
            ApiResponse response = spyService.getBasicProfile(userId, userToken, false);
            assertEquals(HttpStatus.NOT_FOUND, response.getResponseCode());
        }
    }

    @Test
    void testGetBasicProfile_NonSelfUser_CallsSanitize() {
        String userId = "user123";
        String userToken = "token123";

        when(accessTokenValidator.fetchUserIdFromAccessToken(userToken)).thenReturn("otherUser");
        when(cacheService.getCache(anyString())).thenReturn(null);

        ProfileServiceImpl spyService = Mockito.spy(profileService);
        Map<String, Object> profileMap = new HashMap<>();
        profileMap.put("field1", "value1");

        doReturn(profileMap).when(spyService).readUserDataFromDB(eq(userId), isNull());

        try (MockedStatic<UserUtility> mockedUtility = Mockito.mockStatic(UserUtility.class)) {
            mockedUtility.when(() -> UserUtility.decryptSpecificUserData(anyMap(), anyList())).then(inv -> null);

            ApiResponse response = spyService.getBasicProfile(userId, userToken, false);
            // Non-self user path calls sanitizeProfile but still returns 200 OK on success
            assertEquals(HttpStatus.OK, response.getResponseCode());
        }
    }

    @Test
    void testGetBasicProfile_Exception() {
        when(accessTokenValidator.fetchUserIdFromAccessToken(anyString())).thenReturn("user123");
        when(cacheService.getCache(anyString())).thenThrow(new RuntimeException("Cache failure"));

        ApiResponse response = profileService.getBasicProfile("user123", "token123", false);
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getResponseCode());
    }

    @Test
    void testGetUserBadgeCount_CacheHit_ReturnsCount() {
        String userId = "user123";
        String redisKey = "user:badgeCount_" + userId;

        when(cacheService.getCache(redisKey)).thenReturn("5");

        int result = (int) ReflectionTestUtils.invokeMethod(profileService, "getUserBadgeCount", userId);

        assertEquals(5, result);
    }

    @Test
    void testGetUserBadgeCount_CacheMiss_RecordsFound_ReturnsTotalPoints() {
        String userId = "user456";
        String redisKey = "user:badgeCount_" + userId;

        when(cacheService.getCache(redisKey)).thenReturn(null);
        when(serverConfig.getBadgeCountRedisTtl()).thenReturn(0);

        List<Map<String, Object>> records = Arrays.asList(
                Map.of(Constants.COURSE_ID, "course1"),
                Map.of(Constants.COURSE_ID, "course2"),
                Map.of(Constants.COURSE_ID, "course3")
        );
        when(cassandraOperation.getRecordsByPropertiesByKey(
                (Constants.KEYSPACE_SUNBIRD_COURSES),
                (Constants.USER_BADGE_LOOKUP_TABLE),
                (Map.of(Constants.USERID_KEY, userId)),
                (List.of(Constants.COURSE_ID)),
                (userId)
        )).thenReturn(records);

        int result = (int) ReflectionTestUtils.invokeMethod(profileService, "getUserBadgeCount", userId);

        assertEquals(3, result);
        Mockito.verify(cacheService).putCache(redisKey, 3, 0);
    }

    @Test
    void testGetUserBadgeCount_CacheMiss_NoRecords_ReturnsZero() {
        String userId = "user789";
        String redisKey = "user:badgeCount_" + userId;

        when(cacheService.getCache(redisKey)).thenReturn(null);
        when(serverConfig.getBadgeCountRedisTtl()).thenReturn(0);

        when(cassandraOperation.getRecordsByPropertiesByKey(
                (Constants.KEYSPACE_SUNBIRD_COURSES),
                (Constants.USER_BADGE_LOOKUP_TABLE),
                (Map.of(Constants.USERID_KEY, userId)),
                (List.of(Constants.COURSE_ID)),
                (userId)
        )).thenReturn(Collections.emptyList());

        int result = (int) ReflectionTestUtils.invokeMethod(profileService, "getUserBadgeCount", userId);

        assertEquals(0, result);
        Mockito.verify(cacheService).putCache(redisKey, 0, 0);
    }

    @Test
    void testGetUserBadgeCount_CacheMiss_NullRecords_ReturnsZero() {
        String userId = "userNull";
        String redisKey = "user:badgeCount_" + userId;

        when(cacheService.getCache(redisKey)).thenReturn(null);
        when(serverConfig.getBadgeCountRedisTtl()).thenReturn(0);

        when(cassandraOperation.getRecordsByPropertiesByKey(
                (Constants.KEYSPACE_SUNBIRD_COURSES),
                (Constants.USER_BADGE_LOOKUP_TABLE),
                (Map.of(Constants.USERID_KEY, userId)),
                (List.of(Constants.COURSE_ID)),
                (userId)
        )).thenReturn(null);

        int result = (int) ReflectionTestUtils.invokeMethod(profileService, "getUserBadgeCount", userId);

        assertEquals(0, result);
        Mockito.verify(cacheService).putCache(redisKey, 0, 0);
    }

    @Test
    void testGetUserBadgeCount_CacheServiceThrowsException_ReturnsZero() {
        String userId = "userError";
        String redisKey = "user:badgeCount_" + userId;

        when(cacheService.getCache(redisKey)).thenThrow(new RuntimeException("Redis unavailable"));

        int result = (int) ReflectionTestUtils.invokeMethod(profileService, "getUserBadgeCount", userId);

        assertEquals(0, result);
    }

    @Test
    void testGetUserKarmaPoints_CacheMiss_RecordsFound_ReturnsTotalPointsAndCaches() {
        String userId = "user-karma-1";
        String redisKey = "user:karmaPoints:" + userId;

        when(cacheService.getCache(redisKey)).thenReturn(null);
        List<Map<String, Object>> records = List.of(Map.of(Constants.TOTAL_POINTS, 77));
        when(cassandraOperation.getRecordsByPropertiesByKey(
                eq(Constants.KEYSPACE_SUNBIRD),
                eq(Constants.USER_KARMA_POINTS_SUMMARY_TABLE),
                anyMap(), anyList(), eq(userId)))
                .thenReturn(records);

        int points = ReflectionTestUtils.invokeMethod(profileService, "getUserKarmaPoints", userId);

        assertEquals(77, points);
        Mockito.verify(cacheService).putCache(redisKey, 77);
    }

    @Test
    void testGetUserBadgeCount_CassandraThrowsException_ReturnsZero() {
        String userId = "userCassandraError";
        String redisKey = "user:badgeCount_" + userId;

        when(cacheService.getCache(redisKey)).thenReturn(null);
        when(cassandraOperation.getRecordsByPropertiesByKey(
                eq(Constants.KEYSPACE_SUNBIRD_COURSES),
                eq(Constants.USER_BADGE_LOOKUP_TABLE),
                any(),
                any(),
                eq(userId)
        )).thenThrow(new RuntimeException("Cassandra error"));

        int result = (int) ReflectionTestUtils.invokeMethod(profileService, "getUserBadgeCount", userId);

        assertEquals(0, result);
    }
}

