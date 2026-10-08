package com.igot.cb.transactional.cassandrautils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import com.datastax.oss.driver.api.core.CqlIdentifier;
import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.ColumnDefinition;
import com.datastax.oss.driver.api.core.cql.ColumnDefinitions;
import com.datastax.oss.driver.api.core.cql.ResultSet;
import com.datastax.oss.driver.api.core.cql.Row;
import com.datastax.oss.driver.api.core.cql.SimpleStatement;
import com.datastax.oss.driver.api.querybuilder.QueryBuilder;
import com.datastax.oss.driver.api.querybuilder.relation.Relation;
import com.datastax.oss.driver.api.querybuilder.update.Assignment;
import com.datastax.oss.driver.api.querybuilder.update.Update;
import com.datastax.oss.driver.api.querybuilder.update.UpdateStart;
import com.datastax.oss.driver.api.querybuilder.update.UpdateWithAssignments;
import com.igot.cb.util.Constants;

@RunWith(MockitoJUnitRunner.class)
public class CassandraUtilTest {

    @Mock
    private CassandraConnectionManager connectionManager;

    @Mock
    private CqlSession session;

    @InjectMocks
    private CassandraOperationImpl cassandraOperation;

    @Test
    public void testGetPreparedStatement_WithSingleField() {
        // Arrange
        String keyspaceName = "test_keyspace";
        String tableName = "test_table";
        Map<String, Object> map = new LinkedHashMap<>(); // Using LinkedHashMap for predictable order
        map.put("id", "123");

        // Act
        String result = CassandraUtil.getPreparedStatement(keyspaceName, tableName, map);

        // Assert
        String expected = Constants.INSERT_INTO + keyspaceName + Constants.DOT + tableName +
                Constants.OPEN_BRACE + "id" + Constants.VALUES_WITH_BRACE +
                Constants.QUE_MARK + Constants.CLOSING_BRACE;
        assertEquals(expected, result);
    }

    @Test
    public void testGetPreparedStatement_WithMultipleFields() {
        // Arrange
        String keyspaceName = "test_keyspace";
        String tableName = "test_table";
        Map<String, Object> map = new LinkedHashMap<>(); // Using LinkedHashMap for predictable order
        map.put("id", "123");
        map.put("name", "Test User");
        map.put("age", 30);

        // Act
        String result = CassandraUtil.getPreparedStatement(keyspaceName, tableName, map);

        // Assert
        String expected = Constants.INSERT_INTO + keyspaceName + Constants.DOT + tableName +
                Constants.OPEN_BRACE + "id,name,age" + Constants.VALUES_WITH_BRACE +
                Constants.QUE_MARK + Constants.COMMA + Constants.QUE_MARK + Constants.COMMA +
                Constants.QUE_MARK + Constants.CLOSING_BRACE;
        assertEquals(expected, result);
    }

    @Test
    public void testGetPreparedStatement_WithEmptyMap() {
        // Arrange
        String keyspaceName = "test_keyspace";
        String tableName = "test_table";
        Map<String, Object> map = new HashMap<>();

        // Act
        String result = CassandraUtil.getPreparedStatement(keyspaceName, tableName, map);

        // Assert
        String expected = Constants.INSERT_INTO + keyspaceName + Constants.DOT + tableName +
                Constants.OPEN_BRACE + "" + Constants.VALUES_WITH_BRACE +
                "" + Constants.CLOSING_BRACE;
        assertEquals(expected, result);
    }

