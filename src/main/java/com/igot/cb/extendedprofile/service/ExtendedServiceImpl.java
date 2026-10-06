package com.igot.cb.extendedprofile.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.igot.cb.authentication.util.AccessTokenValidator;
import com.igot.cb.transactional.cassandrautils.CassandraOperation;
import com.igot.cb.util.ApiResponse;
import com.igot.cb.util.Constants;
import com.igot.cb.util.ProjectUtil;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.MapUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.apache.commons.lang3.StringUtils;

import java.util.*;

/**
 * @author mahesh.vakkund
 */
@Service
@RequiredArgsConstructor
public class ExtendedServiceImpl implements ExtendedProfileService {
    private final Logger logger = LoggerFactory.getLogger(ExtendedServiceImpl.class);

    private final AccessTokenValidator accessTokenValidator;

    private final CassandraOperation cassandraOperation;


    @Override
    public ApiResponse getStatesList(String authToken) {
        logger.info("ExtendedServiceImpl::getStatesList started");
        ApiResponse outgoingResponse = ProjectUtil.createDefaultResponse(Constants.API_GET_STATE_LIST);
        String userId = accessTokenValidator.fetchUserIdFromAccessToken(authToken);
        if (StringUtils.isEmpty(userId)) {
            updateErrorDetails(outgoingResponse, Constants.USER_ID_DOESNT_EXIST, HttpStatus.BAD_REQUEST);
            return outgoingResponse;
        }
        Map<String, Object> propertyMap = new HashMap<>();
        propertyMap.put(Constants.CONTEXT_TYPE, Constants.STATE);
        try {
            List<Map<String, Object>> rawStateData = cassandraOperation.getRecordsByPropertiesByKey(
                    Constants.KEYSPACE_SUNBIRD,
                    Constants.MASTER_DATA,
                    propertyMap,
                    List.of(Constants.CONTEXT_NAME, Constants.ID),
                    Constants.CONTEXT_TYPE);
            List<Map<String, Object>> stateDataList = rawStateData.stream()
                    .map(map -> {
                        Map<String, Object> transformedMap = new HashMap<>();
                        transformedMap.put(Constants.STATE_NAME, map.get(Constants.CONTEXT_NAME));
                        transformedMap.put(Constants.STATE_ID, map.get(Constants.ID));
                        return transformedMap;
                    })
                    .toList();
            outgoingResponse.getResult().put(Constants.STATES_LIST, stateDataList);
            logger.info("ExtendedServiceImpl::getStatesList completed successfully with {} states", stateDataList.size());
        } catch (Exception e) {
            logger.error("Error while fetching states list from Cassandra: {}", e.getMessage(), e);
            updateErrorDetails(outgoingResponse, "Internal server error while fetching states list",
                    HttpStatus.INTERNAL_SERVER_ERROR);
            return outgoingResponse;
        }
        return outgoingResponse;
    }


    @Override
    public ApiResponse getDistrictsList(String authToken,Map<String, Object> requestBody) {
        logger.info("ExtendedServiceImpl::getDistrictsList started");
        ApiResponse outgoingResponse = ProjectUtil.createDefaultResponse(Constants.API_GET_DISTRICT_LIST);
        String userId = accessTokenValidator.fetchUserIdFromAccessToken(authToken);
        if (StringUtils.isEmpty(userId)) {
            updateErrorDetails(outgoingResponse, Constants.USER_ID_DOESNT_EXIST, HttpStatus.BAD_REQUEST);
            return outgoingResponse;
        }
        if (MapUtils.isEmpty(requestBody)  || !requestBody.containsKey(Constants.CONTEXT_NAME)) {
            outgoingResponse.getParams().setStatus(Constants.FAILED);
            outgoingResponse.getParams().setErrMsg("Context name is missing in the request");
            outgoingResponse.setResponseCode(HttpStatus.OK);
            return outgoingResponse;
        }
        Map<String, Object> propertyMap = new HashMap<>();
        propertyMap.put(Constants.CONTEXT_TYPE, Constants.DISTRICT);
        propertyMap.put(Constants.CONTEXT_NAME, requestBody.get(Constants.CONTEXT_NAME));
        try {
            List<Map<String, Object>> cassandraResults = cassandraOperation.getRecordsByPropertiesByKey(
                    Constants.KEYSPACE_SUNBIRD,
                    Constants.MASTER_DATA,
                    propertyMap,
                    List.of(Constants.CONTEXT_NAME, Constants.CONTEXT_DATA),
                    Constants.CONTEXT_TYPE);
            List<Map<String, Object>> districtsByState = cassandraResults.stream()
                    .map(map -> {
                        Map<String, Object> transformedMap = new HashMap<>();
                        transformedMap.put(Constants.STATE_NAME, map.get(Constants.CONTEXT_NAME));
                        String contextDataJson = (String) map.get(Constants.CONTEXT_DATA);
                        List<String> districtsList = new ArrayList<>();
                        if (StringUtils.isNotEmpty(contextDataJson)) {
                            try {
                                ObjectMapper mapper = new ObjectMapper();
                                districtsList = mapper.readValue(contextDataJson,
                                        new TypeReference<List<String>>() {});
                            } catch (Exception e) {
                                logger.error("Error parsing district data for state {}: {}",
                                        map.get(Constants.CONTEXT_NAME), e.getMessage(), e);
                            }
                        }
                        transformedMap.put(Constants.DISTRICTS, districtsList);
                        return transformedMap;
                    })
                    .toList();
            outgoingResponse.getResult().put(Constants.DISTRICTS_LIST, districtsByState);
            logger.info("ExtendedServiceImpl::getDistrictsList completed successfully with {} states",
                    districtsByState.size());
        } catch (Exception e) {
            logger.error("Error while fetching districts list from Cassandra: {}", e.getMessage(), e);
            updateErrorDetails(outgoingResponse, "Internal server error while fetching districts list",
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }
        return outgoingResponse;
    }

    private void updateErrorDetails(ApiResponse response, String errorMessage, HttpStatus httpStatus) {
        response.getParams().setStatus(Constants.FAILED);
        response.getParams().setErrMsg(errorMessage);
        response.setResponseCode(httpStatus);
    }
}
