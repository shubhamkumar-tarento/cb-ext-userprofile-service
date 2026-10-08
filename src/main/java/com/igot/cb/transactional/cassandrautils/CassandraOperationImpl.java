package com.igot.cb.transactional.cassandrautils;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.BoundStatement;
import com.datastax.oss.driver.api.core.cql.PreparedStatement;
import com.datastax.oss.driver.api.core.cql.ResultSet;
import com.datastax.oss.driver.api.core.cql.SimpleStatement;
import com.datastax.oss.driver.api.querybuilder.QueryBuilder;
import com.datastax.oss.driver.api.querybuilder.relation.Relation;
import com.datastax.oss.driver.api.querybuilder.select.Select;
import com.datastax.oss.driver.api.querybuilder.term.Term;
import com.datastax.oss.driver.api.querybuilder.update.Assignment;
import com.datastax.oss.driver.api.querybuilder.update.Update;
import com.datastax.oss.driver.api.querybuilder.update.UpdateStart;
import com.datastax.oss.driver.api.querybuilder.update.UpdateWithAssignments;
import com.igot.cb.util.ApiResponse;
import com.igot.cb.util.Constants;
import com.igot.cb.util.ProjectUtil;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.nio.ByteBuffer;
import java.text.MessageFormat;
import java.util.*;
import java.util.Map.Entry;
import java.util.stream.Collectors;

/**
 * @author Mahesh RV
 * @author Ruksana
 */
@Component
@RequiredArgsConstructor
public class CassandraOperationImpl implements CassandraOperation {

    private Logger logger = LoggerFactory.getLogger(CassandraOperationImpl.class);

    private final CassandraConnectionManager connectionManager;

    private final ProjectUtil projectUtil;

    public Select processQuery(String keyspaceName, String tableName, Map<String, Object> propertyMap,
            List<String> fields) {
        Select select;
        if (CollectionUtils.isNotEmpty(fields)) {
            select = QueryBuilder.selectFrom(keyspaceName, tableName).columns(fields);
        } else {
            select = QueryBuilder.selectFrom(keyspaceName, tableName).all();
        }
        if (MapUtils.isEmpty(propertyMap)) {
            return select; // Build and return the query
        }
        for (Entry<String, Object> entry : propertyMap.entrySet()) {
            String columnName = entry.getKey();
            Object value = entry.getValue();
            if (value instanceof List) {
                List<?> valueList = (List<?>) value;
                if (CollectionUtils.isNotEmpty(valueList)) {
                    List<Term> terms = valueList.stream()
                            .map(QueryBuilder::literal)
                            .collect(Collectors.toList());
                    select = select.whereColumn(columnName).in(terms);
                }
            } else {
                select = select.whereColumn(columnName).isEqualTo(QueryBuilder.literal(value));
            }
        }
        return select;
    }

    @Override
    public List<Map<String, Object>> getRecordsByPropertiesByKey(String keyspaceName,
            String tableName, Map<String, Object> propertyMap, List<String> fields, String key) {
        Select selectQuery = null;
        List<Map<String, Object>> response = new ArrayList<>();
        try {
            selectQuery = processQuery(keyspaceName, tableName, propertyMap, fields);
            CqlSession session = connectionManager.getSession(keyspaceName);
            ResultSet results = session.execute(selectQuery.build());
            response = CassandraUtil.createResponse(results);
            logger.info("{}", response);

        } catch (Exception e) {
            logger.error(Constants.EXCEPTION_MSG_FETCH + Constants.LOG_TABLE_ERROR_SUFFIX, tableName, e.getMessage(), e);
        }
        return response;
    }

    @Override
    public Object insertRecord(String keyspaceName, String tableName, Map<String, Object> request) {
        ApiResponse response = new ApiResponse();
        try {
            String query = CassandraUtil.getPreparedStatement(keyspaceName, tableName, request);
            CqlSession session = connectionManager.getSession(keyspaceName);
            PreparedStatement statement = session.prepare(query);
            BoundStatement boundStatement = statement.bind(request.values().toArray());
            session.execute(boundStatement);
            response.put(Constants.RESPONSE, Constants.SUCCESS);
        } catch (Exception e) {
            String errMsg = String.format("Exception occurred while inserting record to %s %s", tableName,
                    e.getMessage());
            logger.error("Error inserting record into {}: {}", tableName, e.getMessage());
            response.put(Constants.RESPONSE, Constants.FAILED);
            response.put(Constants.ERROR_MESSAGE, errMsg);
        }
        return response;
    }

