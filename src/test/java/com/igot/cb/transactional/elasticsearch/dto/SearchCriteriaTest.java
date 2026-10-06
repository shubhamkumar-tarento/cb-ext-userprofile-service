package com.igot.cb.transactional.elasticsearch.dto;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SearchCriteriaTest {

    @Test
    void testNoArgsConstructorAndSettersAndGetters() {

        SearchCriteria criteria = new SearchCriteria();

        HashMap<String, Object> filterMap = new HashMap<>();
        filterMap.put("key", "value");

        List<String> requestedFields = Arrays.asList("field1", "field2");
        List<String> facets = Arrays.asList("facet1", "facet2");

        Map<String, Object> query = new HashMap<>();
        query.put("match", "value");

        criteria.setFilterCriteriaMap(filterMap);
        criteria.setRequestedFields(requestedFields);
        criteria.setPageNumber(2);
        criteria.setPageSize(20);
        criteria.setOrderBy("createdOn");
        criteria.setOrderDirection("desc");
        criteria.setSearchString("test");
        criteria.setFacets(facets);
        criteria.setQuery(query);
        criteria.setStartsWith("abc");
        criteria.setStartsWithField("name");

        assertEquals(filterMap, criteria.getFilterCriteriaMap());
        assertEquals(requestedFields, criteria.getRequestedFields());
        assertEquals(2, criteria.getPageNumber());
        assertEquals(20, criteria.getPageSize());
        assertEquals("createdOn", criteria.getOrderBy());
        assertEquals("desc", criteria.getOrderDirection());
        assertEquals("test", criteria.getSearchString());
        assertEquals(facets, criteria.getFacets());
        assertEquals(query, criteria.getQuery());
        assertEquals("abc", criteria.getStartsWith());
        assertEquals("name", criteria.getStartsWithField());
    }

    @Test
    void testAllArgsConstructor() {

        HashMap<String, Object> filterMap = new HashMap<>();
        filterMap.put("key", "value");

        List<String> requestedFields = Arrays.asList("field1", "field2");
        List<String> facets = Arrays.asList("facet1", "facet2");

        Map<String, Object> query = new HashMap<>();
        query.put("match", "value");

        SearchCriteria criteria = new SearchCriteria(
                filterMap,
                requestedFields,
                1,
                10,
                "createdOn",
                "asc",
                "searchText",
                facets,
                query,
                "start",
                "name"
        );

        assertEquals(filterMap, criteria.getFilterCriteriaMap());
        assertEquals(requestedFields, criteria.getRequestedFields());
        assertEquals(1, criteria.getPageNumber());
        assertEquals(10, criteria.getPageSize());
        assertEquals("createdOn", criteria.getOrderBy());
        assertEquals("asc", criteria.getOrderDirection());
        assertEquals("searchText", criteria.getSearchString());
        assertEquals(facets, criteria.getFacets());
        assertEquals(query, criteria.getQuery());
        assertEquals("start", criteria.getStartsWith());
        assertEquals("name", criteria.getStartsWithField());
    }

    @Test
    void testNullValuesHandling() {

        SearchCriteria criteria = new SearchCriteria();

        criteria.setFilterCriteriaMap(null);
        criteria.setRequestedFields(null);
        criteria.setQuery(null);
        criteria.setFacets(null);

        assertNull(criteria.getFilterCriteriaMap());
        assertNull(criteria.getRequestedFields());
        assertNull(criteria.getQuery());
        assertNull(criteria.getFacets());
    }

    @Test
    void testMapReferenceBehavior() {

        HashMap<String, Object> filterMap = new HashMap<>();
        filterMap.put("key1", "value1");

        SearchCriteria criteria = new SearchCriteria();
        criteria.setFilterCriteriaMap(filterMap);

        // mutate original map
        filterMap.put("key2", "value2");

        assertTrue(criteria.getFilterCriteriaMap().containsKey("key2"));
    }
}
