package com.igot.cb.masterdata.service;

import com.igot.cb.masterdata.model.Degree;
import com.igot.cb.masterdata.model.Institute;
import com.igot.cb.masterdata.repository.DegreeRepository;
import com.igot.cb.masterdata.repository.InstituteRepository;
import com.igot.cb.transactional.elasticsearch.model.EsResponse;
import com.igot.cb.transactional.elasticsearch.service.EsUtilService;
import com.igot.cb.util.ApiResponse;
import com.igot.cb.util.CbServerProperties;
import com.igot.cb.util.Constants;
import com.igot.cb.util.ProjectUtil;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.*;
import org.springframework.http.HttpStatus;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MasterDataServiceV2ImplTest {

    @Mock
    DegreeRepository degreeRepository;

    @Mock
    InstituteRepository instituteRepository;

    @Mock
    ValidationService validationService;

    @Mock
    EsUtilService esUtilService;

    @Mock
    CbServerProperties serverProperties;

    @Mock
    RestHighLevelClient igotESClient;

    @InjectMocks
    @Spy
    MasterDataServiceV2Impl masterDataService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    // ---------------------- UPSERT DEGREE ----------------------
    @Test
    void testUpsertDegree_createSuccess() {
        Map<String, Object> requestMap = Map.of(
                Constants.NAME, "MBA",
                Constants.DESCRIPTION, "Master of Business Administration"
        );

        Map<String, Object> requestBody = Map.of(
                Constants.REQUEST, requestMap
        );

        when(validationService.upsertDegreeValidation(any(), any())).thenReturn(true);
        when(degreeRepository.findByNameIgnoreCase("MBA")).thenReturn(Optional.empty());
        when(degreeRepository.save(any(Degree.class))).thenAnswer(inv -> {
            Degree d = inv.getArgument(0);
            d.setId(100L);
            return d;
        });
        when(serverProperties.getEsDegreeIndexName()).thenReturn("degree_index");
        when(serverProperties.getEsMasterDataIndexDocType()).thenReturn("_doc");
        when(esUtilService.saveObjectInIgotES(any(), anyString(), anyString(), anyString()))
                .thenReturn(EsResponse.builder().success(true).build());

        ApiResponse response = masterDataService.upsertDegree(requestBody);

        assertNotNull(response.getResult().get(Constants.RESULT));
        Degree savedDegree = (Degree) response.getResult().get(Constants.RESULT);
        assertEquals("MBA", savedDegree.getName());
        assertEquals("Master of Business Administration", savedDegree.getDescription());
    }

    @Test
    void testUpsertDegree_updateSuccess() {
        Degree existingDegree = new Degree();
        existingDegree.setId(1L);
        existingDegree.setName("MBA");
        existingDegree.setStatus(1);

        Map<String, Object> requestBody = Map.of(
                Constants.REQUEST, Map.of(
                        Constants.ID, 1L,
                        Constants.NAME, "MBA Updated"
                )
        );

        when(validationService.upsertDegreeValidation(any(), any())).thenReturn(true);
        when(degreeRepository.findById(1L)).thenReturn(Optional.of(existingDegree));
        when(degreeRepository.save(any(Degree.class))).thenAnswer(inv -> inv.getArgument(0));
        when(serverProperties.getEsDegreeIndexName()).thenReturn("degree_index");
        when(serverProperties.getEsMasterDataIndexDocType()).thenReturn("_doc");
        when(esUtilService.saveObjectInIgotES(any(), anyString(), anyString(), anyString()))
                .thenReturn(EsResponse.builder().success(true).build());

        ApiResponse response = masterDataService.upsertDegree(requestBody);

        assertEquals("MBA Updated", ((Degree) response.getResult().get(Constants.RESULT)).getName());
    }

    @Test
    void testUpsertDegree_conflict() {
        // Prepare request
        Map<String, Object> requestBody = Map.of(
                Constants.REQUEST, Map.of(Constants.NAME, "MBA")
        );
        when(validationService.upsertDegreeValidation(any(ApiResponse.class), anyMap())).thenReturn(true);
        Degree existingDegree = new Degree();
        existingDegree.setName("MBA");
        when(degreeRepository.findByNameIgnoreCase("MBA")).thenReturn(Optional.of(existingDegree));
        ApiResponse response = masterDataService.upsertDegree(requestBody);
        assertEquals("Degree already exists with the name: " + existingDegree.getName(), response.getParams().getErrMsg());
        assertEquals(HttpStatus.CONFLICT, response.getResponseCode());
    }

    @Test
    void testUpsertDegree_validationFails() {
        Map<String, Object> requestBody = Map.of(Constants.REQUEST, Map.of(Constants.NAME, "MBA"));
        when(validationService.upsertDegreeValidation(any(), any())).thenReturn(false);
        ApiResponse response = masterDataService.upsertDegree(requestBody);
        assertNotNull(response);
    }

    @Test
    void testUpsertDegree_esFailure() {
        Map<String, Object> requestBody = Map.of(Constants.REQUEST, Map.of(Constants.NAME, "MBA"));

        when(validationService.upsertDegreeValidation(any(), any())).thenReturn(true);
        when(degreeRepository.findByNameIgnoreCase("MBA")).thenReturn(Optional.empty());
        when(degreeRepository.save(any(Degree.class))).thenAnswer(inv -> {
            Degree d = inv.getArgument(0);
            d.setId(1L);
            return d;
        });
        when(serverProperties.getEsDegreeIndexName()).thenReturn("degree_index");
        when(serverProperties.getEsMasterDataIndexDocType()).thenReturn("_doc");
        when(esUtilService.saveObjectInIgotES(any(), anyString(), anyString(), anyString()))
                .thenReturn(EsResponse.builder().success(false).message("ES failure").build());

        ApiResponse response = masterDataService.upsertDegree(requestBody);
        assertEquals("Failed to add degree (ES indexing failed)", response.getParams().getErrMsg());
    }

    @Test
    void testUpsertDegree_unexpectedException() {
        Map<String, Object> requestBody = Map.of(Constants.REQUEST, Map.of(Constants.NAME, "MBA"));
        when(validationService.upsertDegreeValidation(any(), any())).thenThrow(new RuntimeException("DB crash"));
        ApiResponse response = masterDataService.upsertDegree(requestBody);
        assertEquals("Unexpected error while adding/updating degree", response.getParams().getErrMsg());
    }

    @Test
    void testUpsertDegree_updateNotFound() {
        Map<String, Object> body = Map.of(
                Constants.REQUEST, Map.of(Constants.ID, 1L)
        );
        when(validationService.upsertDegreeValidation(any(), any())).thenReturn(true);
        when(degreeRepository.findById(1L)).thenReturn(Optional.empty());
        ApiResponse response = masterDataService.upsertDegree(body);
        assertEquals("Degree with given ID not found", response.getParams().getErrMsg());
    }

    // ---------------------- UPSERT INSTITUTE ----------------------

    @Test
    void testUpsertInstitute_createSuccess() {
        Map<String, Object> requestMap = Map.of(
                Constants.NAME, "IIT",
                Constants.DESCRIPTION, "Indian Institute of Technology"
        );
        Map<String, Object> requestBody = Map.of(
                Constants.REQUEST, requestMap
        );
        when(validationService.upsertInstituteValidation(any(), any())).thenReturn(true);
        when(instituteRepository.findByNameIgnoreCase("IIT")).thenReturn(Optional.empty());
        when(instituteRepository.save(any(Institute.class))).thenAnswer(inv -> {
            Institute inst = inv.getArgument(0);
            inst.setId(100L);
            return inst;
        });
        when(serverProperties.getEsInstituteIndexName()).thenReturn("inst_index");
        when(serverProperties.getEsMasterDataIndexDocType()).thenReturn("_doc");
        when(esUtilService.saveObjectInIgotES(any(), anyString(), anyString(), anyString()))
                .thenReturn(EsResponse.builder().success(true).build());
        ApiResponse response = masterDataService.upsertInstitute(requestBody);
        assertNotNull(response.getResult().get(Constants.RESULT));
        Institute savedInstitute = (Institute) response.getResult().get(Constants.RESULT);
        assertEquals("IIT", savedInstitute.getName());
        assertEquals("Indian Institute of Technology", savedInstitute.getDescription());
    }

    @Test
    void testUpsertInstitute_updateNotFound() {
        Map<String, Object> body = Map.of(
                Constants.REQUEST, Map.of(Constants.ID, 10L)
        );
        when(validationService.upsertInstituteValidation(any(), any())).thenReturn(true);
        when(instituteRepository.findById(10L)).thenReturn(Optional.empty());
        ApiResponse response = masterDataService.upsertInstitute(body);
        assertEquals("Institute with given ID not found", response.getParams().getErrMsg());
    }

    @Test
    void testUpsertInstitute_esFailureOnUpdate() {
        Institute existing = new Institute();
        existing.setId(1L);
        existing.setName("IIT");
        Map<String, Object> body = Map.of(
                Constants.REQUEST, Map.of(Constants.ID, 1L, Constants.NAME, "IIT2")
        );
        when(validationService.upsertInstituteValidation(any(), any())).thenReturn(true);
        when(instituteRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(instituteRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(serverProperties.getEsInstituteIndexName()).thenReturn("idx");
        when(serverProperties.getEsMasterDataIndexDocType()).thenReturn("_doc");
        when(esUtilService.saveObjectInIgotES(any(), any(), any(), any()))
                .thenReturn(EsResponse.builder().success(false).message("ES fail").build());
        ApiResponse response = masterDataService.upsertInstitute(body);
        assertEquals("IIT2", ((Institute) response.getResult().get(Constants.RESULT)).getName());
    }

    @Test
    void testUpsertInstitute_updateSuccess() {
        Institute existing = new Institute();
        existing.setId(1L);
        existing.setName("IIT");
        existing.setStatus(1);

        Map<String, Object> requestBody = Map.of(
                Constants.REQUEST, Map.of(
                        Constants.ID, 1L,
                        Constants.NAME, "IIT Updated"
                )
        );

        when(validationService.upsertInstituteValidation(any(), any())).thenReturn(true);
        when(instituteRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(instituteRepository.save(any(Institute.class))).thenAnswer(inv -> inv.getArgument(0));
        when(serverProperties.getEsInstituteIndexName()).thenReturn("inst_index");
        when(serverProperties.getEsMasterDataIndexDocType()).thenReturn("_doc");
        when(esUtilService.saveObjectInIgotES(any(), anyString(), anyString(), anyString()))
                .thenReturn(EsResponse.builder().success(true).build());
        ApiResponse response = masterDataService.upsertInstitute(requestBody);
        assertEquals("IIT Updated", ((Institute) response.getResult().get(Constants.RESULT)).getName());
    }

    @Test
    void testUpsertInstitute_conflict() {
        Map<String, Object> requestBody = Map.of(
                Constants.REQUEST, Map.of(Constants.NAME, "IIT")
        );
        when(validationService.upsertInstituteValidation(any(), any())).thenReturn(true);
        when(instituteRepository.findByNameIgnoreCase("IIT")).thenReturn(Optional.of(new Institute()));
        ApiResponse response = masterDataService.upsertInstitute(requestBody);
        assertEquals(
                "Institute already exists with the name: IIT",
                response.getParams().getErrMsg()
        );
    }

    @Test
    void testUpsertInstitute_validationFails() {
        Map<String, Object> requestBody = Map.of(Constants.REQUEST, Map.of(Constants.NAME, "IIT"));
        when(validationService.upsertInstituteValidation(any(), any())).thenReturn(false);
        ApiResponse response = masterDataService.upsertInstitute(requestBody);
        assertNotNull(response);
    }

    @Test
    void testUpsertInstitute_esFailure() {
        Map<String, Object> requestBody = Map.of(Constants.REQUEST, Map.of(Constants.NAME, "IIT"));
        when(validationService.upsertInstituteValidation(any(), any())).thenReturn(true);
        when(instituteRepository.findByNameIgnoreCase("IIT")).thenReturn(Optional.empty());
        when(instituteRepository.save(any(Institute.class))).thenAnswer(inv -> {
            Institute inst = inv.getArgument(0);
            inst.setId(1L);
            return inst;
        });
        when(serverProperties.getEsInstituteIndexName()).thenReturn("inst_index");
        when(serverProperties.getEsMasterDataIndexDocType()).thenReturn("_doc");
        when(esUtilService.saveObjectInIgotES(any(), anyString(), anyString(), anyString()))
                .thenReturn(EsResponse.builder().success(false).message("ES failure").build());
        ApiResponse response = masterDataService.upsertInstitute(requestBody);
        assertEquals("Failed to add institute (ES indexing failed)", response.getParams().getErrMsg());
    }

    @Test
    void testUpsertInstitute_updateIdNotFound() {
        Map<String, Object> requestBody = Map.of(Constants.REQUEST, Map.of(Constants.ID, 99L, Constants.NAME, "IIT Updated"));
        when(validationService.upsertInstituteValidation(any(), any())).thenReturn(true);
        when(instituteRepository.findById(99L)).thenReturn(Optional.empty());
        ApiResponse response = masterDataService.upsertInstitute(requestBody);
        assertEquals("Institute with given ID not found", response.getParams().getErrMsg());
    }

    // ---------------------- SEARCH MASTER DATA IN ES ----------------------
    @Test
    void testSearchMasterDataInIgotES_success() {
        Map<String, Object> searchRequest = Map.of(
                Constants.PAGE_NUMBER, 0,
                Constants.PAGE_SIZE, 10,
                Constants.SEARCH_STRING, "MBA"
        );
        RestHighLevelClient esClient = mock(RestHighLevelClient.class);
        MasterDataServiceV2Impl service = new MasterDataServiceV2Impl(
                degreeRepository, instituteRepository, validationService, esUtilService, serverProperties, esClient);
        EsResponse esResp = EsResponse.builder().success(true).data(List.of(Map.of("name", "MBA"))).count(1L).build();
        MasterDataServiceV2Impl spyService = spy(service);
        doReturn(esResp).when(spyService).searchMasterDataInIgotES(anyString(), anyString(), anyMap());
        EsResponse result = spyService.searchMasterDataInIgotES("degree_index", "_doc", searchRequest);
        assertTrue(result.isSuccess());
        assertEquals(1L, result.getCount());
    }

    @Test
    void testSearchMasterData_esFailure() {
        Map<String, Object> requestBody = Map.of(
                Constants.TYPE, "degree",
                Constants.REQUEST, Map.of(Constants.PAGE_NUMBER, 0, Constants.PAGE_SIZE, 10)
        );
        when(validationService.validateSearchRequest(any(), any())).thenReturn(true);
        when(serverProperties.getMasterDataAllowedType()).thenReturn(List.of("degree", "institute"));
        when(serverProperties.getEsDegreeIndexName()).thenReturn("degree_index");
        when(serverProperties.getEsMasterDataIndexDocType()).thenReturn("_doc");
        EsResponse mockEsResponse = EsResponse.builder().success(false).message("ES failed").build();
        doReturn(mockEsResponse).when(masterDataService)
                .searchMasterDataInIgotES(eq("degree_index"), eq("_doc"), any(Map.class));
        ApiResponse response = masterDataService.searchMasterData(requestBody);
        assertEquals("ES failed", response.getParams().getErrMsg());
    }

    @Test
    void testSearchMasterData_validationFails() {
        Map<String, Object> requestBody = Map.of(Constants.TYPE, "degree");
        when(validationService.validateSearchRequest(any(), any())).thenReturn(false);
        ApiResponse response = masterDataService.searchMasterData(requestBody);
        assertNull(response.getResult().get(Constants.RESULT));
    }

    @Test
    void testSearchMasterData_illegalArgumentException() {
        Map<String, Object> requestBody = Map.of(Constants.TYPE, "degree", Constants.REQUEST, Map.of());

        when(validationService.validateSearchRequest(any(), any())).thenReturn(true);
        when(serverProperties.getMasterDataAllowedType()).thenReturn(List.of("degree"));
        when(serverProperties.getEsDegreeIndexName()).thenReturn("degree_index");
        when(serverProperties.getEsMasterDataIndexDocType()).thenReturn("_doc");
        doThrow(new IllegalArgumentException("bad pagination"))
                .when(masterDataService).searchMasterDataInIgotES(any(), any(), any());
        ApiResponse response = masterDataService.searchMasterData(requestBody);
        assertEquals("Invalid pagination or sorting parameters", response.getParams().getErrMsg());
    }

    @Test
    void testSearchMasterData_unexpectedException() {
        Map<String, Object> requestBody = Map.of(Constants.TYPE, "degree", Constants.REQUEST, Map.of());

        when(validationService.validateSearchRequest(any(), any())).thenReturn(true);
        when(serverProperties.getMasterDataAllowedType()).thenReturn(List.of("degree"));
        when(serverProperties.getEsDegreeIndexName()).thenReturn("degree_index");
        when(serverProperties.getEsMasterDataIndexDocType()).thenReturn("_doc");
        doThrow(new RuntimeException("boom"))
                .when(masterDataService).searchMasterDataInIgotES(any(), any(), any());
        ApiResponse response = masterDataService.searchMasterData(requestBody);
        assertEquals("Unexpected error during search", response.getParams().getErrMsg());
    }

    @Test
    void testSearchMasterData_missingType() {
        Map<String, Object> requestBody = Map.of(
                Constants.REQUEST, Map.of(Constants.PAGE_NUMBER, 0)
        );
        when(validationService.validateSearchRequest(any(), any())).thenReturn(true);
        ApiResponse response = masterDataService.searchMasterData(requestBody);
        assertEquals("Invalid type.", response.getParams().getErrMsg());
    }

    @Test
    void testSearchMasterData_successButNoData() {
        Map<String, Object> requestBody = Map.of(
                Constants.TYPE, "degree",
                Constants.REQUEST, Map.of(Constants.PAGE_NUMBER, 0, Constants.PAGE_SIZE, 10)
        );
        when(validationService.validateSearchRequest(any(), any())).thenReturn(true);
        when(serverProperties.getMasterDataAllowedType()).thenReturn(List.of("degree"));
        when(serverProperties.getEsDegreeIndexName()).thenReturn("degree_index");
        when(serverProperties.getEsMasterDataIndexDocType()).thenReturn("_doc");
        EsResponse esResp = EsResponse.builder()
                .success(true)
                .data(Collections.emptyList())
                .count(0L)
                .build();

        doReturn(esResp)
                .when(masterDataService)
                .searchMasterDataInIgotES(any(), any(), any());

        ApiResponse response = masterDataService.searchMasterData(requestBody);
        assertEquals(0L, response.getResult().get(Constants.COUNT));
        assertTrue(((List<?>) response.getResult().get(Constants.RESULT)).isEmpty());
    }

    @Test
    void testSearchMasterData_success_degree() {
        Map<String, Object> requestBody = Map.of(
                Constants.TYPE, "degree",
                Constants.REQUEST, Map.of(Constants.PAGE_NUMBER, 0, Constants.PAGE_SIZE, 10)
        );

        when(validationService.validateSearchRequest(any(), any())).thenReturn(true);
        when(serverProperties.getMasterDataAllowedType()).thenReturn(List.of("degree", "institute"));
        when(serverProperties.getEsDegreeIndexName()).thenReturn("degree_index");
        when(serverProperties.getEsMasterDataIndexDocType()).thenReturn("_doc");
        EsResponse mockEsResponse = EsResponse.builder()
                .success(true)
                .data(List.of(Map.of("name", "MBA")))
                .count(1L)
                .build();

        doReturn(mockEsResponse)
                .when(masterDataService)
                .searchMasterDataInIgotES(eq("degree_index"), eq("_doc"), any(Map.class));

        ApiResponse response = masterDataService.searchMasterData(requestBody);
        assertEquals(1L, response.getResult().get(Constants.COUNT));
        assertNotNull(response.getResult().get(Constants.RESULT));
    }

    @Test
    void testSearchMasterData_invalidType() {
        Map<String, Object> requestBody = Map.of(Constants.TYPE, "invalid", Constants.REQUEST, Map.of());
        when(validationService.validateSearchRequest(any(), any())).thenReturn(true);
        ApiResponse response = masterDataService.searchMasterData(requestBody);
        assertEquals("Invalid type.", response.getParams().getErrMsg());
    }

    @Test
    void testSearchMasterData_sortByNotAllowed() {
        Map<String, Object> requestBody = Map.of(
                Constants.TYPE, "degree",
                Constants.REQUEST, Map.of(
                        Constants.PAGE_NUMBER, 0,
                        Constants.PAGE_SIZE, 10,
                        Constants.SORT_BY, "unauthorizedField"
                )
        );
        when(serverProperties.getMasterDataAllowedType())
                .thenReturn(List.of("degree"));

        when(serverProperties.getMasterDataAllowedSortByFields())
                .thenReturn(List.of("name", "description", "id"));
        doAnswer(invocation -> {
            ApiResponse apiResponse = invocation.getArgument(0);
            ProjectUtil.errorResponse(
                    apiResponse,
                    "sortBy field is not allowed",
                    HttpStatus.BAD_REQUEST
            );
            return false;
        }).when(validationService)
                .validateSearchRequest(any(ApiResponse.class), any());
        ApiResponse response = masterDataService.searchMasterData(requestBody);
        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
        assertEquals("sortBy field is not allowed",
                response.getParams().getErrMsg());
        verify(validationService)
                .validateSearchRequest(any(ApiResponse.class), any());
    }

    @Test
    void testSearchMasterData_emptyRequestMap() {
        Map<String, Object> requestBody = Map.of(Constants.TYPE, "degree", Constants.REQUEST, Map.of());
        when(validationService.validateSearchRequest(any(), any())).thenReturn(true);
        when(serverProperties.getMasterDataAllowedType()).thenReturn(List.of("degree"));

        // simulate ES returning empty data
        doReturn(EsResponse.builder().success(true).data(List.of()).count(0L).build())
                .when(masterDataService).searchMasterDataInIgotES(any(), any(), any());

        ApiResponse response = masterDataService.searchMasterData(requestBody);
        assertEquals(0L, response.getResult().get(Constants.COUNT));
        assertTrue(((List<?>) response.getResult().get(Constants.RESULT)).isEmpty());
    }

    @Test
    void testSearchMasterData_success_institute() {
        Map<String, Object> requestBody = Map.of(
                Constants.TYPE, "institute",
                Constants.REQUEST, Map.of(Constants.PAGE_NUMBER, 0, Constants.PAGE_SIZE, 10)
        );
        when(validationService.validateSearchRequest(any(), any())).thenReturn(true);
        when(serverProperties.getMasterDataAllowedType()).thenReturn(List.of("degree", "institute"));
        when(serverProperties.getEsInstituteIndexName()).thenReturn("inst_index");
        when(serverProperties.getEsMasterDataIndexDocType()).thenReturn("_doc");

        EsResponse esResp = EsResponse.builder().success(true)
                .data(List.of(Map.of("name", "IIT")))
                .count(1L)
                .build();

        doReturn(esResp)
                .when(masterDataService)
                .searchMasterDataInIgotES(eq("inst_index"), eq("_doc"), any(Map.class));

        ApiResponse response = masterDataService.searchMasterData(requestBody);
        assertEquals(1L, response.getResult().get(Constants.COUNT));
        assertNotNull(response.getResult().get(Constants.RESULT));
    }

    @Test
    void testSearchMasterData_missingRequestKey() {
        Map<String, Object> requestBody = Map.of(Constants.TYPE, "degree"); // no REQUEST
        when(validationService.validateSearchRequest(any(), any())).thenReturn(true);
        when(serverProperties.getMasterDataAllowedType()).thenReturn(List.of("degree"));
        EsResponse esResp = EsResponse.builder().success(true).data(List.of()).count(0L).build();
        doReturn(esResp).when(masterDataService).searchMasterDataInIgotES(any(), any(), any());

        ApiResponse response = masterDataService.searchMasterData(requestBody);
        assertEquals(0L, response.getResult().get(Constants.COUNT));
    }

    @Test
    void testUpsertDegree_updateWithStatusOnly() {
        Degree existing = new Degree();
        existing.setId(1L);
        existing.setName("MBA");
        existing.setStatus(1);

        Map<String, Object> requestBody = Map.of(
                Constants.REQUEST, Map.of(
                        Constants.ID, 1L,
                        Constants.STATUS, 0
                )
        );

        when(validationService.upsertDegreeValidation(any(), any())).thenReturn(true);
        when(degreeRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(degreeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(serverProperties.getEsDegreeIndexName()).thenReturn("degree_index");
        when(serverProperties.getEsMasterDataIndexDocType()).thenReturn("_doc");
        when(esUtilService.saveObjectInIgotES(any(), any(), any(), any()))
                .thenReturn(EsResponse.builder().success(true).build());

        ApiResponse response = masterDataService.upsertDegree(requestBody);

        Degree result = (Degree) response.getResult().get(Constants.RESULT);
        assertEquals(0, result.getStatus());
    }

    @Test
    void testUpsertInstitute_updateWithStatusOnly() {
        Institute existing = new Institute();
        existing.setId(1L);
        existing.setName("IIT");
        existing.setStatus(1);

        Map<String, Object> requestBody = Map.of(
                Constants.REQUEST, Map.of(
                        Constants.ID, 1L,
                        Constants.STATUS, 0
                )
        );

        when(validationService.upsertInstituteValidation(any(), any())).thenReturn(true);
        when(instituteRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(instituteRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(serverProperties.getEsInstituteIndexName()).thenReturn("inst_index");
        when(serverProperties.getEsMasterDataIndexDocType()).thenReturn("_doc");
        when(esUtilService.saveObjectInIgotES(any(), any(), any(), any()))
                .thenReturn(EsResponse.builder().success(true).build());

        ApiResponse response = masterDataService.upsertInstitute(requestBody);

        Institute result = (Institute) response.getResult().get(Constants.RESULT);
        assertEquals(0, result.getStatus());
    }

    @Test
    void testSearchMasterData_successWithNullData() {
        Map<String, Object> requestBody = Map.of(
                Constants.TYPE, "degree",
                Constants.REQUEST, Map.of(Constants.PAGE_NUMBER, 0, Constants.PAGE_SIZE, 10)
        );

        when(validationService.validateSearchRequest(any(), any())).thenReturn(true);
        when(serverProperties.getMasterDataAllowedType()).thenReturn(List.of("degree"));
        when(serverProperties.getEsDegreeIndexName()).thenReturn("degree_index");
        when(serverProperties.getEsMasterDataIndexDocType()).thenReturn("_doc");

        EsResponse esResp = EsResponse.builder()
                .success(true)
                .data(null)
                .count(0L)
                .build();

        doReturn(esResp)
                .when(masterDataService)
                .searchMasterDataInIgotES(any(), any(), any());

        ApiResponse response = masterDataService.searchMasterData(requestBody);

        assertEquals(0L, response.getResult().get(Constants.COUNT));
    }

    @Test
    void testSearchMasterData_missingIndexName() {
        Map<String, Object> requestBody = Map.of(
                Constants.TYPE, "degree",
                Constants.REQUEST, Map.of(Constants.PAGE_NUMBER, 0)
        );
        when(validationService.validateSearchRequest(any(), any())).thenReturn(true);
        when(serverProperties.getMasterDataAllowedType()).thenReturn(List.of("degree"));
        when(serverProperties.getEsDegreeIndexName()).thenReturn(null);
        ApiResponse response = masterDataService.searchMasterData(requestBody);
        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
    }

    @Test
    void testUpsertDegree_esThrowsException() {
        Map<String, Object> requestBody = Map.of(
                Constants.REQUEST, Map.of(Constants.NAME, "MBA")
        );
        when(validationService.upsertDegreeValidation(any(), any())).thenReturn(true);
        when(degreeRepository.findByNameIgnoreCase("MBA")).thenReturn(Optional.empty());
        when(degreeRepository.save(any())).thenThrow(new RuntimeException("ES down"));
        ApiResponse response = masterDataService.upsertDegree(requestBody);
        assertEquals("Unexpected error while adding/updating degree",
                response.getParams().getErrMsg());
    }

    @Test
    void upsertDegree_update_esFailure() {
        Degree existing = new Degree();
        existing.setId(1L);
        existing.setName("MBA");
        when(validationService.upsertDegreeValidation(any(), any())).thenReturn(true);
        when(degreeRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(degreeRepository.save(any())).thenReturn(existing);
        when(esUtilService.saveObjectInIgotES(any(), any(), any(), any()))
                .thenReturn(EsResponse.builder().success(false).build());
        ApiResponse response = masterDataService.upsertDegree(
                Map.of(Constants.REQUEST, Map.of(Constants.ID, 1L))
        );
        assertNotNull(response);
    }

    @Test
    void searchMasterDataInIgotES_success() throws Exception {
        Map<String, Object> searchRequest = new HashMap<>();
        searchRequest.put(Constants.PAGE_NUMBER, 0);
        searchRequest.put(Constants.PAGE_SIZE, 10);
        SearchHit hit = mock(SearchHit.class);
        when(hit.getSourceAsMap()).thenReturn(Map.of("name", "MBA"));
        SearchHits hits = mock(SearchHits.class);
        when(hits.getHits()).thenReturn(new SearchHit[]{hit});
        when(hits.getTotalHits()).thenReturn(1L); // ✅ ES 6.x
        SearchResponse response = mock(SearchResponse.class);
        when(response.getHits()).thenReturn(hits);
        when(igotESClient.search(any(SearchRequest.class), eq(RequestOptions.DEFAULT)))
                .thenReturn(response);

        EsResponse esResponse = masterDataService.searchMasterDataInIgotES(
                "degree_index",
                "_doc",
                searchRequest
        );
        assertTrue(esResponse.isSuccess());
        assertEquals(1L, esResponse.getCount());
        assertEquals("MBA", (((Map<String, Object>)((List<Map<String, Object>>) esResponse.getData()).get(0)).get(Constants.NAME)));
    }

    @Test
    void searchMasterDataInIgotES_exception() throws Exception {

        when(igotESClient.search(any(SearchRequest.class), eq(RequestOptions.DEFAULT)))
                .thenThrow(new RuntimeException("ES down"));

        EsResponse response =
                masterDataService.searchMasterDataInIgotES("idx", "_doc", Map.of());

        assertFalse(response.isSuccess());
        assertTrue(response.getMessage().contains("Search failed"));
    }

    @Test
    void searchMasterData_keywordWithSort() throws Exception {

        Map<String, Object> searchRequest = new HashMap<>();
        searchRequest.put(Constants.SEARCH_STRING, "MBA");
        searchRequest.put(Constants.SORT_BY, Constants.NAME);
        searchRequest.put(Constants.ORDER_BY, "DESC");

        SearchHit hit = mock(SearchHit.class);
        when(hit.getSourceAsMap()).thenReturn(Map.of(Constants.NAME, "MBA"));

        SearchHits hits = mock(SearchHits.class);
        when(hits.getHits()).thenReturn(new SearchHit[]{hit});
        when(hits.getTotalHits()).thenReturn(1L);

        SearchResponse response = mock(SearchResponse.class);
        when(response.getHits()).thenReturn(hits);

        when(igotESClient.search(any(SearchRequest.class), eq(RequestOptions.DEFAULT)))
                .thenReturn(response);

        EsResponse esResponse = masterDataService.searchMasterDataInIgotES(
                "degree_index", "_doc", searchRequest
        );

        assertTrue(esResponse.isSuccess());
        assertEquals(1L, esResponse.getCount());
    }

    @Test
    void searchMasterData_keywordWithoutSort() throws Exception {

        Map<String, Object> searchRequest = Map.of(
                Constants.SEARCH_STRING, "MBA"
        );

        SearchHit hit = mock(SearchHit.class);
        when(hit.getSourceAsMap()).thenReturn(Map.of(Constants.NAME, "MBA"));

        SearchHits hits = mock(SearchHits.class);
        when(hits.getHits()).thenReturn(new SearchHit[]{hit});
        when(hits.getTotalHits()).thenReturn(1L);

        SearchResponse response = mock(SearchResponse.class);
        when(response.getHits()).thenReturn(hits);

        when(igotESClient.search(any(SearchRequest.class), eq(RequestOptions.DEFAULT)))
                .thenReturn(response);

        EsResponse result =
                masterDataService.searchMasterDataInIgotES("idx", "_doc", searchRequest);

        assertTrue(result.isSuccess());
    }

    @Test
    void searchMasterData_withFilters() throws Exception {

        Map<String, Object> searchRequest = new HashMap<>();
        searchRequest.put(Constants.FILTERS, Map.of(
                Constants.ID, 10L,
                Constants.STATUS, 1
        ));

        SearchHit hit = mock(SearchHit.class);
        when(hit.getSourceAsMap()).thenReturn(Map.of(Constants.ID, 10L));

        SearchHits hits = mock(SearchHits.class);
        when(hits.getHits()).thenReturn(new SearchHit[]{hit});
        when(hits.getTotalHits()).thenReturn(1L);

        SearchResponse response = mock(SearchResponse.class);
        when(response.getHits()).thenReturn(hits);

        when(igotESClient.search(any(SearchRequest.class), eq(RequestOptions.DEFAULT)))
                .thenReturn(response);

        EsResponse result =
                masterDataService.searchMasterDataInIgotES("idx", "_doc", searchRequest);

        assertTrue(result.isSuccess());
    }

    @Test
    void searchMasterData_defaultStatusFilter() throws Exception {

        Map<String, Object> searchRequest = new HashMap<>();

        SearchHit hit = mock(SearchHit.class);
        when(hit.getSourceAsMap()).thenReturn(Map.of(Constants.STATUS, 1));

        SearchHits hits = mock(SearchHits.class);
        when(hits.getHits()).thenReturn(new SearchHit[]{hit});
        when(hits.getTotalHits()).thenReturn(1L);

        SearchResponse response = mock(SearchResponse.class);
        when(response.getHits()).thenReturn(hits);

        when(igotESClient.search(any(SearchRequest.class), eq(RequestOptions.DEFAULT)))
                .thenReturn(response);

        EsResponse result =
                masterDataService.searchMasterDataInIgotES("idx", "_doc", searchRequest);

        assertTrue(result.isSuccess());
    }

    @Test
    void testUpdateDegree_duplicateNameConflict() {
        Degree existing = new Degree();
        existing.setId(1L);
        existing.setName("Old Name");

        Degree duplicate = new Degree();
        duplicate.setId(2L);
        duplicate.setName("MBA");

        Map<String, Object> requestBody = Map.of(
                Constants.REQUEST, Map.of(Constants.ID, 1L, Constants.NAME, "MBA")
        );

        when(validationService.upsertDegreeValidation(any(), any())).thenReturn(true);
        when(degreeRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(degreeRepository.findByNameIgnoreCase("MBA")).thenReturn(Optional.of(duplicate));

        ApiResponse response = masterDataService.upsertDegree(requestBody);

        assertEquals("Degree already exists with the name: MBA", response.getParams().getErrMsg());
        assertEquals(HttpStatus.CONFLICT, response.getResponseCode());
    }

    @Test
    void testUpdateDegree_duplicateNameSameIdNoConflict() {
        Degree existing = new Degree();
        existing.setId(1L);
        existing.setName("Old Name");

        Map<String, Object> requestBody = Map.of(
                Constants.REQUEST, Map.of(Constants.ID, 1L, Constants.NAME, "MBA")
        );

        when(validationService.upsertDegreeValidation(any(), any())).thenReturn(true);
        when(degreeRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(degreeRepository.findByNameIgnoreCase("MBA")).thenReturn(Optional.of(existing));
        when(degreeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(serverProperties.getEsDegreeIndexName()).thenReturn("degree_index");
        when(serverProperties.getEsMasterDataIndexDocType()).thenReturn("_doc");
        when(esUtilService.saveObjectInIgotES(any(), any(), any(), any()))
                .thenReturn(EsResponse.builder().success(true).build());

        ApiResponse response = masterDataService.upsertDegree(requestBody);

        Degree result = (Degree) response.getResult().get(Constants.RESULT);
        assertEquals("MBA", result.getName());
    }

    @Test
    void testUpdateInstitute_duplicateNameConflict() {
        Institute existing = new Institute();
        existing.setId(1L);
        existing.setName("Old Name");

        Institute duplicate = new Institute();
        duplicate.setId(2L);
        duplicate.setName("IIT");

        Map<String, Object> requestBody = Map.of(
                Constants.REQUEST, Map.of(Constants.ID, 1L, Constants.NAME, "IIT")
        );

        when(validationService.upsertInstituteValidation(any(), any())).thenReturn(true);
        when(instituteRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(instituteRepository.findByNameIgnoreCase("IIT")).thenReturn(Optional.of(duplicate));

        ApiResponse response = masterDataService.upsertInstitute(requestBody);

        assertEquals("Institute already exists with the name: IIT", response.getParams().getErrMsg());
        assertEquals(HttpStatus.CONFLICT, response.getResponseCode());
    }

    @Test
    void testUpdateInstitute_duplicateNameSameIdNoConflict() {
        Institute existing = new Institute();
        existing.setId(1L);
        existing.setName("Old Name");

        Map<String, Object> requestBody = Map.of(
                Constants.REQUEST, Map.of(Constants.ID, 1L, Constants.NAME, "IIT")
        );

        when(validationService.upsertInstituteValidation(any(), any())).thenReturn(true);
        when(instituteRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(instituteRepository.findByNameIgnoreCase("IIT")).thenReturn(Optional.of(existing));
        when(instituteRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(serverProperties.getEsInstituteIndexName()).thenReturn("inst_index");
        when(serverProperties.getEsMasterDataIndexDocType()).thenReturn("_doc");
        when(esUtilService.saveObjectInIgotES(any(), any(), any(), any()))
                .thenReturn(EsResponse.builder().success(true).build());

        ApiResponse response = masterDataService.upsertInstitute(requestBody);

        Institute result = (Institute) response.getResult().get(Constants.RESULT);
        assertEquals("IIT", result.getName());
    }

    @Test
    void searchMasterData_sortByDescriptionDesc() throws Exception {
        Map<String, Object> searchRequest = new HashMap<>();
        searchRequest.put(Constants.SORT_BY, Constants.DESCRIPTION);
        searchRequest.put(Constants.ORDER_BY, "DESC");

        SearchHit hit = mock(SearchHit.class);
        when(hit.getSourceAsMap()).thenReturn(Map.of(Constants.NAME, "MBA"));

        SearchHits hits = mock(SearchHits.class);
        when(hits.getHits()).thenReturn(new SearchHit[]{hit});
        when(hits.getTotalHits()).thenReturn(1L);

        SearchResponse response = mock(SearchResponse.class);
        when(response.getHits()).thenReturn(hits);

        when(igotESClient.search(any(SearchRequest.class), eq(RequestOptions.DEFAULT)))
                .thenReturn(response);

        EsResponse result = masterDataService.searchMasterDataInIgotES("idx", "_doc", searchRequest);

        assertTrue(result.isSuccess());
    }

    @Test
    void searchMasterData_withNameFilter() throws Exception {
        Map<String, Object> searchRequest = new HashMap<>();
        searchRequest.put(Constants.FILTERS, Map.of(Constants.NAME, "MBA"));

        SearchHit hit = mock(SearchHit.class);
        when(hit.getSourceAsMap()).thenReturn(Map.of(Constants.NAME, "MBA"));

        SearchHits hits = mock(SearchHits.class);
        when(hits.getHits()).thenReturn(new SearchHit[]{hit});
        when(hits.getTotalHits()).thenReturn(1L);

        SearchResponse response = mock(SearchResponse.class);
        when(response.getHits()).thenReturn(hits);

        when(igotESClient.search(any(SearchRequest.class), eq(RequestOptions.DEFAULT)))
                .thenReturn(response);

        EsResponse result = masterDataService.searchMasterDataInIgotES("idx", "_doc", searchRequest);

        assertTrue(result.isSuccess());
    }

    @Test
    void searchMasterData_withStatusFilterAsString() throws Exception {
        Map<String, Object> searchRequest = new HashMap<>();
        searchRequest.put(Constants.FILTERS, Map.of(Constants.STATUS, "1"));

        SearchHit hit = mock(SearchHit.class);
        when(hit.getSourceAsMap()).thenReturn(Map.of(Constants.STATUS, 1));

        SearchHits hits = mock(SearchHits.class);
        when(hits.getHits()).thenReturn(new SearchHit[]{hit});
        when(hits.getTotalHits()).thenReturn(1L);

        SearchResponse response = mock(SearchResponse.class);
        when(response.getHits()).thenReturn(hits);

        when(igotESClient.search(any(SearchRequest.class), eq(RequestOptions.DEFAULT)))
                .thenReturn(response);

        EsResponse result = masterDataService.searchMasterDataInIgotES("idx", "_doc", searchRequest);

        assertTrue(result.isSuccess());
    }

    @Test
    void searchMasterData_filtersNotAMap() throws Exception {
        Map<String, Object> searchRequest = new HashMap<>();
        searchRequest.put(Constants.FILTERS, "not-a-map");

        SearchHit hit = mock(SearchHit.class);
        when(hit.getSourceAsMap()).thenReturn(Map.of(Constants.NAME, "MBA"));

        SearchHits hits = mock(SearchHits.class);
        when(hits.getHits()).thenReturn(new SearchHit[]{hit});
        when(hits.getTotalHits()).thenReturn(1L);

        SearchResponse response = mock(SearchResponse.class);
        when(response.getHits()).thenReturn(hits);

        when(igotESClient.search(any(SearchRequest.class), eq(RequestOptions.DEFAULT)))
                .thenReturn(response);

        EsResponse result = masterDataService.searchMasterDataInIgotES("idx", "_doc", searchRequest);

        assertTrue(result.isSuccess());
    }

    @Test
    void searchMasterData_withPagination() throws Exception {

        Map<String, Object> searchRequest = Map.of(
                Constants.PAGE_NUMBER, 2,
                Constants.PAGE_SIZE, 5
        );

        SearchHit hit = mock(SearchHit.class);
        when(hit.getSourceAsMap()).thenReturn(Map.of("name", "Test"));

        SearchHits hits = mock(SearchHits.class);
        when(hits.getHits()).thenReturn(new SearchHit[]{hit});
        when(hits.getTotalHits()).thenReturn(1L);

        SearchResponse response = mock(SearchResponse.class);
        when(response.getHits()).thenReturn(hits);

        when(igotESClient.search(any(SearchRequest.class), eq(RequestOptions.DEFAULT)))
                .thenReturn(response);

        EsResponse result =
                masterDataService.searchMasterDataInIgotES("idx", "_doc", searchRequest);

        assertTrue(result.isSuccess());
    }
}
