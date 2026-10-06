package com.igot.cb.transactional.elasticsearch.service;

import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.igot.cb.exceptions.CustomException;
import com.igot.cb.transactional.elasticsearch.dto.SearchCriteria;
import com.igot.cb.transactional.elasticsearch.dto.SearchResult;

import java.io.IOException;
import java.util.List;
import java.util.Map;


public interface EsClientService {
  String addDocument(String esIndexName, String type, String id, Map<String, Object> document, String jsonFilePath);

  void updateDocument(String index, String indexType, String entityId, Map<String, Object> document, String jsonFilePath);

  void deleteDocument(String documentId, String esIndexName);

  void deleteDocumentsByCriteria(String esIndexName, Query query);

  SearchResult searchDocuments(String esIndexName, SearchCriteria searchCriteria) throws CustomException;

  boolean isIndexPresent(String indexName);

  BulkResponse saveAll(String esIndexName, List<JsonNode> entities) throws IOException;

  /**
   * Reads a document from Elasticsearch by index and id.
   * @param esIndexName The Elasticsearch index name.
   * @param id The document id.
   * @return The document as a Map<String, Object>, or null if not found.
   */
  Map<String, Object> readDocument(String esIndexName, String id);
}
