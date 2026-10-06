package com.igot.cb.masterdata.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.igot.cb.masterdata.service.MasterDataService;
import com.igot.cb.masterdata.service.MasterDataServiceV2;
import com.igot.cb.util.ApiResponse;
import com.igot.cb.util.Constants;
import com.igot.cb.util.ProjectUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("unchecked")
class MasterDataControllerTest {

    private MockMvc mockMvc;

    @Mock
    private MasterDataService masterDataService;

    private MasterDataController masterDataController;

    @Mock
    private MasterDataServiceV2 masterDataServiceV2;

    @BeforeEach
    void setup() {
        MockitoAnnotations.openMocks(this);
        // @InjectMocks constructor resolution is unreliable here; construct explicitly so
        // the controller is guaranteed to hold these exact mock instances.
        masterDataController = new MasterDataController(masterDataService, masterDataServiceV2);
        mockMvc = MockMvcBuilders.standaloneSetup(masterDataController).build();
    }

    @Test
    void testGetInstitutionsList() throws Exception {
        String authToken = "test-auth-token";
        ApiResponse mockResponse = ProjectUtil.createDefaultResponse("TEST_API");
        mockResponse.setResponseCode(HttpStatus.OK);
        when(masterDataService.getInstitutionsList(authToken)).thenReturn(mockResponse);
        mockMvc.perform(get("/v1/masterdata/list/institutions")
                        .header(Constants.X_AUTH_TOKEN, authToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.responseCode").value(HttpStatus.OK.name()));
        verify(masterDataService, times(1)).getInstitutionsList(authToken);
    }

    @Test
    void testGetDegreesList() throws Exception {
        String authToken = "test-auth-token";
        ApiResponse mockResponse = ProjectUtil.createDefaultResponse("TEST_API");
        mockResponse.setResponseCode(HttpStatus.OK);
        when(masterDataService.getDegreesList(authToken)).thenReturn(mockResponse);
        mockMvc.perform(get("/v1/masterdata/list/degrees")
                        .header(Constants.X_AUTH_TOKEN, authToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.responseCode").value(HttpStatus.OK.name()));
        verify(masterDataService, times(1)).getDegreesList(authToken);
    }

    @Test
    void testUpdateInstitution() throws Exception {
        String authToken = "test-auth-token";
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("institutionName", "Test Institution");
        ApiResponse mockResponse = ProjectUtil.createDefaultResponse(Constants.API_UPDATE_INSTITUTION_LIST);
        mockResponse.setResponseCode(HttpStatus.CREATED);
        mockResponse.getResult().put("response", "Institution added successfully: Test Institution");
        when(masterDataService.updateInstitutionList(eq(authToken), any(Map.class))).thenReturn(mockResponse);
        mockMvc.perform(post("/v1/masterdata/update/institution")
                        .header(Constants.X_AUTH_TOKEN, authToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(new ObjectMapper().writeValueAsString(requestBody)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.responseCode").value(HttpStatus.CREATED.name()))
                .andExpect(jsonPath("$.result.response").value("Institution added successfully: Test Institution"));

        verify(masterDataService, times(1)).updateInstitutionList(eq(authToken), any(Map.class));
    }

    @Test
    void testUpdateDegree() throws Exception {
        String authToken = "test-auth-token";
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("degreeName", "Test Degree");
        ApiResponse mockResponse = ProjectUtil.createDefaultResponse(Constants.API_UPDATE_DEGREE_LIST);
        mockResponse.setResponseCode(HttpStatus.CREATED);
        mockResponse.getResult().put("response", "Degree added successfully: Test Degree");
        when(masterDataService.updateDegreesList(eq(authToken), any(Map.class))).thenReturn(mockResponse);
        mockMvc.perform(post("/v1/masterdata/update/degree")
                        .header(Constants.X_AUTH_TOKEN, authToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(new ObjectMapper().writeValueAsString(requestBody)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.responseCode").value(HttpStatus.CREATED.name()))
                .andExpect(jsonPath("$.result.response").value("Degree added successfully: Test Degree"));
        verify(masterDataService, times(1)).updateDegreesList(eq(authToken), any(Map.class));
    }

    @Test
    void testSearchMasterData_degree() {
        Map<String, Object> requestBody = Map.of(
                Constants.TYPE, "degree",
                Constants.REQUEST, Map.of("filters", Map.of("name", "MBA"))
        );

        ApiResponse mockResponse = new ApiResponse();
        mockResponse.setResponseCode(HttpStatus.OK);
        mockResponse.getResult().put(Constants.RESULT, List.of(Map.of("name", "MBA")));
        mockResponse.getResult().put(Constants.COUNT, 1L);
        when(masterDataServiceV2.searchMasterData(requestBody)).thenReturn(mockResponse);
        ResponseEntity<ApiResponse> response = masterDataController.searchMasterData(requestBody);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(1L, response.getBody().getResult().get(Constants.COUNT));
        assertNotNull(response.getBody().getResult().get(Constants.RESULT));
        verify(masterDataServiceV2).searchMasterData(requestBody);
    }

    @Test
    void testSearchMasterData_institute() {
        Map<String, Object> requestBody = Map.of(
                Constants.TYPE, "institute",
                Constants.REQUEST, Map.of("filters", Map.of("name", "IIT"))
        );

        ApiResponse mockResponse = new ApiResponse();
        mockResponse.setResponseCode(HttpStatus.OK);
        mockResponse.put(Constants.RESULT, List.of(Map.of("name", "IIT")));
        mockResponse.put(Constants.COUNT, 1L);

        when(masterDataServiceV2.searchMasterData(requestBody)).thenReturn(mockResponse);

        ResponseEntity<ApiResponse> response = masterDataController.searchMasterData(requestBody);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(1L, response.getBody().get(Constants.COUNT));
        assertNotNull(response.getBody().get(Constants.RESULT));
        verify(masterDataServiceV2).searchMasterData(requestBody);
    }

    @Test
    void testSearchMasterData_invalidType() {
        Map<String, Object> requestBody = Map.of(
                Constants.TYPE, "invalid",
                Constants.REQUEST, Map.of("filters", Map.of("name", "XYZ"))
        );

        ApiResponse mockResponse = new ApiResponse();
        mockResponse.setResponseCode(HttpStatus.BAD_REQUEST);
        mockResponse.getResult().put(Constants.RESULT, null);
        when(masterDataServiceV2.searchMasterData(requestBody)).thenReturn(mockResponse);
        ResponseEntity<ApiResponse> response = masterDataController.searchMasterData(requestBody);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        verify(masterDataServiceV2).searchMasterData(requestBody);
    }

    @Test
    void testUpsertDegree_success() {
        Map<String, Object> requestBody = Map.of(
                Constants.REQUEST, Map.of(Constants.NAME, "MBA")
        );

        ApiResponse mockResponse = new ApiResponse();
        mockResponse.setResponseCode(HttpStatus.OK);
        mockResponse.getResult().put(Constants.RESULT, Map.of("name", "MBA"));

        when(masterDataServiceV2.upsertDegree(requestBody)).thenReturn(mockResponse);

        ResponseEntity<ApiResponse> response = masterDataController.upsertDegree(requestBody);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("MBA", ((Map) response.getBody().getResult().get(Constants.RESULT)).get("name"));
        verify(masterDataServiceV2).upsertDegree(requestBody);
    }

    @Test
    void testUpsertDegree_conflict() {
        Map<String, Object> requestBody = Map.of(
                Constants.REQUEST, Map.of(Constants.NAME, "MBA")
        );

        ApiResponse mockResponse = new ApiResponse();
        mockResponse.setResponseCode(HttpStatus.CONFLICT);
        mockResponse.getResult().put(Constants.RESULT, null);

        when(masterDataServiceV2.upsertDegree(requestBody)).thenReturn(mockResponse);

        ResponseEntity<ApiResponse> response = masterDataController.upsertDegree(requestBody);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        verify(masterDataServiceV2).upsertDegree(requestBody);
    }

    @Test
    void testUpsertInstitute_success() {
        Map<String, Object> requestBody = Map.of(
                Constants.REQUEST, Map.of(Constants.NAME, "IIT")
        );

        ApiResponse mockResponse = new ApiResponse();
        mockResponse.setResponseCode(HttpStatus.OK);
        mockResponse.getResult().put(Constants.RESULT, Map.of("name", "IIT"));

        when(masterDataServiceV2.upsertInstitute(requestBody)).thenReturn(mockResponse);

        ResponseEntity<ApiResponse> response = masterDataController.upsertInstitute(requestBody);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("IIT", ((Map) response.getBody().getResult().get(Constants.RESULT)).get("name"));
        verify(masterDataServiceV2).upsertInstitute(requestBody);
    }

    @Test
    void testUpsertInstitute_conflict() {
        Map<String, Object> requestBody = Map.of(
                Constants.REQUEST, Map.of(Constants.NAME, "IIT")
        );

        ApiResponse mockResponse = new ApiResponse();
        mockResponse.setResponseCode(HttpStatus.CONFLICT);
        mockResponse.getResult().put(Constants.RESULT, null);

        when(masterDataServiceV2.upsertInstitute(requestBody)).thenReturn(mockResponse);

        ResponseEntity<ApiResponse> response = masterDataController.upsertInstitute(requestBody);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        verify(masterDataServiceV2).upsertInstitute(requestBody);
    }
}