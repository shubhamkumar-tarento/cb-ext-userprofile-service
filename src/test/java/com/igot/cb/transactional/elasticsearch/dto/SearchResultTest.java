package com.igot.cb.transactional.elasticsearch.dto;
import org.junit.jupiter.api.Test;

import java.io.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
class SearchResultTest {

    @Test
    void testNoArgsConstructorAndSettersAndGetters() {

        SearchResult result = new SearchResult();

        List<Map<String, Object>> data = new ArrayList<>();
        Map<String, Object> row = new HashMap<>();
        row.put("id", "1");
        data.add(row);

        Map<String, List<FacetDTO>> facets = new HashMap<>();
        facets.put("status", new ArrayList<>());

        Map<String, String> userDetails = new HashMap<>();
        userDetails.put("user1", "John");

        result.setData(data);
        result.setFacets(facets);
        result.setTotalCount(10L);
        result.setUserDetails(userDetails);

        assertEquals(data, result.getData());
        assertEquals(facets, result.getFacets());
        assertEquals(10L, result.getTotalCount());
        assertEquals(userDetails, result.getUserDetails());
    }

    @Test
    void testAllArgsConstructor() {

        List<Map<String, Object>> data = new ArrayList<>();
        Map<String, Object> row = new HashMap<>();
        row.put("name", "test");
        data.add(row);

        Map<String, List<FacetDTO>> facets = new HashMap<>();
        facets.put("category", new ArrayList<>());

        Map<String, String> userDetails = new HashMap<>();
        userDetails.put("u1", "Alice");

        SearchResult result = new SearchResult(
                data,
                facets,
                5L,
                userDetails
        );

        assertEquals(data, result.getData());
        assertEquals(facets, result.getFacets());
        assertEquals(5L, result.getTotalCount());
        assertEquals(userDetails, result.getUserDetails());
    }

    @Test
    void testNullValues() {

        SearchResult result = new SearchResult();

        result.setData(null);
        result.setFacets(null);
        result.setUserDetails(null);

        assertNull(result.getData());
        assertNull(result.getFacets());
        assertNull(result.getUserDetails());
    }

    @Test
    void testReferenceBehavior() {

        List<Map<String, Object>> data = new ArrayList<>();
        Map<String, Object> row = new HashMap<>();
        row.put("key", "value");
        data.add(row);

        SearchResult result = new SearchResult();
        result.setData(data);

        // mutate original list
        row.put("newKey", "newValue");

        assertTrue(result.getData().get(0).containsKey("newKey"));
    }

    @Test
    void testSerializable() throws Exception {

        List<Map<String, Object>> data = new ArrayList<>();
        Map<String, Object> row = new HashMap<>();
        row.put("id", "1");
        data.add(row);

        SearchResult original = new SearchResult(
                data,
                new HashMap<>(),
                2L,
                new HashMap<>()
        );

        // Serialize
        ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
        ObjectOutputStream out = new ObjectOutputStream(byteOut);
        out.writeObject(original);
        out.close();

        // Deserialize
        ObjectInputStream in = new ObjectInputStream(
                new ByteArrayInputStream(byteOut.toByteArray())
        );
        SearchResult deserialized = (SearchResult) in.readObject();
        in.close();

        assertEquals(original.getTotalCount(), deserialized.getTotalCount());
        assertEquals(original.getData(), deserialized.getData());
    }
}
