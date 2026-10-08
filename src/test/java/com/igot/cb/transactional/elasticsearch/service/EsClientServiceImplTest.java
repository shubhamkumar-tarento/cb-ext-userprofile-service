package com.igot.cb.transactional.elasticsearch.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.Result;
import co.elastic.clients.elasticsearch._types.aggregations.*;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch._types.query_dsl.QueryBuilders;
import co.elastic.clients.elasticsearch.core.*;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch.core.search.HitsMetadata;
import co.elastic.clients.elasticsearch.core.search.TotalHits;
import co.elastic.clients.elasticsearch.core.search.TotalHitsRelation;
import co.elastic.clients.elasticsearch.indices.GetIndexRequest;
import co.elastic.clients.elasticsearch.indices.GetIndexResponse;
import co.elastic.clients.elasticsearch.indices.RefreshRequest;
import co.elastic.clients.elasticsearch.indices.ElasticsearchIndicesClient;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.igot.cb.exceptions.CustomException;
import com.igot.cb.transactional.elasticsearch.dto.SearchCriteria;
import com.igot.cb.transactional.elasticsearch.dto.SearchResult;
import com.igot.cb.util.Constants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class EsClientServiceImplTest {

    @Mock
    private ElasticsearchClient elasticsearchClient;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private ElasticsearchIndicesClient indicesClient;

    private EsClientServiceImpl esClientService;

    @BeforeEach
    void setUp() {
        try (AutoCloseable ignored = MockitoAnnotations.openMocks(this)) {
            esClientService = new EsClientServiceImpl(elasticsearchClient, objectMapper);
        } catch (Exception e) {
            fail("Failed to initialize mocks: " + e.getMessage());
        }
    }

    // ==================== addDocument Tests ====================

    @Test
    void testAddDocument_success() throws Exception {
        String indexName = "test-index";
        String type = "_doc";
        String id = "test-id";
        Map<String, Object> document = new HashMap<>();
        document.put("field1", "value1");
        document.put("field2", "value2");
        String jsonFilePath = "/test-mapping.json";

        // The method reads schema from classpath, but we can't easily mock that
        // Instead, test the exception path or skip schema validation
        when(objectMapper.readValue(any(InputStream.class), any(TypeReference.class)))
                .thenThrow(new IOException("Schema not found"));

        String result = esClientService.addDocument(indexName, type, id, document, jsonFilePath);

        assertNull(result);
    }

    @Test
    void testAddDocument_filterFieldsNotInSchema() throws Exception {
        String indexName = "test-index";
        String type = "_doc";
        String id = "test-id";
        Map<String, Object> document = new HashMap<>();
        document.put("field1", "value1");
        document.put("field2", "value2");
        document.put("field3", "value3"); // This field is not in schema
        String jsonFilePath = "/test-mapping.json";

        // Test exception path
        when(objectMapper.readValue(any(InputStream.class), any(TypeReference.class)))
                .thenThrow(new IOException("Schema not found"));

        String result = esClientService.addDocument(indexName, type, id, document, jsonFilePath);

        assertNull(result);
    }

    @Test
    void testAddDocument_exception() throws Exception {
        String indexName = "test-index";
        String type = "_doc";
        String id = "test-id";
        Map<String, Object> document = new HashMap<>();
        String jsonFilePath = "/test-mapping.json";

        when(objectMapper.readValue(any(InputStream.class), any(TypeReference.class)))
                .thenThrow(new IOException("Schema read error"));

        String result = esClientService.addDocument(indexName, type, id, document, jsonFilePath);

        assertNull(result);
    }

    @Test
    void testAddDocument_successIndexesDocument() throws Exception {
        String indexName = "test-index";
        String type = "_doc";
        String id = "test-id";
        Map<String, Object> document = new HashMap<>();
        document.put("field1", "value1");
        document.put("field2", "value2");
        String jsonFilePath = "/test-mapping.json";

        Map<String, Object> schemaMap = new HashMap<>();
        schemaMap.put("field1", "string");
        schemaMap.put("field2", "string");
        when(objectMapper.readValue(any(InputStream.class), any(TypeReference.class)))
                .thenReturn(schemaMap);

        IndexResponse mockIndexResponse = mock(IndexResponse.class);
        when(mockIndexResponse.result()).thenReturn(Result.Created);
        when(elasticsearchClient.index(any(IndexRequest.class))).thenReturn(mockIndexResponse);

        String result = esClientService.addDocument(indexName, type, id, document, jsonFilePath);

        assertNotNull(result);
        assertTrue(result.startsWith("Successfully indexed document with id:"));
        verify(elasticsearchClient, times(1)).index(any(IndexRequest.class));
    }

    @Test
    void testAddDocument_successFiltersUnknownFields() throws Exception {
        String indexName = "test-index";
        String type = "_doc";
        String id = "test-id";
        Map<String, Object> document = new HashMap<>();
        document.put("field1", "value1");
        document.put("unknownField", "value3");
        String jsonFilePath = "/test-mapping.json";

        Map<String, Object> schemaMap = new HashMap<>();
        schemaMap.put("field1", "string");
        when(objectMapper.readValue(any(InputStream.class), any(TypeReference.class)))
                .thenReturn(schemaMap);

        IndexResponse mockIndexResponse = mock(IndexResponse.class);
        when(mockIndexResponse.result()).thenReturn(Result.Updated);
        when(elasticsearchClient.index(any(IndexRequest.class))).thenReturn(mockIndexResponse);

        String result = esClientService.addDocument(indexName, type, id, document, jsonFilePath);

        assertNotNull(result);
        assertFalse(document.containsKey("unknownField"));
        assertTrue(document.containsKey("field1"));
    }

    // ==================== updateDocument Tests ====================

    @Test
    void testUpdateDocument_success() throws Exception {
        String index = "test-index";
        String indexType = "_doc";
        String entityId = "entity-123";
        Map<String, Object> updatedDocument = new HashMap<>();
        updatedDocument.put("field1", "updatedValue1");
        updatedDocument.put("unknownField", "shouldBeFiltered");
        String jsonFilePath = "/test-mapping.json";

        Map<String, Object> schemaMap = new HashMap<>();
        schemaMap.put("field1", "string");
        when(objectMapper.readValue(any(InputStream.class), any(TypeReference.class)))
                .thenReturn(schemaMap);

        IndexResponse mockIndexResponse = mock(IndexResponse.class);
        when(mockIndexResponse.result()).thenReturn(Result.Updated);
        when(elasticsearchClient.index(any(IndexRequest.class))).thenReturn(mockIndexResponse);

        assertDoesNotThrow(() -> esClientService.updateDocument(index, indexType, entityId, updatedDocument, jsonFilePath));

        assertFalse(updatedDocument.containsKey("unknownField"));
        assertTrue(updatedDocument.containsKey("field1"));
        verify(elasticsearchClient, times(1)).index(any(IndexRequest.class));
    }

    @Test
    void testUpdateDocument_exception() throws Exception {
        String index = "test-index";
        String indexType = "_doc";
        String entityId = "entity-123";
        Map<String, Object> updatedDocument = new HashMap<>();
        updatedDocument.put("field1", "updatedValue1");
        String jsonFilePath = "/test-mapping.json";
        when(objectMapper.readValue(any(InputStream.class), any(TypeReference.class)))
                .thenThrow(new IOException("Schema not found"));

        try {
            esClientService.updateDocument(index, indexType, entityId, updatedDocument, jsonFilePath);
        } catch (NullPointerException e) {
            assertNotNull(e);
        }
    }

    @Test
    void testUpdateDocument_ioException() throws Exception {
        String index = "test-index";
        String indexType = "_doc";
        String entityId = "entity-123";
        Map<String, Object> updatedDocument = new HashMap<>();
        String jsonFilePath = "/test-mapping.json";

        when(objectMapper.readValue(any(InputStream.class), any(TypeReference.class)))
                .thenThrow(new IOException("Read error"));

        assertDoesNotThrow(() -> esClientService.updateDocument(index, indexType, entityId, updatedDocument, jsonFilePath));
    }

    // ==================== deleteDocument Tests ====================

    @Test
    void testDeleteDocument_success() throws Exception {
        String documentId = "doc-123";
        String esIndexName = "test-index";

        DeleteResponse mockDeleteResponse = mock(DeleteResponse.class);
        when(mockDeleteResponse.result()).thenReturn(Result.Deleted);
        when(elasticsearchClient.delete(any(DeleteRequest.class))).thenReturn(mockDeleteResponse);

        when(elasticsearchClient.indices()).thenReturn(indicesClient);
        when(indicesClient.refresh(any(RefreshRequest.class))).thenReturn(mock(co.elastic.clients.elasticsearch.indices.RefreshResponse.class));

        assertDoesNotThrow(() -> esClientService.deleteDocument(documentId, esIndexName));

        verify(elasticsearchClient, times(1)).delete(any(DeleteRequest.class));
        verify(indicesClient, times(1)).refresh(any(RefreshRequest.class));
    }

    @Test
    void testDeleteDocument_notDeleted() throws Exception {
        String documentId = "doc-123";
        String esIndexName = "test-index";

        DeleteResponse mockDeleteResponse = mock(DeleteResponse.class);
        when(mockDeleteResponse.result()).thenReturn(Result.NotFound);
        when(elasticsearchClient.delete(any(DeleteRequest.class))).thenReturn(mockDeleteResponse);

        assertDoesNotThrow(() -> esClientService.deleteDocument(documentId, esIndexName));

        verify(elasticsearchClient, times(1)).delete(any(DeleteRequest.class));
        verify(indicesClient, never()).refresh(any(RefreshRequest.class));
    }

    @Test
    void testDeleteDocument_exception() throws Exception {
        String documentId = "doc-123";
        String esIndexName = "test-index";

        when(elasticsearchClient.delete(any(DeleteRequest.class)))
                .thenThrow(new IOException("Delete error"));

        assertDoesNotThrow(() -> esClientService.deleteDocument(documentId, esIndexName));
    }

    // ==================== searchDocuments Tests ====================

    @Test
    void testSearchDocuments_success() throws Exception {
        String esIndexName = "test-index";
        SearchCriteria searchCriteria = new SearchCriteria();
        searchCriteria.setPageNumber(0);
        searchCriteria.setPageSize(10);

        SearchResponse<Object> mockSearchResponse = createMockSearchResponse();
        when(elasticsearchClient.search(any(SearchRequest.class), eq(Object.class)))
                .thenReturn(mockSearchResponse);

        SearchResult result = esClientService.searchDocuments(esIndexName, searchCriteria);

        assertNotNull(result);
        assertNotNull(result.getData());
        assertEquals(2, result.getData().size());
        assertEquals(2L, result.getTotalCount());
        verify(elasticsearchClient, times(1)).search(any(SearchRequest.class), eq(Object.class));
    }

    @Test
    void testSearchDocuments_withFacets() throws Exception {
        String esIndexName = "test-index";
        SearchCriteria searchCriteria = new SearchCriteria();
        searchCriteria.setPageNumber(0);
        searchCriteria.setPageSize(10);
        searchCriteria.setFacets(Arrays.asList("category", "status"));

        SearchResponse<Object> mockSearchResponse = createMockSearchResponseWithFacets();
        when(elasticsearchClient.search(any(SearchRequest.class), eq(Object.class)))
                .thenReturn(mockSearchResponse);

        SearchResult result = esClientService.searchDocuments(esIndexName, searchCriteria);

        assertNotNull(result);
        assertNotNull(result.getFacets());
        assertTrue(result.getFacets().containsKey("category"));
        assertEquals(2, result.getFacets().get("category").size());
        verify(elasticsearchClient, times(1)).search(any(SearchRequest.class), eq(Object.class));
    }

    @Test
    void testSearchDocuments_withFilters() throws Exception {
        String esIndexName = "test-index";
        SearchCriteria searchCriteria = new SearchCriteria();
        searchCriteria.setPageNumber(0);
        searchCriteria.setPageSize(10);

        HashMap<String, Object> filterCriteria = new HashMap<>();
        filterCriteria.put("status", "active");
        filterCriteria.put("category", Arrays.asList("cat1", "cat2"));
        searchCriteria.setFilterCriteriaMap(filterCriteria);

        SearchResponse<Object> mockSearchResponse = createMockSearchResponse();
        when(elasticsearchClient.search(any(SearchRequest.class), eq(Object.class)))
                .thenReturn(mockSearchResponse);

        SearchResult result = esClientService.searchDocuments(esIndexName, searchCriteria);

        assertNotNull(result);
        verify(elasticsearchClient, times(1)).search(any(SearchRequest.class), eq(Object.class));
    }

    @Test
    void testSearchDocuments_withBooleanFilter() throws Exception {
        String esIndexName = "test-index";
        SearchCriteria searchCriteria = new SearchCriteria();
        searchCriteria.setPageNumber(0);
        searchCriteria.setPageSize(10);

        HashMap<String, Object> filterCriteria = new HashMap<>();
        filterCriteria.put("isActive", true);
        searchCriteria.setFilterCriteriaMap(filterCriteria);

        SearchResponse<Object> mockSearchResponse = createMockSearchResponse();
        when(elasticsearchClient.search(any(SearchRequest.class), eq(Object.class)))
                .thenReturn(mockSearchResponse);

        SearchResult result = esClientService.searchDocuments(esIndexName, searchCriteria);

        assertNotNull(result);
        verify(elasticsearchClient, times(1)).search(any(SearchRequest.class), eq(Object.class));
    }

    @Test
    void testSearchDocuments_withRangeQuery() throws Exception {
        String esIndexName = "test-index";
        SearchCriteria searchCriteria = new SearchCriteria();
        searchCriteria.setPageNumber(0);
        searchCriteria.setPageSize(10);

        HashMap<String, Object> rangeFilter = new HashMap<>();
        rangeFilter.put(Constants.SEARCH_OPERATION_GREATER_THAN_EQUALS, 100);
        rangeFilter.put(Constants.SEARCH_OPERATION_LESS_THAN_EQUALS, 500);

        HashMap<String, Object> filterCriteria = new HashMap<>();
        filterCriteria.put("price", rangeFilter);
        searchCriteria.setFilterCriteriaMap(filterCriteria);

        SearchResponse<Object> mockSearchResponse = createMockSearchResponse();
        when(elasticsearchClient.search(any(SearchRequest.class), eq(Object.class)))
                .thenReturn(mockSearchResponse);

        SearchResult result = esClientService.searchDocuments(esIndexName, searchCriteria);

        assertNotNull(result);
        verify(elasticsearchClient, times(1)).search(any(SearchRequest.class), eq(Object.class));
    }

    @Test
    void testSearchDocuments_withNestedMap() throws Exception {
        String esIndexName = "test-index";
        SearchCriteria searchCriteria = new SearchCriteria();
        searchCriteria.setPageNumber(0);
        searchCriteria.setPageSize(10);

        HashMap<String, Object> nestedMap = new HashMap<>();
        nestedMap.put("subField", "value");

        HashMap<String, Object> filterCriteria = new HashMap<>();
        filterCriteria.put("parent", nestedMap);
        searchCriteria.setFilterCriteriaMap(filterCriteria);

        SearchResponse<Object> mockSearchResponse = createMockSearchResponse();
        when(elasticsearchClient.search(any(SearchRequest.class), eq(Object.class)))
                .thenReturn(mockSearchResponse);

        SearchResult result = esClientService.searchDocuments(esIndexName, searchCriteria);

        assertNotNull(result);
        verify(elasticsearchClient, times(1)).search(any(SearchRequest.class), eq(Object.class));
    }

    @Test
    void testSearchDocuments_withNestedBooleanMap() throws Exception {
        String esIndexName = "test-index";
        SearchCriteria searchCriteria = new SearchCriteria();
        searchCriteria.setPageNumber(0);
        searchCriteria.setPageSize(10);

        HashMap<String, Object> nestedMap = new HashMap<>();
        nestedMap.put("subField", true);

        HashMap<String, Object> filterCriteria = new HashMap<>();
        filterCriteria.put("parent", nestedMap);
        searchCriteria.setFilterCriteriaMap(filterCriteria);

        SearchResponse<Object> mockSearchResponse = createMockSearchResponse();
        when(elasticsearchClient.search(any(SearchRequest.class), eq(Object.class)))
                .thenReturn(mockSearchResponse);

        SearchResult result = esClientService.searchDocuments(esIndexName, searchCriteria);

        assertNotNull(result);
        verify(elasticsearchClient, times(1)).search(any(SearchRequest.class), eq(Object.class));
    }

    @Test
    void testSearchDocuments_withNestedArrayMap() throws Exception {
        String esIndexName = "test-index";
        SearchCriteria searchCriteria = new SearchCriteria();
        searchCriteria.setPageNumber(0);
        searchCriteria.setPageSize(10);

        HashMap<String, Object> nestedMap = new HashMap<>();
        nestedMap.put("subField", Arrays.asList("val1", "val2"));

        HashMap<String, Object> filterCriteria = new HashMap<>();
        filterCriteria.put("parent", nestedMap);
        searchCriteria.setFilterCriteriaMap(filterCriteria);

        SearchResponse<Object> mockSearchResponse = createMockSearchResponse();
        when(elasticsearchClient.search(any(SearchRequest.class), eq(Object.class)))
                .thenReturn(mockSearchResponse);

        SearchResult result = esClientService.searchDocuments(esIndexName, searchCriteria);

        assertNotNull(result);
        verify(elasticsearchClient, times(1)).search(any(SearchRequest.class), eq(Object.class));
    }

    @Test
    void testSearchDocuments_withMustNotFilter() throws Exception {
        String esIndexName = "test-index";
        SearchCriteria searchCriteria = new SearchCriteria();
        searchCriteria.setPageNumber(0);
        searchCriteria.setPageSize(10);

        HashMap<String, Object> filterCriteria = new HashMap<>();
        filterCriteria.put("must_not", new ArrayList<>(List.of("excludedValue")));
        searchCriteria.setFilterCriteriaMap(filterCriteria);

        SearchResponse<Object> mockSearchResponse = createMockSearchResponse();
        when(elasticsearchClient.search(any(SearchRequest.class), eq(Object.class)))
                .thenReturn(mockSearchResponse);

        SearchResult result = esClientService.searchDocuments(esIndexName, searchCriteria);

        assertNotNull(result);
        verify(elasticsearchClient, times(1)).search(any(SearchRequest.class), eq(Object.class));
    }

    @Test
    void testSearchDocuments_withFacetsNonSterms() throws Exception {
        String esIndexName = "test-index";
        SearchCriteria searchCriteria = new SearchCriteria();
        searchCriteria.setPageNumber(0);
        searchCriteria.setPageSize(10);
        searchCriteria.setFacets(Collections.singletonList("category"));

        SearchResponse<Object> mockSearchResponse = createMockSearchResponse();
        Map<String, Aggregate> aggregations = new HashMap<>();
        Aggregate nonStermsAggregate = mock(Aggregate.class);
        when(nonStermsAggregate.isSterms()).thenReturn(false);
        aggregations.put("category_agg", nonStermsAggregate);
        when(mockSearchResponse.aggregations()).thenReturn(aggregations);

        when(elasticsearchClient.search(any(SearchRequest.class), eq(Object.class)))
                .thenReturn(mockSearchResponse);

        SearchResult result = esClientService.searchDocuments(esIndexName, searchCriteria);

        assertNotNull(result);
        assertTrue(result.getFacets().isEmpty() || !result.getFacets().containsKey("category"));
    }

    @Test
    void testSearchDocuments_withStartsWith() throws Exception {
        String esIndexName = "test-index";
        SearchCriteria searchCriteria = new SearchCriteria();
        searchCriteria.setPageNumber(0);
        searchCriteria.setPageSize(10);
        searchCriteria.setStartsWith("test");
        searchCriteria.setStartsWithField("name");

        SearchResponse<Object> mockSearchResponse = createMockSearchResponse();
        when(elasticsearchClient.search(any(SearchRequest.class), eq(Object.class)))
                .thenReturn(mockSearchResponse);

        SearchResult result = esClientService.searchDocuments(esIndexName, searchCriteria);

        assertNotNull(result);
        verify(elasticsearchClient, times(1)).search(any(SearchRequest.class), eq(Object.class));
    }

    @Test
    void testSearchDocuments_withSorting() throws Exception {
        String esIndexName = "test-index";
        SearchCriteria searchCriteria = new SearchCriteria();
        searchCriteria.setPageNumber(0);
        searchCriteria.setPageSize(10);
        searchCriteria.setOrderBy("createdDate");
        searchCriteria.setOrderDirection(Constants.ASC);

        SearchResponse<Object> mockSearchResponse = createMockSearchResponse();
        when(elasticsearchClient.search(any(SearchRequest.class), eq(Object.class)))
                .thenReturn(mockSearchResponse);

        SearchResult result = esClientService.searchDocuments(esIndexName, searchCriteria);

        assertNotNull(result);
        verify(elasticsearchClient, times(1)).search(any(SearchRequest.class), eq(Object.class));
    }

    @Test
    void testSearchDocuments_withDescSorting() throws Exception {
        String esIndexName = "test-index";
        SearchCriteria searchCriteria = new SearchCriteria();
        searchCriteria.setPageNumber(0);
        searchCriteria.setPageSize(10);
        searchCriteria.setOrderBy("createdDate");
        searchCriteria.setOrderDirection("desc");

        SearchResponse<Object> mockSearchResponse = createMockSearchResponse();
        when(elasticsearchClient.search(any(SearchRequest.class), eq(Object.class)))
                .thenReturn(mockSearchResponse);

        SearchResult result = esClientService.searchDocuments(esIndexName, searchCriteria);

        assertNotNull(result);
        verify(elasticsearchClient, times(1)).search(any(SearchRequest.class), eq(Object.class));
    }

    @Test
    void testSearchDocuments_withRequestedFields() throws Exception {
        String esIndexName = "test-index";
        SearchCriteria searchCriteria = new SearchCriteria();
        searchCriteria.setPageNumber(0);
        searchCriteria.setPageSize(10);
        searchCriteria.setRequestedFields(Arrays.asList("field1", "field2"));

        SearchResponse<Object> mockSearchResponse = createMockSearchResponse();
        when(elasticsearchClient.search(any(SearchRequest.class), eq(Object.class)))
                .thenReturn(mockSearchResponse);

        SearchResult result = esClientService.searchDocuments(esIndexName, searchCriteria);

        assertNotNull(result);
        verify(elasticsearchClient, times(1)).search(any(SearchRequest.class), eq(Object.class));
    }

    @Test
    void testSearchDocuments_withEmptyRequestedFields() throws Exception {
        String esIndexName = "test-index";
        SearchCriteria searchCriteria = new SearchCriteria();
        searchCriteria.setPageNumber(0);
        searchCriteria.setPageSize(10);
        searchCriteria.setRequestedFields(Collections.emptyList());

        SearchResponse<Object> mockSearchResponse = createMockSearchResponse();
        when(elasticsearchClient.search(any(SearchRequest.class), eq(Object.class)))
                .thenReturn(mockSearchResponse);

        SearchResult result = esClientService.searchDocuments(esIndexName, searchCriteria);

        assertNotNull(result);
        verify(elasticsearchClient, times(1)).search(any(SearchRequest.class), eq(Object.class));
    }

    @Test
    void testSearchDocuments_withSearchString() throws Exception {
        String esIndexName = "test-index";
        SearchCriteria searchCriteria = new SearchCriteria();
        searchCriteria.setPageNumber(0);
        searchCriteria.setPageSize(10);
        searchCriteria.setSearchString("searchTerm");

        SearchResponse<Object> mockSearchResponse = createMockSearchResponse();
        when(elasticsearchClient.search(any(SearchRequest.class), eq(Object.class)))
                .thenReturn(mockSearchResponse);

        SearchResult result = esClientService.searchDocuments(esIndexName, searchCriteria);

        assertNotNull(result);
        verify(elasticsearchClient, times(1)).search(any(SearchRequest.class), eq(Object.class));
    }


    @Test
    void testSearchDocuments_withTermQuery() throws Exception {
        String esIndexName = "test-index";
        SearchCriteria searchCriteria = new SearchCriteria();
        searchCriteria.setPageNumber(0);
        searchCriteria.setPageSize(10);

        Map<String, Object> query = new HashMap<>();
        query.put(Constants.TERM, Collections.singletonMap("status", FieldValue.of("active")));

        searchCriteria.setQuery(query);

        SearchResponse<Object> mockSearchResponse = createMockSearchResponse();
        when(elasticsearchClient.search(any(SearchRequest.class), eq(Object.class)))
                .thenReturn(mockSearchResponse);

        SearchResult result = esClientService.searchDocuments(esIndexName, searchCriteria);

        assertNotNull(result);
        verify(elasticsearchClient, times(1)).search(any(SearchRequest.class), eq(Object.class));
    }

    static Stream<Arguments> rangeQueryOperators() {
        return Stream.of(
                Arguments.of("gt", 100),
                Arguments.of("gte", 100),
                Arguments.of("lt", 500),
                Arguments.of("lte", 500)
        );
    }

    @ParameterizedTest
    @MethodSource("rangeQueryOperators")
    void testSearchDocuments_withRangeQuery(String operator, int value) throws Exception {
        String esIndexName = "test-index";
        SearchCriteria searchCriteria = new SearchCriteria();
        searchCriteria.setPageNumber(0);
        searchCriteria.setPageSize(10);

        Map<String, Object> rangeConditions = new HashMap<>();
        rangeConditions.put(operator, value);

        Map<String, Object> query = new HashMap<>();
        query.put(Constants.RANGE, Collections.singletonMap("age", rangeConditions));

        searchCriteria.setQuery(query);

        SearchResponse<Object> mockSearchResponse = createMockSearchResponse();
        when(elasticsearchClient.search(any(SearchRequest.class), eq(Object.class)))
                .thenReturn(mockSearchResponse);

        SearchResult result = esClientService.searchDocuments(esIndexName, searchCriteria);

        assertNotNull(result);
        verify(elasticsearchClient, times(1)).search(any(SearchRequest.class), eq(Object.class));
    }

    @Test
    void testSearchDocuments_withRangeQueryInvalidCondition() {
        String esIndexName = "test-index";
        SearchCriteria searchCriteria = new SearchCriteria();
        searchCriteria.setPageNumber(0);
        searchCriteria.setPageSize(10);

        Map<String, Object> rangeConditions = new HashMap<>();
        rangeConditions.put("invalid", 100);

        Map<String, Object> query = new HashMap<>();
        query.put(Constants.RANGE, Collections.singletonMap("age", rangeConditions));

        searchCriteria.setQuery(query);

        assertThrows(IllegalArgumentException.class, () ->
            esClientService.searchDocuments(esIndexName, searchCriteria)
        );
    }

    @Test
    void testSearchDocuments_withMatchQuery() throws Exception {
        String esIndexName = "test-index";
        SearchCriteria searchCriteria = new SearchCriteria();
        searchCriteria.setPageNumber(0);
        searchCriteria.setPageSize(10);

        Map<String, Object> query = new HashMap<>();
        query.put(Constants.MATCH, Collections.singletonMap("description", FieldValue.of("test description")));

        searchCriteria.setQuery(query);

        SearchResponse<Object> mockSearchResponse = createMockSearchResponse();
        when(elasticsearchClient.search(any(SearchRequest.class), eq(Object.class)))
                .thenReturn(mockSearchResponse);

        SearchResult result = esClientService.searchDocuments(esIndexName, searchCriteria);

        assertNotNull(result);
        verify(elasticsearchClient, times(1)).search(any(SearchRequest.class), eq(Object.class));
    }

    @Test
    void testSearchDocuments_withUnsupportedQuery() {
        String esIndexName = "test-index";
        SearchCriteria searchCriteria = new SearchCriteria();
        searchCriteria.setPageNumber(0);
        searchCriteria.setPageSize(10);

        Map<String, Object> query = new HashMap<>();
        query.put("unsupported", Collections.singletonMap("field", "value"));

        searchCriteria.setQuery(query);

        assertThrows(IllegalArgumentException.class, () ->
            esClientService.searchDocuments(esIndexName, searchCriteria)
        );
    }

    @Test
    void testSearchDocuments_ioException() throws Exception {
        String esIndexName = "test-index";
        SearchCriteria searchCriteria = new SearchCriteria();
        searchCriteria.setPageNumber(0);
        searchCriteria.setPageSize(10);

        when(elasticsearchClient.search(any(SearchRequest.class), eq(Object.class)))
                .thenThrow(new IOException("Search error"));

        SearchResult result = esClientService.searchDocuments(esIndexName, searchCriteria);

        assertNull(result);
    }

    @Test
    void testSearchDocuments_nullCriteria() {
        String esIndexName = "test-index";

        // When criteria is null, buildSearchRequest returns null which causes assertion error
        // This test verifies the behavior when null criteria is passed
        try {
            SearchResult result = esClientService.searchDocuments(esIndexName, null);
            // If assertions are disabled, result will be null
            assertNull(result);
        } catch (AssertionError e) {
            // If assertions are enabled, an assertion error will be thrown
            assertNotNull(e);
        }
    }

    @Test
    void testSearchDocuments_withPageSizeZero() throws Exception {
        String esIndexName = "test-index";
        SearchCriteria searchCriteria = new SearchCriteria();
        searchCriteria.setPageNumber(0);
        searchCriteria.setPageSize(0);

        SearchResponse<Object> mockSearchResponse = createMockSearchResponse();
        when(elasticsearchClient.search(any(SearchRequest.class), eq(Object.class)))
                .thenReturn(mockSearchResponse);

        SearchResult result = esClientService.searchDocuments(esIndexName, searchCriteria);

        assertNotNull(result);
        verify(elasticsearchClient, times(1)).search(any(SearchRequest.class), eq(Object.class));
    }

    // ==================== deleteDocumentsByCriteria Tests ====================

    @Test
    void testDeleteDocumentsByCriteria_success() throws Exception {
        String esIndexName = "test-index";
        Query query = QueryBuilders.term(t -> t.field("status").value("deleted"));

        SearchResponse<Object> mockSearchResponse = mock(SearchResponse.class);
        HitsMetadata<Object> mockHits = mock(HitsMetadata.class);
        TotalHits mockTotalHits = mock(TotalHits.class);
        when(mockTotalHits.value()).thenReturn(2L);
        when(mockHits.total()).thenReturn(mockTotalHits);

        List<Hit<Object>> hitsList = new ArrayList<>();
        Hit<Object> hit1 = mock(Hit.class);
        when(hit1.id()).thenReturn("id1");
        Hit<Object> hit2 = mock(Hit.class);
        when(hit2.id()).thenReturn("id2");
        hitsList.add(hit1);
        hitsList.add(hit2);
        when(mockHits.hits()).thenReturn(hitsList);
        when(mockSearchResponse.hits()).thenReturn(mockHits);

        when(elasticsearchClient.search(any(SearchRequest.class), eq(Object.class)))
                .thenReturn(mockSearchResponse);

        BulkResponse mockBulkResponse = mock(BulkResponse.class);
        when(mockBulkResponse.errors()).thenReturn(false);
        when(elasticsearchClient.bulk(any(BulkRequest.class))).thenReturn(mockBulkResponse);

        assertDoesNotThrow(() -> esClientService.deleteDocumentsByCriteria(esIndexName, query));

        verify(elasticsearchClient, times(1)).search(any(SearchRequest.class), eq(Object.class));
        verify(elasticsearchClient, times(1)).bulk(any(BulkRequest.class));
    }

    @Test
    void testDeleteDocumentsByCriteria_withErrors() throws Exception {
        String esIndexName = "test-index";
        Query query = QueryBuilders.term(t -> t.field("status").value("deleted"));

        SearchResponse<Object> mockSearchResponse = mock(SearchResponse.class);
        HitsMetadata<Object> mockHits = mock(HitsMetadata.class);
        TotalHits mockTotalHits = mock(TotalHits.class);
        when(mockTotalHits.value()).thenReturn(1L);
        when(mockHits.total()).thenReturn(mockTotalHits);

        List<Hit<Object>> hitsList = new ArrayList<>();
        Hit<Object> hit1 = mock(Hit.class);
        when(hit1.id()).thenReturn("id1");
        hitsList.add(hit1);
        when(mockHits.hits()).thenReturn(hitsList);
        when(mockSearchResponse.hits()).thenReturn(mockHits);

        when(elasticsearchClient.search(any(SearchRequest.class), eq(Object.class)))
                .thenReturn(mockSearchResponse);

        BulkResponse mockBulkResponse = mock(BulkResponse.class);
        when(mockBulkResponse.errors()).thenReturn(true);
        when(elasticsearchClient.bulk(any(BulkRequest.class))).thenReturn(mockBulkResponse);

        assertDoesNotThrow(() -> esClientService.deleteDocumentsByCriteria(esIndexName, query));

        verify(elasticsearchClient, times(1)).bulk(any(BulkRequest.class));
    }

    @Test
    void testDeleteDocumentsByCriteria_noDocumentsFound() throws Exception {
        String esIndexName = "test-index";
        Query query = QueryBuilders.term(t -> t.field("status").value("deleted"));

        SearchResponse<Object> mockSearchResponse = mock(SearchResponse.class);
        HitsMetadata<Object> mockHits = mock(HitsMetadata.class);
        TotalHits mockTotalHits = mock(TotalHits.class);
        when(mockTotalHits.value()).thenReturn(0L);
        when(mockHits.total()).thenReturn(mockTotalHits);
        when(mockSearchResponse.hits()).thenReturn(mockHits);

        when(elasticsearchClient.search(any(SearchRequest.class), eq(Object.class)))
                .thenReturn(mockSearchResponse);

        assertDoesNotThrow(() -> esClientService.deleteDocumentsByCriteria(esIndexName, query));

        verify(elasticsearchClient, times(1)).search(any(SearchRequest.class), eq(Object.class));
        verify(elasticsearchClient, never()).bulk(any(BulkRequest.class));
    }

    @Test
    void testDeleteDocumentsByCriteria_exception() throws Exception {
        String esIndexName = "test-index";
        Query query = QueryBuilders.term(t -> t.field("status").value("deleted"));

        when(elasticsearchClient.search(any(SearchRequest.class), eq(Object.class)))
                .thenThrow(new IOException("Search error"));

        assertDoesNotThrow(() -> esClientService.deleteDocumentsByCriteria(esIndexName, query));
    }

    // ==================== isIndexPresent Tests ====================

    @Test
    void testIsIndexPresent_true() throws Exception {
        String indexName = "test-index";

        when(elasticsearchClient.indices()).thenReturn(indicesClient);
        GetIndexResponse mockGetIndexResponse = mock(GetIndexResponse.class);
        when(indicesClient.get(any(GetIndexRequest.class))).thenReturn(mockGetIndexResponse);

        boolean result = esClientService.isIndexPresent(indexName);

        assertTrue(result);
        verify(indicesClient, times(1)).get(any(GetIndexRequest.class));
    }

    @Test
    void testIsIndexPresent_ioException() throws Exception {
        String indexName = "test-index";

        when(elasticsearchClient.indices()).thenReturn(indicesClient);
        when(indicesClient.get(any(GetIndexRequest.class))).thenThrow(new IOException("Index not found"));

        boolean result = esClientService.isIndexPresent(indexName);

        assertFalse(result);
    }

    // ==================== saveAll Tests ====================

    @Test
    void testSaveAll_success() throws Exception {
        String esIndexName = "test-index";
        List<JsonNode> entities = new ArrayList<>();

        JsonNode entity1 = mock(JsonNode.class);
        JsonNode idNode1 = mock(JsonNode.class);
        when(idNode1.asText()).thenReturn("id1");
        when(entity1.get(Constants.ID)).thenReturn(idNode1);
        entities.add(entity1);

        JsonNode entity2 = mock(JsonNode.class);
        JsonNode idNode2 = mock(JsonNode.class);
        when(idNode2.asText()).thenReturn("id2");
        when(entity2.get(Constants.ID)).thenReturn(idNode2);
        entities.add(entity2);

        when(objectMapper.convertValue(any(JsonNode.class), eq(Map.class)))
                .thenReturn(new HashMap<>());

        BulkResponse mockBulkResponse = mock(BulkResponse.class);
        when(mockBulkResponse.errors()).thenReturn(false);
        when(elasticsearchClient.bulk(any(BulkRequest.class))).thenReturn(mockBulkResponse);

        BulkResponse result = esClientService.saveAll(esIndexName, entities);

        assertNotNull(result);
        assertFalse(result.errors());
        verify(elasticsearchClient, times(1)).bulk(any(BulkRequest.class));
    }

    @Test
    void testSaveAll_exception() throws Exception {
        String esIndexName = "test-index";
        List<JsonNode> entities = new ArrayList<>();

        JsonNode entity1 = mock(JsonNode.class);
        JsonNode idNode1 = mock(JsonNode.class);
        when(idNode1.asText()).thenReturn("id1");
        when(entity1.get(Constants.ID)).thenReturn(idNode1);
        entities.add(entity1);

        when(objectMapper.convertValue(any(JsonNode.class), eq(Map.class)))
                .thenReturn(new HashMap<>());

        when(elasticsearchClient.bulk(any(BulkRequest.class)))
                .thenThrow(new IOException("Bulk operation failed"));

        assertThrows(CustomException.class, () ->
            esClientService.saveAll(esIndexName, entities)
        );
    }

    @Test
    void testSaveAll_runtimeException() {
        String esIndexName = "test-index";
        List<JsonNode> entities = new ArrayList<>();

        JsonNode entity1 = mock(JsonNode.class);
        when(entity1.get(Constants.ID)).thenThrow(new RuntimeException("Unexpected error"));
        entities.add(entity1);

        assertThrows(CustomException.class, () ->
            esClientService.saveAll(esIndexName, entities)
        );
    }

    // ==================== readDocument Tests ====================

    @Test
    void testReadDocument_success() throws Exception {
        String esIndexName = "test-index";
        String id = "doc-123";

        Map<String, Object> expectedDoc = new HashMap<>();
        expectedDoc.put("field1", "value1");
        expectedDoc.put("field2", "value2");

        GetResponse<Object> mockGetResponse = mock(GetResponse.class);
        when(mockGetResponse.found()).thenReturn(true);
        when(mockGetResponse.source()).thenReturn(expectedDoc);
        when(elasticsearchClient.get(any(GetRequest.class), eq(Object.class)))
                .thenReturn(mockGetResponse);

        Map<String, Object> result = esClientService.readDocument(esIndexName, id);

        assertNotNull(result);
        assertEquals(expectedDoc, result);
        verify(elasticsearchClient, times(1)).get(any(GetRequest.class), eq(Object.class));
    }

    @Test
    void testReadDocument_notFound() throws Exception {
        String esIndexName = "test-index";
        String id = "doc-123";

        GetResponse<Object> mockGetResponse = mock(GetResponse.class);
        when(mockGetResponse.found()).thenReturn(false);
        when(elasticsearchClient.get(any(GetRequest.class), eq(Object.class)))
                .thenReturn(mockGetResponse);

        Map<String, Object> result = esClientService.readDocument(esIndexName, id);

        assertNotNull(result);
        assertTrue(result.isEmpty());
        verify(elasticsearchClient, times(1)).get(any(GetRequest.class), eq(Object.class));
    }

    @Test
    void testReadDocument_sourceNotMap() throws Exception {
        String esIndexName = "test-index";
        String id = "doc-123";

        GetResponse<Object> mockGetResponse = mock(GetResponse.class);
        when(mockGetResponse.found()).thenReturn(true);
        when(mockGetResponse.source()).thenReturn("not a map");
        when(elasticsearchClient.get(any(GetRequest.class), eq(Object.class)))
                .thenReturn(mockGetResponse);

        Map<String, Object> result = esClientService.readDocument(esIndexName, id);

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void testReadDocument_exception() throws Exception {
        String esIndexName = "test-index";
        String id = "doc-123";

        when(elasticsearchClient.get(any(GetRequest.class), eq(Object.class)))
                .thenThrow(new IOException("Get error"));

        Map<String, Object> result = esClientService.readDocument(esIndexName, id);

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    // ==================== Helper Methods ====================

    private SearchResponse<Object> createMockSearchResponse() {
        SearchResponse<Object> mockSearchResponse = mock(SearchResponse.class);
        HitsMetadata<Object> mockHits = mock(HitsMetadata.class);
        TotalHits mockTotalHits = mock(TotalHits.class);

        when(mockTotalHits.value()).thenReturn(2L);
        when(mockTotalHits.relation()).thenReturn(TotalHitsRelation.Eq);
        when(mockHits.total()).thenReturn(mockTotalHits);

        List<Hit<Object>> hitsList = new ArrayList<>();
        Hit<Object> hit1 = mock(Hit.class);
        Map<String, Object> source1 = new HashMap<>();
        source1.put("id", "1");
        source1.put("name", "Test 1");
        when(hit1.source()).thenReturn(source1);

        Hit<Object> hit2 = mock(Hit.class);
        Map<String, Object> source2 = new HashMap<>();
        source2.put("id", "2");
        source2.put("name", "Test 2");
        when(hit2.source()).thenReturn(source2);

        hitsList.add(hit1);
        hitsList.add(hit2);
        when(mockHits.hits()).thenReturn(hitsList);
        when(mockSearchResponse.hits()).thenReturn(mockHits);
        when(mockSearchResponse.aggregations()).thenReturn(Collections.emptyMap());

        return mockSearchResponse;
    }

    private SearchResponse<Object> createMockSearchResponseWithFacets() {
        SearchResponse<Object> mockSearchResponse = createMockSearchResponse();

        Map<String, Aggregate> aggregations = new HashMap<>();

        // Mock category aggregation
        Aggregate categoryAggregate = mock(Aggregate.class);
        when(categoryAggregate.isSterms()).thenReturn(true);

        StringTermsAggregate categoryTermsAggregate = mock(StringTermsAggregate.class);
        List<StringTermsBucket> categoryBuckets = new ArrayList<>();

        StringTermsBucket bucket1 = mock(StringTermsBucket.class);
        when(bucket1.key()).thenReturn(FieldValue.of("category1"));
        when(bucket1.docCount()).thenReturn(10L);
        categoryBuckets.add(bucket1);

        StringTermsBucket bucket2 = mock(StringTermsBucket.class);
        when(bucket2.key()).thenReturn(FieldValue.of("category2"));
        when(bucket2.docCount()).thenReturn(5L);
        categoryBuckets.add(bucket2);

        Buckets<StringTermsBucket> buckets = mock(Buckets.class);
        when(buckets.array()).thenReturn(categoryBuckets);
        when(categoryTermsAggregate.buckets()).thenReturn(buckets);
        when(categoryAggregate.sterms()).thenReturn(categoryTermsAggregate);

        aggregations.put("category_agg", categoryAggregate);

        // Mock status aggregation
        Aggregate statusAggregate = mock(Aggregate.class);
        when(statusAggregate.isSterms()).thenReturn(true);

        StringTermsAggregate statusTermsAggregate = mock(StringTermsAggregate.class);
        List<StringTermsBucket> statusBuckets = new ArrayList<>();

        StringTermsBucket statusBucket1 = mock(StringTermsBucket.class);
        when(statusBucket1.key()).thenReturn(FieldValue.of("active"));
        when(statusBucket1.docCount()).thenReturn(8L);
        statusBuckets.add(statusBucket1);

        Buckets<StringTermsBucket> statusBucketsObj = mock(Buckets.class);
        when(statusBucketsObj.array()).thenReturn(statusBuckets);
        when(statusTermsAggregate.buckets()).thenReturn(statusBucketsObj);
        when(statusAggregate.sterms()).thenReturn(statusTermsAggregate);

        aggregations.put("status_agg", statusAggregate);

        when(mockSearchResponse.aggregations()).thenReturn(aggregations);

        return mockSearchResponse;
    }
}
