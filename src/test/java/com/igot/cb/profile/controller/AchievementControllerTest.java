package com.igot.cb.profile.controller;

import com.igot.cb.profile.service.AchievementService;
import com.igot.cb.transactional.elasticsearch.dto.SearchCriteria;
import com.igot.cb.util.ApiResponse;
import com.igot.cb.util.Constants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AchievementControllerTest {
    @Mock
    private AchievementService achievementService;

    @InjectMocks
    private AchievementController achievementController;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void testCreateLearnerAchievement() {
        Map<String, Object> request = new HashMap<>();
        ApiResponse apiResponse = new ApiResponse();
        apiResponse.setResponseCode(HttpStatus.OK);
        when(achievementService.createLearnerAchievement(anyMap(), anyString(), anyString())).thenReturn(apiResponse);
        ResponseEntity<?> response = achievementController.createLearnerAchievement("token", "orgId", request);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(achievementService).createLearnerAchievement(request, "token", "orgId");
    }

    @Test
    void testUpdateLearnerAchievement() {
        Map<String, Object> request = new HashMap<>();
        ApiResponse apiResponse = new ApiResponse();
        apiResponse.setResponseCode(HttpStatus.OK);
        when(achievementService.updateLearnerAchievement(anyMap(), anyString(), anyString())).thenReturn(apiResponse);
        ResponseEntity<?> response = achievementController.updateLearnerAchievement("token", "orgId", request);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(achievementService).updateLearnerAchievement(request, "token", "orgId");
    }

    @Test
    void testReadLearnerAchievement() {
        ApiResponse apiResponse = new ApiResponse();
        apiResponse.setResponseCode(HttpStatus.OK);
        when(achievementService.readLearnerAchievement(anyString(), anyString(), anyString())).thenReturn(apiResponse);
        ResponseEntity<Object> response = achievementController.readLearnerAchievement("achId", "token", "contextType");
        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(achievementService).readLearnerAchievement("achId", "token", "contextType");
    }

    @Test
    void testDeleteLearnerAchievement() throws Exception {
        Map<String, Object> request = new HashMap<>();
        ApiResponse apiResponse = new ApiResponse();
        apiResponse.setResponseCode(HttpStatus.OK);
        when(achievementService.deleteLearnerAchievement(anyMap(), anyString())).thenReturn(apiResponse);
        ResponseEntity<?> response = achievementController.deleteLearnerAchievement("token", request);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(achievementService).deleteLearnerAchievement(request, "token");
    }

    @Test
    void testStatusUpdateLearnerAchievement() {
        Map<String, Object> request = new HashMap<>();
        ApiResponse apiResponse = new ApiResponse();
        apiResponse.setResponseCode(HttpStatus.OK);
        when(achievementService.statusUpdateLearnerAchievement(anyMap(), anyString())).thenReturn(apiResponse);
        ResponseEntity<?> response = achievementController.statusUpdateLearnerAchievement("token", request);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(achievementService).statusUpdateLearnerAchievement(request, "token");
    }

    @Test
    void testSearchLearnerAchievements() {
        SearchCriteria searchCriteria = new SearchCriteria();
        ApiResponse apiResponse = new ApiResponse();
        apiResponse.setResponseCode(HttpStatus.OK);
        when(achievementService.searchLearnerAchievements(any(SearchCriteria.class), anyString())).thenReturn(apiResponse);
        ResponseEntity<?> response = achievementController.searchLearnerAchievements("token", searchCriteria);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(achievementService).searchLearnerAchievements(searchCriteria, "token");
    }

    @Test
    void testListLearnerAchievements_success() {
        ApiResponse apiResponse = new ApiResponse();
        apiResponse.setResponseCode(HttpStatus.OK);
        Map<String, Object> result = new HashMap<>();
        result.put("achievements", "data");
        apiResponse.put("result", result);
        when(achievementService.getUserAchievements(anyString(), any())).thenReturn(apiResponse);
        ResponseEntity<?> response = achievementController.listLearnerAchievementsForAdmin("token", null);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(apiResponse, response.getBody());
        verify(achievementService).getUserAchievements("token", null);
    }

    @Test
    void testListLearnerAchievements_unauthorized() {
        ApiResponse apiResponse = new ApiResponse();
        apiResponse.setResponseCode(HttpStatus.UNAUTHORIZED);
        when(achievementService.getUserAchievements(anyString(), any())).thenReturn(apiResponse);
        ResponseEntity<?> response = achievementController.listLearnerAchievementsForAdmin("invalid_token", null);
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertEquals(apiResponse, response.getBody());
        verify(achievementService).getUserAchievements("invalid_token", null);
    }

    @Test
    void testListLearnerAchievements_badRequest() {
        ApiResponse apiResponse = new ApiResponse();
        apiResponse.setResponseCode(HttpStatus.BAD_REQUEST);
        when(achievementService.getUserAchievements(anyString(), any())).thenReturn(apiResponse);
        ResponseEntity<?> response = achievementController.listLearnerAchievementsForAdmin("token", null);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals(apiResponse, response.getBody());
        verify(achievementService).getUserAchievements(eq("token"), any());
    }

    @Test
    void testListLearnerAchievements_internalServerError() {
        ApiResponse apiResponse = new ApiResponse();
        apiResponse.setResponseCode(HttpStatus.INTERNAL_SERVER_ERROR);
        when(achievementService.getUserAchievements(anyString(), any())).thenReturn(apiResponse);
        ResponseEntity<?> response = achievementController.listLearnerAchievementsForAdmin("token", "123");
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertEquals(apiResponse, response.getBody());
        verify(achievementService).getUserAchievements("token", "123");
    }

    @Test
    void testListLearnerAchievements_emptyResult() {
        ApiResponse apiResponse = new ApiResponse();
        apiResponse.setResponseCode(HttpStatus.OK);
        apiResponse.put("result", new HashMap<>());
        when(achievementService.getUserAchievements(anyString(), any())).thenReturn(apiResponse);
        ResponseEntity<?> response = achievementController.listLearnerAchievementsForAdmin("token", "123");
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(apiResponse, response.getBody());
        verify(achievementService).getUserAchievements("token", "123");
    }

    // ==================== listLearnerAchievementsByUserIds TESTS ====================

    @Test
    void testListLearnerAchievementsByUserIds_success() {
        // valid request body → forwarded as-is to service, service returns OK
        Map<String, Object> request = new HashMap<>();
        request.put(Constants.REQUEST, Map.of(Constants.ACHIEVEMENT_IDS, List.of("achv-1", "achv-2")));
        ApiResponse apiResponse = new ApiResponse();
        apiResponse.setResponseCode(HttpStatus.OK);
        when(achievementService.getUserAchievementsByUserIds("token", request)).thenReturn(apiResponse);

        ResponseEntity<?> response =
                achievementController.listLearnerAchievementsByUserIds("token", request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertSame(apiResponse, response.getBody());
        verify(achievementService).getUserAchievementsByUserIds("token", request);
    }

    @Test
    void testListLearnerAchievementsByUserIds_invalidToken_returnsUnauthorized() {
        // service validates the token and returns UNAUTHORIZED
        Map<String, Object> request = new HashMap<>();
        ApiResponse apiResponse = new ApiResponse();
        apiResponse.setResponseCode(HttpStatus.UNAUTHORIZED);
        when(achievementService.getUserAchievementsByUserIds(anyString(), anyMap()))
                .thenReturn(apiResponse);

        ResponseEntity<?> response =
                achievementController.listLearnerAchievementsByUserIds("bad-token", request);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertSame(apiResponse, response.getBody());
        verify(achievementService).getUserAchievementsByUserIds("bad-token", request);
    }

    @Test
    void testListLearnerAchievementsByUserIds_emptyRequest_returnsBadRequest() {
        // empty request body → forwarded to service, service returns BAD_REQUEST
        Map<String, Object> request = new HashMap<>();
        ApiResponse apiResponse = new ApiResponse();
        apiResponse.setResponseCode(HttpStatus.BAD_REQUEST);
        when(achievementService.getUserAchievementsByUserIds("token", request))
                .thenReturn(apiResponse);

        ResponseEntity<?> response =
                achievementController.listLearnerAchievementsByUserIds("token", request);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        verify(achievementService).getUserAchievementsByUserIds("token", request);
    }

    @Test
    void testListLearnerAchievementsByUserIds_serviceReturnsInternalServerError() {
        // service returns 500 → controller propagates it unchanged
        Map<String, Object> request = new HashMap<>();
        request.put(Constants.REQUEST, Map.of(Constants.ACHIEVEMENT_IDS, List.of("achv-1")));
        ApiResponse apiResponse = new ApiResponse();
        apiResponse.setResponseCode(HttpStatus.INTERNAL_SERVER_ERROR);
        when(achievementService.getUserAchievementsByUserIds(anyString(), anyMap()))
                .thenReturn(apiResponse);

        ResponseEntity<?> response =
                achievementController.listLearnerAchievementsByUserIds("token", request);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertSame(apiResponse, response.getBody());
    }

    @Test
    void testListLearnerAchievementsByUserIds_responseBodyForwardedUnchanged() {
        // whatever the service returns must be forwarded as-is (no wrapping / mutation)
        Map<String, Object> request = new HashMap<>();
        ApiResponse apiResponse = new ApiResponse();
        apiResponse.setResponseCode(HttpStatus.OK);
        apiResponse.put(Constants.RESULT,
                Map.of(Constants.SEARCH_RESULTS,
                        Map.of(Constants.DATA, List.of(), Constants.TOTAL_COUNT, 0)));
        when(achievementService.getUserAchievementsByUserIds(anyString(), anyMap()))
                .thenReturn(apiResponse);

        ResponseEntity<?> response =
                achievementController.listLearnerAchievementsByUserIds("token", request);

        assertSame(apiResponse, response.getBody());
    }

    @Test
    void testListLearnerAchievementsByUserIds_requestPassedDirectlyToService() {
        // the controller must pass the exact same request map object it received — no transformation
        Map<String, Object> request = new HashMap<>();
        request.put(Constants.REQUEST, Map.of(Constants.ACHIEVEMENT_IDS, List.of("achv-1")));
        ApiResponse apiResponse = new ApiResponse();
        apiResponse.setResponseCode(HttpStatus.OK);
        when(achievementService.getUserAchievementsByUserIds(anyString(), anyMap()))
                .thenReturn(apiResponse);

        achievementController.listLearnerAchievementsByUserIds("token", request);

        // verify the exact same map reference is passed through without any extraction/modification
        verify(achievementService).getUserAchievementsByUserIds("token", request);
    }

    @Test
    void testListLearnerAchievementsForUser_success() {
        ApiResponse apiResponse = new ApiResponse();
        apiResponse.setResponseCode(HttpStatus.OK);
        Map<String, Object> result = new HashMap<>();
        result.put("achievements", "data");
        apiResponse.put("result", result);
        when(achievementService.getUserAchievements(anyString(), any())).thenReturn(apiResponse);
        ResponseEntity<?> response = achievementController.listLearnerAchievementsForUser("token");
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(apiResponse, response.getBody());
        verify(achievementService).getUserAchievements("token", "");
    }

    @Test
    void testListLearnerAchievementsForUser_unauthorized() {
        ApiResponse apiResponse = new ApiResponse();
        apiResponse.setResponseCode(HttpStatus.UNAUTHORIZED);
        when(achievementService.getUserAchievements(anyString(), any())).thenReturn(apiResponse);
        ResponseEntity<?> response = achievementController.listLearnerAchievementsForUser("invalid_token" );
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertEquals(apiResponse, response.getBody());
        verify(achievementService).getUserAchievements("invalid_token", "");
    }

    @Test
    void testListLearnerAchievementsForUser_badRequest() {
        ApiResponse apiResponse = new ApiResponse();
        apiResponse.setResponseCode(HttpStatus.BAD_REQUEST);
        when(achievementService.getUserAchievements(anyString(), any())).thenReturn(apiResponse);
        ResponseEntity<?> response = achievementController.listLearnerAchievementsForUser("token");
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals(apiResponse, response.getBody());
        verify(achievementService).getUserAchievements(eq("token"), any());
    }
}