    @Test
    public void testCreateResponse() {
        ResultSet mockResultSet = mock(ResultSet.class);
        Row mockRow = mock(Row.class);
        Iterator<Row> rowIterator = Collections.singletonList(mockRow).iterator();
        when(mockResultSet.iterator()).thenReturn(rowIterator);
        Map<String, String> mockColumnsMapping = new HashMap<>();
        mockColumnsMapping.put("id", "id_column");
        mockColumnsMapping.put("name", "name_column");
        when(mockRow.getObject("id_column")).thenReturn(101);
        when(mockRow.getObject("name_column")).thenReturn("Alice");
        try (MockedStatic<CassandraUtil> mockedStatic = Mockito.mockStatic(CassandraUtil.class)) {
            mockedStatic.when(() -> CassandraUtil.fetchColumnsMapping(mockResultSet))
                    .thenReturn(mockColumnsMapping);
            mockedStatic.when(() -> CassandraUtil.createResponse(mockResultSet))
                    .thenCallRealMethod();
            List<Map<String, Object>> result = CassandraUtil.createResponse(mockResultSet);
            assertEquals(1, result.size());
            Map<String, Object> rowMap = result.get(0);
            assertEquals(101, rowMap.get("id"));
            assertEquals("Alice", rowMap.get("name"));
        }
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testCreateResponse_WithKey() {
        ResultSet mockResultSet = mock(ResultSet.class);
        Row mockRow = mock(Row.class);
        Iterator<Row> rowIterator = Collections.singletonList(mockRow).iterator();
        when(mockResultSet.iterator()).thenReturn(rowIterator);
        Map<String, String> columnMapping = new HashMap<>();
        columnMapping.put("userId", "user_id_column");
        columnMapping.put("email", "email_column");
        when(mockRow.getObject("user_id_column")).thenReturn("123");
        when(mockRow.getObject("email_column")).thenReturn("test@example.com");
        try (MockedStatic<CassandraUtil> staticMock = mockStatic(CassandraUtil.class)) {
            staticMock.when(() -> CassandraUtil.fetchColumnsMapping(mockResultSet))
                    .thenReturn(columnMapping);
            staticMock.when(() -> CassandraUtil.createResponse(mockResultSet, "userId"))
                    .thenCallRealMethod();
            Map<String, Object> result = CassandraUtil.createResponse(mockResultSet, "userId");
            assertEquals(1, result.size());
            assertTrue(result.containsKey("123"));
            Map<String, Object> rowMap = (Map<String, Object>) result.get("123");
            assertEquals("123", rowMap.get("userId"));
            assertEquals("test@example.com", rowMap.get("email"));
        }
    }

    @Test
    public void testFetchColumnsMapping_ReturnsExpectedMapping() {
        ResultSet mockResultSet = mock(ResultSet.class);
        ColumnDefinitions mockColumnDefinitions = mock(ColumnDefinitions.class);
        ColumnDefinition mockColumnDefinition = mock(ColumnDefinition.class);
        CqlIdentifier mockCqlIdentifier = mock(CqlIdentifier.class);

        when(mockCqlIdentifier.asInternal()).thenReturn("id");
        when(mockColumnDefinition.getName()).thenReturn(mockCqlIdentifier);
        doAnswer(invocation -> {
            java.util.function.Consumer<ColumnDefinition> consumer = invocation.getArgument(0);
            consumer.accept(mockColumnDefinition);
            return null;
        }).when(mockColumnDefinitions).forEach(any());
        when(mockResultSet.getColumnDefinitions()).thenReturn(mockColumnDefinitions);

        Map<String, String> result = CassandraUtil.fetchColumnsMapping(mockResultSet);

        assertEquals(1, result.size());
        assertEquals("id", result.get("id"));
    }

    @Test
    public void testFetchColumnsMapping_WithEmptyColumnDefinitions() {
        ResultSet mockResultSet = mock(ResultSet.class);
        ColumnDefinitions mockColumnDefinitions = mock(ColumnDefinitions.class);

        when(mockResultSet.getColumnDefinitions()).thenReturn(mockColumnDefinitions);

        Map<String, String> result = CassandraUtil.fetchColumnsMapping(mockResultSet);

        assertEquals(0, result.size());
    }

    @Test
    public void testUpdateRecordByCompositeKey_Success() {
        String keyspace = "ks";
        String table = "tbl";
        Map<String, Object> updateAttrs = Map.of("name", "John");
        Map<String, Object> compositeKey = Map.of("id", 1, "type", "A");

        UpdateStart updateStart = QueryBuilder.update(keyspace, table);
        Assignment[] assignments = updateAttrs.entrySet().stream()
                .map(entry -> Assignment.setColumn(entry.getKey(), QueryBuilder.literal(entry.getValue())))
                .toArray(Assignment[]::new);
        UpdateWithAssignments updateWithAssignments = updateStart.set(assignments);
        Relation[] relations = compositeKey.entrySet().stream()
                .map(entry -> Relation.column(entry.getKey()).isEqualTo(QueryBuilder.literal(entry.getValue())))
                .toArray(Relation[]::new);
        Update update = updateWithAssignments.where(relations);
        update.build();

        when(connectionManager.getSession(keyspace)).thenReturn(session);
        when(session.execute(any(SimpleStatement.class))).thenReturn(mock(ResultSet.class));

        Map<String, Object> result = cassandraOperation.updateRecordByCompositeKey(keyspace, table, updateAttrs, compositeKey);

        assertEquals(Constants.SUCCESS, result.get(Constants.RESPONSE));
        verify(session).execute(any(SimpleStatement.class));
    }
}