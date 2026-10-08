package com.igot.cb.profile.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.igot.cb.profile.service.ProfileService;
import com.igot.cb.util.ApiResponse;
import com.igot.cb.util.Constants;
import com.igot.cb.util.ProjectUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.HashMap;
import java.util.Map;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
 class ProfileControllerTest {

    private MockMvc mockMvc;

    @Mock
    private ProfileService profileService;

    @InjectMocks
    private ProfileController profileController;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
     void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(profileController).build();
    }

    @Test
     void testSaveExtendedProfile() throws Exception {

        String authToken = "test-auth-token";
        Map<String, Object> request = new HashMap<>();
        request.put("field1", "value1");

        ApiResponse mockResponse = ProjectUtil.createDefaultResponse("SAVE_EXTENDED_PROFILE");
        when(profileService.saveExtendedProfile((request), (authToken))).thenReturn(mockResponse);

        mockMvc.perform(post("/user/profile/extended")
                        .header(Constants.X_AUTH_TOKEN, authToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        verify(profileService).saveExtendedProfile((request), (authToken));
    }

    @Test
     void testGetExtendedProfileSummary() throws Exception {
        String authToken = "test-auth-token";
        String userId = "user-123";
        ApiResponse mockResponse = ProjectUtil.createDefaultResponse("GET_EXTENDED_PROFILE");

        when(profileService.getExtendedProfileSummary((userId), (authToken))).thenReturn(mockResponse);

        mockMvc.perform(get("/user/profile/extended/all/{userId}", userId)
                        .header(Constants.X_AUTH_TOKEN, authToken))
                .andExpect(status().isOk());

        verify(profileService).getExtendedProfileSummary((userId), (authToken));
    }


    @Test
     void testGetServiceHistory() throws Exception {
        String authToken = "test-auth-token";
        String userId = "user-123";
        ApiResponse mockResponse = ProjectUtil.createDefaultResponse("GET_SERVICE_HISTORY");

        when(profileService.readFullExtendedProfile((userId), (Constants.SERVICE_HISTORY), (authToken)))
                .thenReturn(mockResponse);

        mockMvc.perform(get("/user/profile/extended/serviceHistory/{userId}", userId)
                        .header(Constants.X_AUTH_TOKEN, authToken))
                .andExpect(status().isOk());

        verify(profileService).readFullExtendedProfile((userId), (Constants.SERVICE_HISTORY), (authToken));
    }

    @Test
     void testGetEducationalQualifications() throws Exception {
        String authToken = "test-auth-token";
        String userId = "user-123";
        ApiResponse mockResponse = ProjectUtil.createDefaultResponse("GET_EDUCATION");

        when(profileService.readFullExtendedProfile((userId), (Constants.EDUCATION_QUALIFICATION), (authToken)))
                .thenReturn(mockResponse);

        mockMvc.perform(get("/user/profile/extended/education/{userId}", userId)
                        .header(Constants.X_AUTH_TOKEN, authToken))
                .andExpect(status().isOk());

        verify(profileService).readFullExtendedProfile((userId), (Constants.EDUCATION_QUALIFICATION), (authToken));
    }

    @Test
     void testGetLocationDetails() throws Exception {
        String authToken = "test-auth-token";
        String userId = "user-123";
        ApiResponse mockResponse = ProjectUtil.createDefaultResponse("GET_LOCATION");

        when(profileService.readFullExtendedProfile((userId), (Constants.LOCATION_DETAILS), (authToken)))
                .thenReturn(mockResponse);

        mockMvc.perform(get("/user/profile/extended/locationDetails/{userId}", userId)
                        .header(Constants.X_AUTH_TOKEN, authToken))
                .andExpect(status().isOk());

        verify(profileService).readFullExtendedProfile((userId), (Constants.LOCATION_DETAILS), (authToken));
    }

    @Test
     void testGetAchievements() throws Exception {
        String authToken = "test-auth-token";
        String userId = "user-123";
        ApiResponse mockResponse = ProjectUtil.createDefaultResponse("GET_ACHIEVEMENTS");

        when(profileService.readFullExtendedProfile((userId), (Constants.ACHIEVEMENTS), (authToken)))
                .thenReturn(mockResponse);

        mockMvc.perform(get("/user/profile/extended/achievements/{userId}", userId)
                        .header(Constants.X_AUTH_TOKEN, authToken))
                .andExpect(status().isOk());

        verify(profileService).readFullExtendedProfile((userId), (Constants.ACHIEVEMENTS), (authToken));
    }

    @Test
     void testUpdateExtendedProfile() throws Exception {
        String authToken = "test-auth-token";
        Map<String, Object> request = new HashMap<>();
        request.put("field1", "updatedValue");

        ApiResponse mockResponse = ProjectUtil.createDefaultResponse("UPDATE_EXTENDED_PROFILE");

        when(profileService.updateExtendedProfile((request), (authToken))).thenReturn(mockResponse);

        mockMvc.perform(put("/user/profile/extended")
                        .header(Constants.X_AUTH_TOKEN, authToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        verify(profileService).updateExtendedProfile((request), (authToken));
    }

    @Test
     void testDeleteExtendedProfile() throws Exception {
        String authToken = "test-auth-token";
        Map<String, Object> request = new HashMap<>();
        request.put("profileId", "123");

        ApiResponse mockResponse = ProjectUtil.createDefaultResponse("DELETE_EXTENDED_PROFILE");

        when(profileService.deleteExtendedProfile((request), (authToken))).thenReturn(mockResponse);

        mockMvc.perform(delete("/user/profile/extended")
                        .header(Constants.X_AUTH_TOKEN, authToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        verify(profileService).deleteExtendedProfile((request), (authToken));
    }


    @Test
     void testGetCompetencies() throws Exception {
        String authToken = "test-auth-token";
        String userId = "user-123";

        ApiResponse mockResponse = ProjectUtil.createDefaultResponse("GET_COMPETENCIES");

        when(profileService.listCompetencies((userId), (authToken))).thenReturn(mockResponse);

        mockMvc.perform(get("/user/profile/extended/competencies/{userId}", userId)
                        .header(Constants.X_AUTH_TOKEN, authToken))
                .andExpect(status().isOk());

     verify(profileService).listCompetencies((userId), (authToken));
     }

     // ==================== BASIC PROFILE ENDPOINT TESTS ====================

     /**
      * Test: Get authenticated user's basic profile (own profile)
      * Endpoint: GET /user/profile/v1/basic
      * Uses isNgo = true (userId extracted from token)
      */
     @Test
     void testGetBasicProfileForAuthenticatedUser() throws Exception {
         String authToken = "test-auth-token";

         ApiResponse mockResponse = ProjectUtil.createDefaultResponse("GET_BASIC_PROFILE");

         when(profileService.getBasicProfile("", authToken, true)).thenReturn(mockResponse);

         mockMvc.perform(get("/user/profile/v1/basic")
                         .header(Constants.X_AUTH_TOKEN, authToken))
                 .andExpect(status().isOk());

         verify(profileService).getBasicProfile("", authToken, true);
     }

     /**
      * Test: Get specific user's basic profile
      * Endpoint: GET /user/profile/basic/{userId}
      * Uses isNgo = false (userId from path parameter)
      */
     @Test
     void testGetBasicProfileByUserId() throws Exception {
         String authToken = "test-auth-token";
         String userId = "user-123";

         ApiResponse mockResponse = ProjectUtil.createDefaultResponse("GET_BASIC_PROFILE");

         when(profileService.getBasicProfile(userId, authToken, false)).thenReturn(mockResponse);

         mockMvc.perform(get("/user/profile/basic/{userId}", userId)
                         .header(Constants.X_AUTH_TOKEN, authToken))
                 .andExpect(status().isOk());

         verify(profileService).getBasicProfile(userId, authToken, false);
     }

     /**
      * Test: Get specific user's basic profile with different userId
      * Endpoint: GET /user/profile/basic/{userId}
      * Verifies parameter passing and response handling
      */
     @Test
     void testGetBasicProfileByUserIdDifferentUser() throws Exception {
         String authToken = "test-auth-token";
         String userId = "user-456";

         ApiResponse mockResponse = ProjectUtil.createDefaultResponse("GET_BASIC_PROFILE");

         when(profileService.getBasicProfile(userId, authToken, false)).thenReturn(mockResponse);

         mockMvc.perform(get("/user/profile/basic/{userId}", userId)
                         .header(Constants.X_AUTH_TOKEN, authToken))
                 .andExpect(status().isOk());

         verify(profileService).getBasicProfile(userId, authToken, false);
     }
     /**
      * Test: Get extended profile summary for authenticated user
      * Endpoint: GET /user/profile/v1/extended/all/
      * Uses empty userId (extracted from token)
      */
     @Test
     void testGetUserExtendedProfileSummary() throws Exception {
         String authToken = "test-auth-token";

         ApiResponse mockResponse = ProjectUtil.createDefaultResponse("GET_USER_EXTENDED_PROFILE");

         when(profileService.getExtendedProfileSummary("", authToken)).thenReturn(mockResponse);

         mockMvc.perform(get("/user/profile/v1/extended/all/")
                         .header(Constants.X_AUTH_TOKEN, authToken))
                 .andExpect(status().isOk());

         verify(profileService).getExtendedProfileSummary("", authToken);
     }

     /**
      * Test: Get additional fields for authenticated user by org
      * Endpoint: GET /user/profile/v1/getAdditionalFields
      * Uses empty userId (extracted from token) and userOrgId from header
      */
     @Test
     void testGetAdditionalFieldsByOrgForUser() throws Exception {
         String authToken = "test-auth-token";
         String userOrgId = "org-123";

         ApiResponse mockResponse = ProjectUtil.createDefaultResponse("GET_ADDITIONAL_FIELDS");

         when(profileService.getAdditionalFieldsByOrg("", userOrgId, authToken, true)).thenReturn(mockResponse);

         mockMvc.perform(get("/user/profile/v1/getAdditionalFields")
                         .header(Constants.X_AUTH_TOKEN, authToken)
                         .header(Constants.X_AUTH_USER_ORG_ID, userOrgId))
                 .andExpect(status().isOk());

         verify(profileService).getAdditionalFieldsByOrg("", userOrgId, authToken, true);
     }

     /**
      * Test: Get additional fields with different org
      * Verifies parameter passing for different organization
      */
     @Test
     void testGetAdditionalFieldsByOrgForUserDifferentOrg() throws Exception {
         String authToken = "test-auth-token";
         String userOrgId = "org-456";

         ApiResponse mockResponse = ProjectUtil.createDefaultResponse("GET_ADDITIONAL_FIELDS");

         when(profileService.getAdditionalFieldsByOrg("", userOrgId, authToken, true)).thenReturn(mockResponse);

         mockMvc.perform(get("/user/profile/v1/getAdditionalFields")
                         .header(Constants.X_AUTH_TOKEN, authToken)
                         .header(Constants.X_AUTH_USER_ORG_ID, userOrgId))
                 .andExpect(status().isOk());

         verify(profileService).getAdditionalFieldsByOrg("", userOrgId, authToken, true);
     }

     /**
      * Test: Update additional fields for a user's profile.
      * Endpoint: POST /user/profile/update/additionalFields
      */
     @Test
     void testUpdateAdditionalFields() throws Exception {
         String authToken = "test-auth-token";
         String orgId = "org-123";
         Map<String, Object> request = new HashMap<>();
         request.put("field1", "value1");

         ApiResponse mockResponse = ProjectUtil.createDefaultResponse("UPDATE_ADDITIONAL_FIELDS");

         when(profileService.updateAdditionalFields(request, orgId, authToken)).thenReturn(mockResponse);

         mockMvc.perform(post("/user/profile/update/additionalFields")
                         .header(Constants.X_AUTH_TOKEN, authToken)
                         .header(Constants.X_AUTH_USER_ORG_ID, orgId)
                         .contentType(MediaType.APPLICATION_JSON)
                         .content(objectMapper.writeValueAsString(request)))
                 .andExpect(status().isOk());

         verify(profileService).updateAdditionalFields(request, orgId, authToken);
     }

     /**
      * Test: Get additional fields for a specific user by org (admin path variant).
      * Endpoint: GET /user/profile/getAdditionalFields/{userId}/{orgId}
      */
     @Test
     void testGetAdditionalFieldsByOrg() throws Exception {
         String authToken = "test-auth-token";
         String userId = "user-123";
         String orgId = "org-123";

         ApiResponse mockResponse = ProjectUtil.createDefaultResponse("GET_ADDITIONAL_FIELDS_BY_ORG");

         when(profileService.getAdditionalFieldsByOrg(userId, orgId, authToken, false)).thenReturn(mockResponse);

         mockMvc.perform(get("/user/profile/getAdditionalFields/{userId}/{orgId}", userId, orgId)
                         .header(Constants.X_AUTH_TOKEN, authToken))
                 .andExpect(status().isOk());

         verify(profileService).getAdditionalFieldsByOrg(userId, orgId, authToken, false);
     }

     /**
      * Test: Get basic profile for public endpoint (v2), which delegates to the v1 volunteer handler.
      * Endpoint: GET /user/profile/v2/basic
      */
     @Test
     void testGetBasicProfileForPublic() throws Exception {
         String authToken = "test-auth-token";

         ApiResponse mockResponse = ProjectUtil.createDefaultResponse("GET_BASIC_PROFILE");

         when(profileService.getBasicProfile("", authToken, true)).thenReturn(mockResponse);

         mockMvc.perform(get("/user/profile/v2/basic")
                         .header(Constants.X_AUTH_TOKEN, authToken))
                 .andExpect(status().isOk());

         verify(profileService).getBasicProfile("", authToken, true);
     }

     /**
      * Test: Get extended profile summary (v2), which delegates to the v1 ngo handler.
      * Endpoint: GET /user/profile/v2/extended/all
      */
     @Test
     void testGetExtendedProfileSummaryForUserV2() throws Exception {
         String authToken = "test-auth-token";

         ApiResponse mockResponse = ProjectUtil.createDefaultResponse("GET_EXTENDED_PROFILE");

         when(profileService.getExtendedProfileSummary("", authToken)).thenReturn(mockResponse);

         mockMvc.perform(get("/user/profile/v2/extended/all")
                         .header(Constants.X_AUTH_TOKEN, authToken))
                 .andExpect(status().isOk());

         verify(profileService).getExtendedProfileSummary("", authToken);
     }

     /**
      * Test: Get additional fields by org (v2), which delegates to the v1 ngo handler.
      * Endpoint: GET /user/profile/v2/getAdditionalFields
      */
     @Test
     void testGetAdditionalFieldsByOrgForUserV2() throws Exception {
         String authToken = "test-auth-token";
         String userOrgId = "org-789";

         ApiResponse mockResponse = ProjectUtil.createDefaultResponse("GET_ADDITIONAL_FIELDS");

         when(profileService.getAdditionalFieldsByOrg("", userOrgId, authToken, true)).thenReturn(mockResponse);

         mockMvc.perform(get("/user/profile/v2/getAdditionalFields")
                         .header(Constants.X_AUTH_TOKEN, authToken)
                         .header(Constants.X_AUTH_USER_ORG_ID, userOrgId))
                 .andExpect(status().isOk());

         verify(profileService).getAdditionalFieldsByOrg("", userOrgId, authToken, true);
     }

 }
