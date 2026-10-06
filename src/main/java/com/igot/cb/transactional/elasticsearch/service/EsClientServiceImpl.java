package com.igot.cb.transactional.elasticsearch.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.Refresh;
import co.elastic.clients.elasticsearch._types.SortOptions;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.aggregations.Aggregate;
import co.elastic.clients.elasticsearch._types.aggregations.Aggregation;
import co.elastic.clients.elasticsearch._types.aggregations.StringTermsBucket;
import co.elastic.clients.elasticsearch._types.aggregations.TermsAggregation;
import co.elastic.clients.elasticsearch._types.query_dsl.*;
import co.elastic.clients.elasticsearch.core.*;
import co.elastic.clients.elasticsearch.core.bulk.BulkOperation;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch.core.search.HitsMetadata;
import co.elastic.clients.elasticsearch.core.search.SourceConfig;
import co.elastic.clients.elasticsearch.indices.GetIndexRequest;
import co.elastic.clients.elasticsearch.indices.GetIndexResponse;
import co.elastic.clients.elasticsearch.indices.RefreshRequest;
import co.elastic.clients.json.JsonData;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.igot.cb.exceptions.CustomException;
import com.igot.cb.transactional.elasticsearch.config.EsClientConfig;
import com.igot.cb.transactional.elasticsearch.dto.FacetDTO;
import com.igot.cb.transactional.elasticsearch.dto.SearchCriteria;
import com.igot.cb.transactional.elasticsearch.dto.SearchResult;
import com.igot.cb.util.CbServerProperties;
import com.igot.cb.util.Constants;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.client.RequestOptions;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.Map.Entry;
import java.util.stream.Collectors;

@Service
@Slf4j
public class EsClientServiceImpl implements EsClientService {

    private final ElasticsearchClient elasticsearchClient;
    private final ObjectMapper objectMapper;
    private final CbServerProperties cbServerProperties;
    private final CbServerProperties serverConfig;

    public EsClientServiceImpl(ElasticsearchClient elasticsearchClient, EsClientConfig esConnection,
            ObjectMapper objectMapper, CbServerProperties cbServerProperties, CbServerProperties serverConfig) {
        this.elasticsearchClient = elasticsearchClient;
        this.objectMapper = objectMapper;
        this.cbServerProperties = cbServerProperties;
        this.serverConfig = serverConfig;
    }


