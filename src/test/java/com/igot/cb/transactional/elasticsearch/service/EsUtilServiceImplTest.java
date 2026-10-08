package com.igot.cb.transactional.elasticsearch.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.igot.cb.transactional.elasticsearch.model.EsResponse;
import com.igot.cb.util.CbServerProperties;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.action.update.UpdateResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.lang.reflect.Field;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

class EsUtilServiceImplTest {

    @Mock
    private RestHighLevelClient mockClient;

    @Mock
    private CbServerProperties mockProperties;

    @InjectMocks
    private EsUtilServiceImpl esUtilService;

    private final ObjectMapper realObjectMapper =
            new ObjectMapper().registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());

    @BeforeEach
    void setUp() throws Exception {
        MockitoAnnotations.openMocks(this);
        when(mockProperties.getUserProfileIndex()).thenReturn("user-profile-index");

        // Inject cbProperties using reflection since @InjectMocks doesn't work with @Autowired fields
        Field cbPropertiesField = EsUtilServiceImpl.class.getDeclaredField("cbProperties");
        cbPropertiesField.setAccessible(true);
        cbPropertiesField.set(esUtilService, mockProperties);

        // Inject igotESClient (shares the same RestHighLevelClient mock) and a real ObjectMapper,
        // since @InjectMocks constructor resolution cannot populate every field automatically.
        Field igotEsClientField = EsUtilServiceImpl.class.getDeclaredField("igotESClient");
        igotEsClientField.setAccessible(true);
        igotEsClientField.set(esUtilService, mockClient);

        Field objectMapperField = EsUtilServiceImpl.class.getDeclaredField("objectMapper");
        objectMapperField.setAccessible(true);
        objectMapperField.set(esUtilService, realObjectMapper);
    }

    @Test
    void testUpdateUserOrgCustomFields_Success() throws Exception {
        // Arrange
        String userId = "user123";
        String orgId = "org123";
        List<Map<String, Object>> orgCustomFields = new ArrayList<>();

        // Mock successful call
        UpdateResponse mockResponse = mock(UpdateResponse.class);
        when(mockClient.update(any(UpdateRequest.class), eq(RequestOptions.DEFAULT))).thenReturn(mockResponse);

        // Act
        Boolean result = esUtilService.updateUserOrgCustomFields(userId, orgId, orgCustomFields);

        // Assert
        assertTrue(result);
        verify(mockClient, times(1)).update(any(UpdateRequest.class), eq(RequestOptions.DEFAULT));
    }

    @Test
    void testUpdateUserOrgCustomFields_Failure() throws Exception {
        // Arrange
        String userId = "user123";
        String orgId = "org123";
        List<Map<String, Object>> orgCustomFields = new ArrayList<>();

        // Mock exception
        doThrow(new RuntimeException("Update failed"))
                .when(mockClient).update(any(UpdateRequest.class), eq(RequestOptions.DEFAULT));

        // Act
        Boolean result = esUtilService.updateUserOrgCustomFields(userId, orgId, orgCustomFields);

        // Assert
        assertFalse(result);
        verify(mockClient, times(1)).update(any(UpdateRequest.class), eq(RequestOptions.DEFAULT));
    }

    @Test
    void testSaveObjectInIgotES_NullDocument_ReturnsFailure() {
        EsResponse response = esUtilService.saveObjectInIgotES(null, "test-index", "test-type", "doc123");

        assertNotNull(response);
        assertFalse(response.isSuccess());
        assertEquals("Document object is null", response.getMessage());
    }

    @Test
    void testSaveObjectInIgotES_EmptyDocument_ReturnsFailure() {
        Map<String, Object> emptyDoc = new HashMap<>();
        EsResponse response = esUtilService.saveObjectInIgotES(emptyDoc, "test-index", "test-type", "doc123");

        assertNotNull(response);
        assertFalse(response.isSuccess());
        assertEquals("Document object is null", response.getMessage());
    }

    @Test
    void testSaveObjectInIgotES_NullDocId_ReturnsFailure() {
        Map<String, Object> doc = new HashMap<>();
        doc.put("field", "value");
        EsResponse response = esUtilService.saveObjectInIgotES(doc, "test-index", "test-type", null);

        assertNotNull(response);
        assertFalse(response.isSuccess());
        assertEquals("Document ID must not be null or empty", response.getMessage());
    }

    @Test
    void testSaveObjectInIgotES_EmptyDocId_ReturnsFailure() {
        Map<String, Object> doc = new HashMap<>();
        doc.put("field", "value");
        EsResponse response = esUtilService.saveObjectInIgotES(doc, "test-index", "test-type", "");

        assertNotNull(response);
        assertFalse(response.isSuccess());
        assertEquals("Document ID must not be null or empty", response.getMessage());
    }

    @Test
    void testSaveObjectInIgotES_Success() throws Exception {
        Map<String, Object> doc = new HashMap<>();
        doc.put("field", "value");

        UpdateResponse mockUpdateResponse = mock(UpdateResponse.class);
        when(mockClient.update(any(UpdateRequest.class), eq(RequestOptions.DEFAULT))).thenReturn(mockUpdateResponse);

        EsResponse response = esUtilService.saveObjectInIgotES(doc, "test-index", "test-type", "doc123");

        assertNotNull(response);
        assertTrue(response.isSuccess());
        assertEquals("Document upserted successfully", response.getMessage());
        assertEquals("doc123", response.getDocumentId());
        verify(mockClient, times(1)).update(any(UpdateRequest.class), eq(RequestOptions.DEFAULT));
    }

    @Test
    void testSaveObjectInIgotES_ExceptionDuringUpdate_ReturnsFailure() throws Exception {
        Map<String, Object> doc = new HashMap<>();
        doc.put("field", "value");

        doThrow(new RuntimeException("ES connection failed"))
                .when(mockClient).update(any(UpdateRequest.class), eq(RequestOptions.DEFAULT));

        EsResponse response = esUtilService.saveObjectInIgotES(doc, "test-index", "test-type", "doc123");

        assertNotNull(response);
        assertFalse(response.isSuccess());
        assertTrue(response.getMessage().contains("Error upserting document"));
        assertTrue(response.getMessage().contains("ES connection failed"));
        verify(mockClient, times(1)).update(any(UpdateRequest.class), eq(RequestOptions.DEFAULT));
    }

    @Test
    void testSaveObjectInIgotES_PojoDocument_Success() throws Exception {
        // Use a plain POJO (not a Map) to exercise objectMapper.convertValue's POJO-to-Map path
        EsResponse doc = EsResponse.builder().success(true).message("payload").documentId("ignored").build();

        UpdateResponse mockUpdateResponse = mock(UpdateResponse.class);
        when(mockClient.update(any(UpdateRequest.class), eq(RequestOptions.DEFAULT))).thenReturn(mockUpdateResponse);

        EsResponse response = esUtilService.saveObjectInIgotES(doc, "another-index", "another-type", "doc456");

        assertNotNull(response);
        assertTrue(response.isSuccess());
        verify(mockClient, times(1)).update(any(UpdateRequest.class), eq(RequestOptions.DEFAULT));
    }
}