    @Override
    public List<Map<String, Object>> getRecordsByPropertiesWithoutFiltering(String keyspaceName, String tableName,
            Map<String, Object> propertyMap, List<String> fields, Integer limit) {

        List<Map<String, Object>> response = new ArrayList<>();
        try {
            Select selectQuery = null;
            selectQuery = processQuery(keyspaceName, tableName, propertyMap, fields);

            if (limit != null)
                selectQuery = selectQuery.limit(limit);
            String queryString = selectQuery.toString();
            SimpleStatement statement = SimpleStatement.newInstance(queryString);
            ResultSet results = connectionManager.getSession(keyspaceName).execute(statement);
            response = CassandraUtil.createResponse(results);

        } catch (Exception e) {
            logger.error("Error fetching records from {}: {}", tableName, e.getMessage());
        }
        return response;
    }

    @Override
    public Map<String, Object> updateRecord(
            String keyspaceName, String tableName, Map<String, Object> request) {
        long startTime = System.currentTimeMillis();
        logger.debug("Cassandra Service updateRecord method started at == {}", startTime);
        Map<String, Object> response = new HashMap<>();
        String query = getUpdateQueryStatement(keyspaceName, tableName, request);
        try {
            PreparedStatement statement = connectionManager.getSession(keyspaceName).prepare(query);
            Object[] array = new Object[request.size()];
            int i = 0;
            String str = "";
            int index = query.lastIndexOf(Constants.SET.trim());
            str = query.substring(index + 4);
            str = str.replace(Constants.EQUAL_WITH_QUE_MARK, "");
            str = str.replace(Constants.WHERE_ID, "");
            str = str.replace(Constants.SEMICOLON, "");
            String[] arr = str.split(",");
            for (String key : arr) {
                array[i++] = request.get(key.trim());
            }
            array[i] = request.get(Constants.ID);
            BoundStatement boundStatement = statement.bind(array);
            connectionManager.getSession(keyspaceName).execute(boundStatement);
            response.put(Constants.RESPONSE, Constants.SUCCESS);
            if (tableName.equalsIgnoreCase(Constants.USER)) {
                logger.info("Cassandra Service updateRecord in user table : {}", request);
            }
        } catch (Exception e) {
            if (e.getMessage().contains(Constants.UNKNOWN_IDENTIFIER)) {
                logger.error(
                        Constants.EXCEPTION_MSG_UPDATE + Constants.LOG_TABLE_ERROR_SUFFIX, tableName, e.getMessage(), e);
                String errMsg = String.format("Exception occurred while updating record to to %s %s", tableName,
                        e.getMessage());
                response.put(Constants.RESPONSE, Constants.FAILED);
                response.put(Constants.ERROR_MESSAGE, errMsg);
            }
            logger.error(Constants.EXCEPTION_MSG_UPDATE + Constants.LOG_TABLE_ERROR_SUFFIX, tableName, e.getMessage(), e);
        } finally {
            logQueryElapseTime("updateRecord", startTime, query);
        }
        return response;
    }

    public static String getUpdateQueryStatement(
            String keyspaceName, String tableName, Map<String, Object> map) {
        StringBuilder query = new StringBuilder(
                Constants.UPDATE + keyspaceName + Constants.DOT + tableName + Constants.SET);
        Set<String> key = new HashSet<>(map.keySet());
        key.remove(Constants.ID);
        query.append(String.join(" = ? ,", key));
        query.append(
                Constants.EQUAL_WITH_QUE_MARK + Constants.WHERE_ID + Constants.EQUAL_WITH_QUE_MARK);
        return query.toString();
    }

    protected void logQueryElapseTime(
            String operation, long startTime, String query) {
        logger.info("Cassandra query : {}", query);
        long stopTime = System.currentTimeMillis();
        long elapsedTime = stopTime - startTime;
        if (logger.isDebugEnabled()) {
            String message = "Cassandra operation {0} started at {1} and completed at {2}. Total time elapsed is {3}.";
            MessageFormat mf = new MessageFormat(message);
            logger.debug(mf.format(new Object[] { operation, startTime, stopTime, elapsedTime }));
        }
    }