    @Override
    public String addDocument(
            String esIndexName, String type, String id, Map<String, Object> document, String jsonFilePath) {
        log.info("EsUtilServiceImpl :: addDocument");
        try {
            JsonSchemaFactory schemaFactory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7);
            InputStream schemaStream = schemaFactory.getClass().getResourceAsStream(jsonFilePath);
            Map<String, Object> map = objectMapper.readValue(schemaStream,
                    new TypeReference<Map<String, Object>>() {
                    });
            Iterator<Entry<String, Object>> iterator = document.entrySet().iterator();
            while (iterator.hasNext()) {
                Entry<String, Object> entry = iterator.next();
                String key = entry.getKey();
                if (!map.containsKey(key)) {
                    iterator.remove();
                }
            }
            IndexRequest<Map<String,Object>> indexRequest = new IndexRequest.Builder<Map<String, Object>>()
                    .index(esIndexName)
                    .id(id)
                    .document(document)
                    .refresh(Refresh.True)
                    .build();
            IndexResponse response = elasticsearchClient.index(indexRequest);
            return "Successfully indexed document with id: " + response.result();
        } catch (Exception e) {
            log.error("Issue while Indexing to es: {}", e.getMessage());
            return null;
        }
    }

    public void updateDocument(
            String index, String indexType, String entityId, Map<String, Object> updatedDocument, String jsonFilePath) {
        try {
            JsonSchemaFactory schemaFactory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7);
            InputStream schemaStream = schemaFactory.getClass().getResourceAsStream(jsonFilePath);
            Map<String, Object> map = objectMapper.readValue(schemaStream,
                    new TypeReference<Map<String, Object>>() {
                    });
            Iterator<Entry<String, Object>> iterator = updatedDocument.entrySet().iterator();
            while (iterator.hasNext()) {
                Entry<String, Object> entry = iterator.next();
                String key = entry.getKey();
                if (!map.containsKey(key)) {
                    iterator.remove();
                }
            }
            IndexRequest<Map<String, Object>> indexRequest = new IndexRequest.Builder<Map<String, Object>>()
                    .index(index)
                    .id(entityId)
                    .document(updatedDocument)
                    .refresh(Refresh.True)
                    .build();
            elasticsearchClient.index(indexRequest);
        } catch (IOException e) {
            log.error("Error occurred during deleting document in elasticsearch");
        }
    }

    @Override
    public void deleteDocument(String documentId, String esIndexName) {
        try {
            DeleteRequest request = new DeleteRequest.Builder().index(esIndexName).id(documentId).build();
            DeleteResponse response = elasticsearchClient.delete(request);
            if (response.result().jsonValue().equalsIgnoreCase("DELETED")) {
                log.info("Document deleted successfully from elasticsearch.");
                RefreshRequest refreshRequest = new RefreshRequest.Builder().index(esIndexName).build();
                elasticsearchClient.indices().refresh(refreshRequest);
                log.info("Index refreshed to reflect the document deletion.");
            } else {
                log.error("Document not found or failed to delete from elasticsearch.");
            }
        } catch (Exception e) {
            log.error("Error occurred during deleting document in elasticsearch");
        }
    }

    @Override
    public SearchResult searchDocuments(String esIndexName, SearchCriteria searchCriteria) {
        SearchRequest.Builder searchRequestBuilder = buildSearchRequest(searchCriteria);
        assert searchRequestBuilder != null;
        searchRequestBuilder.index(esIndexName);
        try {
            if (searchCriteria != null) {
                int pageNumber = searchCriteria.getPageNumber();
                int pageSize = searchCriteria.getPageSize();
                int from = pageNumber * pageSize;
                searchRequestBuilder.from(from);
                if (pageSize > 0) {
                    searchRequestBuilder.size(pageSize);
                }

            }

            SearchRequest searchRequest = searchRequestBuilder.build();
            log.info("Final search query: {}", searchRequest.toString());
            SearchResponse<Object> paginatedSearchResponse =
                    elasticsearchClient.search(searchRequest, Object.class);
            List<Map<String, Object>> paginatedResult = extractPaginatedResult(paginatedSearchResponse);
            Map<String, List<FacetDTO>> fieldAggregations =
                    extractFacetData(paginatedSearchResponse, searchCriteria);
            SearchResult searchResult = new SearchResult();
            searchResult.setData(paginatedResult);
            searchResult.setFacets(fieldAggregations);
            searchResult.setTotalCount(paginatedSearchResponse.hits().total().value());
            return searchResult;
        } catch (IOException e) {
            log.error("Error while fetching details from elastic search");
            return null;
        }
    }

    private Map<String, List<FacetDTO>> extractFacetData(
            SearchResponse<Object> searchResponse, SearchCriteria searchCriteria) {
        Map<String, List<FacetDTO>> fieldAggregations = new HashMap<>();
        if (searchCriteria.getFacets() != null) {
            for (String field : searchCriteria.getFacets()) {
                Aggregate aggregate = searchResponse
                        .aggregations()
                        .get(field + "_agg");
                if (aggregate.isSterms()) {
                    List<FacetDTO> fieldValueList = new ArrayList<>();
                    for (StringTermsBucket bucket : aggregate.sterms().buckets().array()) {
                        if (!bucket.key().stringValue().isEmpty()) {
                            FacetDTO facetDTO = new FacetDTO(bucket.key().stringValue(), bucket.docCount());
                            fieldValueList.add(facetDTO);
                        }
                    }
                    fieldAggregations.put(field, fieldValueList);
                }
            }
        }
        return fieldAggregations;
    }

    private List<Map<String, Object>> extractPaginatedResult(SearchResponse<Object> paginatedSearchResponse) {
        List<Map<String, Object>> paginatedResult = new ArrayList<>();
        for (Hit<Object> hit : paginatedSearchResponse.hits().hits()) {
            paginatedResult.add((Map<String, Object>) hit.source());
        }
        return paginatedResult;
    }

    private SearchRequest.Builder buildSearchRequest(SearchCriteria searchCriteria) {
        log.info("Building search query");
        if (searchCriteria == null || searchCriteria.toString().isEmpty()) {
            log.error("Search criteria body is missing");
            return null;
        }
        BoolQuery.Builder boolQueryBuilder = buildFilterQuery(searchCriteria.getFilterCriteriaMap());
        // Add startsWith logic if present
        String startsWith = searchCriteria.getStartsWith();
        String startsWithField = searchCriteria.getStartsWithField();
        if (startsWith != null && !startsWith.trim().isEmpty() &&
                startsWithField != null && !startsWithField.trim().isEmpty()) {
            Query prefixQuery = Query.of(q -> q.prefix(p -> p
                    .field(startsWithField + ".keyword")
                    .value(startsWith)
            ));
            boolQueryBuilder.must(prefixQuery);
        }
        SearchRequest.Builder searchSourceBuilder = new SearchRequest.Builder();
        searchSourceBuilder.query(boolQueryBuilder.build()._toQuery());
        addSortToSearchSourceBuilder(searchCriteria, searchSourceBuilder);
        addRequestedFieldsToSearchSourceBuilder(searchCriteria, searchSourceBuilder);
        addQueryStringToFilter(searchCriteria.getSearchString(), boolQueryBuilder);
        addFacetsToSearchSourceBuilder(searchCriteria.getFacets(), searchSourceBuilder);
        Query queryPart = buildQueryPart(searchCriteria.getQuery());
        boolQueryBuilder.must(queryPart);
        return searchSourceBuilder;
    }

    private BoolQuery.Builder buildFilterQuery(Map<String, Object> filterCriteriaMap) {
        BoolQuery.Builder boolQueryBuilder = QueryBuilders.bool();
        List<Query> mustNotQueries = new ArrayList<>();
        List<Query> boolQueries = new ArrayList<>();

        if (filterCriteriaMap != null) {
            filterCriteriaMap.forEach((field, value) ->
                    applyFilterEntry(field, value, boolQueryBuilder, mustNotQueries, boolQueries));
            mustNotQueries.forEach(boolQueryBuilder::mustNot);
            boolQueries.forEach(boolQueryBuilder::must);
        }
        return boolQueryBuilder;
    }

    private void applyFilterEntry(String field, Object value, BoolQuery.Builder boolQueryBuilder,
            List<Query> mustNotQueries, List<Query> boolQueries) {
        if (field.equals("must_not") && value instanceof ArrayList) {
            mustNotQueries.forEach(boolQueryBuilder::mustNot);
        } else if (value instanceof Boolean booleanValue) {
            boolQueries.add(Query.of(q -> q.term(t -> t.field(field).value(booleanValue))));
        } else if (value instanceof ArrayList) {
            applyArrayListFilter(field, (ArrayList<String>) value, boolQueryBuilder);
        } else if (value instanceof String stringValue) {
            applyStringFilter(field, stringValue, boolQueryBuilder);
        } else if (value instanceof Map) {
            applyMapFilter(field, (Map<String, Object>) value, boolQueryBuilder);
        }
    }

    private void applyArrayListFilter(String field, ArrayList<String> value, BoolQuery.Builder boolQueryBuilder) {
        List<FieldValue> termsList = value.stream()
                .map(FieldValue::of)
                .toList();
        boolQueryBuilder.must(Query.of(q -> q.terms(t -> t.field(field + Constants.KEYWORD).terms(terms -> terms.value(termsList)))));
    }

    private void applyStringFilter(String field, String value, BoolQuery.Builder boolQueryBuilder) {
        boolQueryBuilder.must(Query.of(q -> q.terms(t ->
                t.field(field + Constants.KEYWORD)
                        .terms(terms -> terms.value(List.of(FieldValue.of(value))))
        )));
    }

    private void applyMapFilter(String field, Map<String, Object> nestedMap, BoolQuery.Builder boolQueryBuilder) {
        if (isRangeQuery(nestedMap)) {
            boolQueryBuilder.must(buildRangeOrNullQuery(field, nestedMap));
        } else {
            applyNestedFieldFilters(field, nestedMap, boolQueryBuilder);
        }
    }

    private Query buildRangeOrNullQuery(String field, Map<String, Object> nestedMap) {
        RangeQuery.Builder rangeQuery = QueryBuilders.range().field(field);
        nestedMap.forEach((rangeOperator, rangeValue) -> applyRangeOperator(rangeQuery, rangeOperator, rangeValue));
        BoolQuery.Builder rangeOrNullQuery = QueryBuilders.bool();
        rangeOrNullQuery.should(rangeQuery.build()._toQuery());
        rangeOrNullQuery.should(Query.of(q -> q.bool(b -> b.mustNot(Query.of(qn -> qn.exists(e -> e.field(field)))))));
        return rangeOrNullQuery.build()._toQuery();
    }

    private void applyRangeOperator(RangeQuery.Builder rangeQuery, String rangeOperator, Object rangeValue) {
        switch (rangeOperator) {
            case Constants.SEARCH_OPERATION_GREATER_THAN_EQUALS:
                rangeQuery.gte(JsonData.of(rangeValue));
                break;
            case Constants.SEARCH_OPERATION_LESS_THAN_EQUALS:
                rangeQuery.lte(JsonData.of(rangeValue));
                break;
            case Constants.SEARCH_OPERATION_GREATER_THAN:
                rangeQuery.gt(JsonData.of(rangeValue));
                break;
            case Constants.SEARCH_OPERATION_LESS_THAN:
                rangeQuery.lt(JsonData.of(rangeValue));
                break;
            default:
                break;
        }
    }

    private void applyNestedFieldFilters(String field, Map<String, Object> nestedMap, BoolQuery.Builder boolQueryBuilder) {
        nestedMap.forEach((nestedField, nestedValue) -> {
            String fullPath = field + "." + nestedField;
            if (nestedValue instanceof Boolean booleanValue) {
                boolQueryBuilder.must(Query.of(q -> q.term(t -> t.field(fullPath).value(booleanValue))));
            } else if (nestedValue instanceof String stringValue) {
                List<FieldValue> termList = Collections.singletonList(FieldValue.of(stringValue));
                boolQueryBuilder.must(Query.of(q -> q.terms(t -> t.field(fullPath + Constants.KEYWORD)
                        .terms(terms -> terms.value(termList))
                )));
            } else if (nestedValue instanceof ArrayList) {
                boolQueryBuilder.must(Query.of(q -> q.terms(t -> t.field(fullPath + Constants.KEYWORD).terms((TermsQueryField) nestedValue))));
            }
        });
    }

    private void addSortToSearchSourceBuilder(
            SearchCriteria searchCriteria, SearchRequest.Builder searchRequestBuilder) {
        if (isNotBlank(searchCriteria.getOrderBy()) && isNotBlank(searchCriteria.getOrderDirection())) {
            SortOrder sortOrder =
                    Constants.ASC.equals(searchCriteria.getOrderDirection()) ? SortOrder.Asc : SortOrder.Desc;
            searchRequestBuilder.sort(SortOptions.of(so -> so
                    .field(f -> f
                            .field(searchCriteria.getOrderBy())
                            .order(sortOrder)
                    )
            ));
        }
    }

    private void addRequestedFieldsToSearchSourceBuilder(
            SearchCriteria searchCriteria, SearchRequest.Builder searchRequestBuilder) {
        if (searchCriteria.getRequestedFields() == null) {
            // Get all fields in response
            searchRequestBuilder.source(SourceConfig.of(sc -> sc.fetch(true)));
        } else {
            if (searchCriteria.getRequestedFields().isEmpty()) {
                log.error("Please specify at least one field to include in the results.");
            }
            searchRequestBuilder.source(SourceConfig.of(sc -> sc.filter(filter -> filter.includes(searchCriteria.getRequestedFields()))));
        }
    }

    private void addQueryStringToFilter(String searchString, BoolQuery.Builder boolQueryBuilder) {
        if (isNotBlank(searchString)) {
            Query wildcardQuery = Query.of(q -> q.wildcard(
                    WildcardQuery.of(w -> w
                            .field("searchTags.keyword")
                            .value("*" + searchString.toLowerCase() + "*"))
            ));
            boolQueryBuilder.must(wildcardQuery);
        }
    }

    private void addFacetsToSearchSourceBuilder(
            List<String> facets, SearchRequest.Builder searchRequestBuilder) {
        if (facets != null && !facets.isEmpty()) {
            Map<String, Aggregation> aggregationMap = facets.stream()
                    .collect(Collectors.toMap(
                            field -> field + "_agg",
                            field -> Aggregation.of(a -> a.terms(
                                    TermsAggregation.of(t -> t.field(field + ".keyword").size(250))))
                    ));
            searchRequestBuilder.aggregations(aggregationMap);
        }
    }

    private boolean isNotBlank(String value) {
        return value != null && !value.trim().isEmpty();
    }

    @Override
    public void deleteDocumentsByCriteria(String esIndexName, Query query) {
        try {
            HitsMetadata<Object> searchHits = executeSearch(esIndexName, query);
            assert searchHits.total() != null;
            if (searchHits.total().value() > 0) {
                BulkResponse bulkResponse = deleteMatchingDocuments(esIndexName, searchHits);
                if (!bulkResponse.errors()) {
                    log.info("Documents matching the criteria deleted successfully from Elasticsearch.");
                } else {
                    log.error("Some documents failed to delete from Elasticsearch.");
                }
            } else {
                log.info("No documents match the criteria.");
            }
        } catch (Exception e) {
            log.error("Error occurred during deleting documents by criteria from Elasticsearch.", e);
        }
    }

    private HitsMetadata<Object> executeSearch(String esIndexName, Query query) throws IOException {
        SearchRequest searchRequest = new SearchRequest.Builder()
                .index(esIndexName)
                .query(query)
                .build();
        SearchResponse<Object> searchResponse =
                elasticsearchClient.search(searchRequest, Object.class);
        return searchResponse.hits();
    }

    private BulkResponse deleteMatchingDocuments(String esIndexName,  HitsMetadata<Object> searchHits)
            throws IOException {
        List<BulkOperation> operations = new ArrayList<>();
        for (Hit<Object> hit : searchHits.hits()) {
            new DeleteRequest.Builder()
                    .index(esIndexName)
                    .id(hit.id())
                    .build();
            operations.add(new BulkOperation.Builder().delete(d -> d.index(esIndexName).id(hit.id())).build());
        }
        BulkRequest bulkRequest = new BulkRequest.Builder().operations(operations).build();
        return elasticsearchClient.bulk(bulkRequest);
    }

    private boolean isRangeQuery(Map<String, Object> nestedMap) {
        return nestedMap.keySet().stream().anyMatch(key -> key.equals(Constants.SEARCH_OPERATION_GREATER_THAN_EQUALS) ||
                key.equals(Constants.SEARCH_OPERATION_LESS_THAN_EQUALS) || key.equals(Constants.SEARCH_OPERATION_GREATER_THAN) ||
                key.equals(Constants.SEARCH_OPERATION_LESS_THAN));
    }

    private Query buildQueryPart(Map<String, Object> queryMap) {
        log.info("Search:: buildQueryPart");
        if (queryMap == null || queryMap.isEmpty()) {
            return QueryBuilders.matchAll().build()._toQuery();
        }
        for (Entry<String, Object> entry : queryMap.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();

            switch (key) {
                case Constants.BOOL:
                    return buildBoolQuery((Map<String, Object>) value)._toQuery();
                case Constants.TERM:
                    return buildTermQuery((Map<String, Object>) value);
                case Constants.TERMS:
                    return buildTermsQuery((Map<String, Object>) value);
                case Constants.MATCH:
                    return buildMatchQuery((Map<String, Object>) value);
                case Constants.RANGE:
                    return buildRangeQuery((Map<String, Object>) value);
                default:
                    throw new IllegalArgumentException(Constants.UNSUPPORTED_QUERY + key);
            }
        }

        return null;
    }

    private BoolQuery buildBoolQuery(Map<String, Object> boolMap) {
        log.info("Search:: builderBoolQuery");
        BoolQuery.Builder boolQueryBuilder = QueryBuilders.bool();
        if (boolMap.containsKey(Constants.MUST)) {
            List<Map<String, Object>> mustList = (List<Map<String, Object>>) boolMap.get("must");
            mustList.forEach(must -> boolQueryBuilder.must(buildQueryPart(must)));
        }
        if (boolMap.containsKey(Constants.FILTER)) {
            List<Map<String, Object>> filterList = (List<Map<String, Object>>) boolMap.get("filter");
            filterList.forEach(filter -> boolQueryBuilder.filter(buildQueryPart(filter)));
        }
        if (boolMap.containsKey(Constants.MUST_NOT)) {
            List<Map<String, Object>> mustNotList = (List<Map<String, Object>>) boolMap.get("must_not");
            mustNotList.forEach(mustNot -> boolQueryBuilder.mustNot(buildQueryPart(mustNot)));
        }
        if (boolMap.containsKey(Constants.SHOULD)) {
            List<Map<String, Object>> shouldList = (List<Map<String, Object>>) boolMap.get("should");
            shouldList.forEach(should -> boolQueryBuilder.should(buildQueryPart(should)));
        }

        return boolQueryBuilder.build();
    }

    private Query buildTermQuery(Map<String, Object> termMap) {
        log.info("search::buildTermQuery");
        BoolQuery.Builder boolQueryBuilder = QueryBuilders.bool();
        for (Entry<String, Object> entry : termMap.entrySet()) {
            boolQueryBuilder.must(QueryBuilders.term(t -> t.field(entry.getKey()).value((FieldValue) entry.getValue())));
        }
        return boolQueryBuilder.build()._toQuery();
    }

    private Query buildTermsQuery(Map<String, Object> termsMap) {
        log.info("search:: buildTermsQuery");
        BoolQuery.Builder boolQueryBuilder = QueryBuilders.bool();
        for (Entry<String, Object> entry : termsMap.entrySet()) {
            boolQueryBuilder.must(QueryBuilders.terms(t -> t.field(entry.getKey()).terms((TermsQueryField) entry.getValue())));
        }
        return boolQueryBuilder.build()._toQuery();
    }

    private Query buildMatchQuery(Map<String, Object> matchMap) {
        log.info("search:: buildMatchQuery");
        BoolQuery.Builder boolQueryBuilder = QueryBuilders.bool();
        for (Entry<String, Object> entry : matchMap.entrySet()) {
            boolQueryBuilder.must(QueryBuilders.match(m -> m.field(entry.getKey()).query((FieldValue) entry.getValue())));
        }
        return boolQueryBuilder.build()._toQuery();
    }

    private Query buildRangeQuery(Map<String, Object> rangeMap) {
        log.info("search:: buildRangeQuery");
        BoolQuery.Builder boolQueryBuilder = QueryBuilders.bool();
        for (Entry<String, Object> entry : rangeMap.entrySet()) {
            Map<String, Object> rangeConditions = (Map<String, Object>) entry.getValue();
            RangeQuery.Builder rangeQueryBuilder = new RangeQuery.Builder().field(entry.getKey());
            rangeConditions.forEach((condition, value) -> {
                switch (condition) {
                    case "gt":
                        rangeQueryBuilder.gt(JsonData.of(value));
                        break;
                    case "gte":
                        rangeQueryBuilder.gte(JsonData.of(value));
                        break;
                    case "lt":
                        rangeQueryBuilder.lt(JsonData.of(value));
                        break;
                    case "lte":
                        rangeQueryBuilder.lte(JsonData.of(value));
                        break;
                    default:
                        throw new IllegalArgumentException(Constants.UNSUPPORTED_RANGE + condition);
                }
            });
            boolQueryBuilder.must(rangeQueryBuilder.build()._toQuery());
        }
        return boolQueryBuilder.build()._toQuery();
    }

    @Override
    public boolean isIndexPresent(String indexName) {
        try {
            GetIndexRequest request = new GetIndexRequest.Builder().index(indexName).build();
            GetIndexResponse response = elasticsearchClient.indices().get(request);
            return response != null;
        } catch (IOException e) {
            log.error("Error checking if index exists", e);
            return false;
        }
    }

    @Override
    public BulkResponse saveAll(String esIndexName, List<JsonNode> entities) throws IOException {
        try {
            log.info("EsUtilServiceImpl :: saveAll");
            List<BulkOperation> operations = new ArrayList<>();
            entities.forEach(entity -> {
                String formattedId = entity.get(Constants.ID).asText();
                Map<String, Object> entityMap = objectMapper.convertValue(entity, Map.class);
                BulkOperation operation = BulkOperation.of(b -> b
                        .index(i -> i
                                .index(esIndexName)
                                .id(formattedId)
                                .document(entityMap)
                        )
                );
                operations.add(operation);
            });

            BulkRequest bulkRequest = BulkRequest.of(b -> b.operations(operations));
            return elasticsearchClient.bulk(bulkRequest);
        } catch (Exception e) {
            log.error(e.getMessage());
            throw new CustomException("error bulk uploading", e.getMessage(),
                HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @Override
    public Map<String, Object> readDocument(String esIndexName, String id) {
        try {
            GetRequest getRequest = new GetRequest.Builder()
                    .index(esIndexName)
                    .id(id)
                    .build();
            GetResponse<Object> getResponse = elasticsearchClient.get(getRequest, Object.class);
            if (getResponse.found() && getResponse.source() instanceof Map) {
                return (Map<String, Object>) getResponse.source();
            } else {
                log.info("Document not found in ES for index: {} and id: {}", esIndexName, id);
                return Map.of();
            }
        } catch (Exception e) {
            log.error("Error reading document from ES for index: {} and id: {}", esIndexName, id, e);
            return Map.of();
        }
    }


}