    @Override
    public Map<String, Object> updateRecordByCompositeKey(String keyspaceName, String tableName,
            Map<String, Object> updateAttributes,
            Map<String, Object> compositeKey) {
        Map<String, Object> response = new HashMap<>();
        CqlSession session = null;
        try {
            session = connectionManager.getSession(keyspaceName);
            UpdateStart updateStart = QueryBuilder.update(keyspaceName, tableName);
            UpdateWithAssignments updateWithAssignments = updateStart.set(updateAttributes.entrySet().stream()
                    .map(entry -> Assignment.setColumn(entry.getKey(), QueryBuilder.literal(entry.getValue())))
                    .toArray(Assignment[]::new));
            Update update = updateWithAssignments.where(compositeKey.entrySet().stream()
                    .map(entry -> Relation.column(entry.getKey()).isEqualTo(QueryBuilder.literal(entry.getValue())))
                    .toArray(Relation[]::new));
            SimpleStatement statement = update.build();
            session.execute(statement);
            response.put(Constants.RESPONSE, Constants.SUCCESS);
        } catch (Exception e) {
            String errMsg = String.format("Exception occurred while updating record to %s: %s", tableName,
                    e.getMessage());
            logger.error(errMsg, e);
            response.put(Constants.RESPONSE, Constants.FAILED);
            response.put(Constants.ERROR_MESSAGE, errMsg);
        }
        return response;
    }

    public List<Map<String, Object>> getAllRecordsByPrimaryKey(String keyspaceName, String tableName,
            Map<String, Object> primaryKey, List<String> fields, int pageSize) {
        List<Map<String, Object>> allResults = new ArrayList<>();
        if (logger.isInfoEnabled()) {
            logger.info(
                    "CassandraOperationImpl::getAllRecordsByPrimaryKey: Fetching all records for table: {} with primaryKey: {}",
                    tableName,
                    projectUtil.convertToString(primaryKey));
        }
        ByteBuffer pagingState = null;
        try {
            do {
                Select selectQuery = processQuery(keyspaceName, tableName, primaryKey, fields);
                SimpleStatement statement = selectQuery.limit(pageSize).build();

                if (pagingState != null) {
                    statement = statement.setPagingState(pagingState);
                }

                CqlSession session = connectionManager.getSession(keyspaceName);
                ResultSet resultSet = session.execute(statement);

                List<Map<String, Object>> pageResults = CassandraUtil.createResponse(resultSet);
                allResults.addAll(pageResults);

                pagingState = resultSet.getExecutionInfo().getPagingState();
            } while (pagingState != null);
            logger.info("CassandraOperationImpl::getAllRecordsByPrimaryKey: Fetched {} records from table: {}",
                    allResults.size(), tableName);
        } catch (Exception e) {
            logger.error(
                    "CassandraOperationImpl::getAllRecordsByPrimaryKey: Failed to fetch all records for table {}: with primaryKey: {}",
                    tableName,
                    projectUtil.convertToString(primaryKey), e);
        }
        return allResults;
    }

    @Override
    public Map<String, Object> deleteRecordByCompositeKey(String keyspaceName, String tableName, Map<String, Object> compositeKey) {
        Map<String, Object> response = new HashMap<>();
        try {
            StringBuilder queryBuilder = new StringBuilder();
            queryBuilder.append("DELETE FROM ")
                        .append(keyspaceName).append(".").append(tableName)
                        .append(" WHERE ");
            List<Object> values = new ArrayList<>();
            int count = 0;
            for (Map.Entry<String, Object> entry : compositeKey.entrySet()) {
                if (count > 0) queryBuilder.append(" AND ");
                queryBuilder.append(entry.getKey()).append(" = ?");
                values.add(entry.getValue());
                count++;
            }
            String query = queryBuilder.toString();
            CqlSession session = connectionManager.getSession(keyspaceName);
            PreparedStatement statement = session.prepare(query);
            BoundStatement boundStatement = statement.bind(values.toArray());
            session.execute(boundStatement);
            response.put(Constants.RESPONSE, Constants.SUCCESS);
        } catch (Exception e) {
            logger.error("Error deleting record from {}: {}", tableName, e.getMessage());
            response.put(Constants.RESPONSE, Constants.FAILED);
            response.put(Constants.ERROR_MESSAGE, e.getMessage());
        }
        return response;
    }
}
