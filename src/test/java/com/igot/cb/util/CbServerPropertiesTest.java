package com.igot.cb.util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class CbServerPropertiesTest {

    private CbServerProperties properties;

    @BeforeEach
    void setUp() {
        properties = new CbServerProperties();
    }

    private void setField(String fieldName, Object value) throws Exception {
        Field field = CbServerProperties.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(properties, value);
    }

    @Test
    void testGettersAndSetters() throws Exception {
        setField("basicProfileFields", "name,email,phone");
        setField("profileCompletionRequiredFields", "field1,field2");

        // Test getBasicProfileFields
        List<String> basicFields = properties.getBasicProfileFields();
        assertEquals(3, basicFields.size());
        assertEquals("name", basicFields.get(0));

        // Test getProfileCompletionRequiredFields
        List<String> requiredFields = properties.getProfileCompletionRequiredFields();
        assertEquals(2, requiredFields.size());
        assertEquals("field1", requiredFields.get(0));

        // Test simple getter and setter
        properties.setRedisInsightIndex(5);
        assertEquals(5, properties.getRedisInsightIndex());

        properties.setSearchResultRedisTtl(100L);
        assertEquals(100L, properties.getSearchResultRedisTtl());

        properties.setSbApiKey("api-key");
        assertEquals("api-key", properties.getSbApiKey());

        properties.setRequestTimeoutMs(3000);
        assertEquals(3000, properties.getRequestTimeoutMs());

        properties.setContextType(new String[]{"A", "B"});
        assertArrayEquals(new String[]{"A", "B"}, properties.getContextType());

        properties.setExtendedFieldsConfig(List.of("ext1", "ext2"));
        assertEquals(2, properties.getExtendedFieldsConfig().size());

        properties.setRedisTestOnBorrow(Boolean.TRUE);
        assertTrue(properties.getRedisTestOnBorrow());

        properties.setRedisTimeBetweenEvictionRunsMillis(12345L);
        assertEquals(12345L, properties.getRedisTimeBetweenEvictionRunsMillis());

        properties.setCertificateCountRedisKey("certKey");
        assertEquals("certKey", properties.getCertificateCountRedisKey());

        properties.setConnectionApi("http://api");
        assertEquals("http://api", properties.getConnectionApi());
    }

    @Test
    void testRemainingPrimitiveAndWrapperGettersAndSetters() {
        properties.setMaxTotalConnections(10);
        assertEquals(10, properties.getMaxTotalConnections());

        properties.setMaxConnectionsPerRoute(20);
        assertEquals(20, properties.getMaxConnectionsPerRoute());

        properties.setRedisPoolMaxTotal(30);
        assertEquals(30, properties.getRedisPoolMaxTotal());

        properties.setRedisPoolMaxIdle(40);
        assertEquals(40, properties.getRedisPoolMaxIdle());

        properties.setRedisPoolMinIdle(5);
        assertEquals(5, properties.getRedisPoolMinIdle());

        properties.setRedisPoolMaxWait(50);
        assertEquals(50, properties.getRedisPoolMaxWait());

        properties.setRedisConnectionTimeout(6000L);
        assertEquals(6000L, properties.getRedisConnectionTimeout());

        properties.setEducationalQualificationMandatoryFields("edu1,edu2");
        assertEquals("edu1,edu2", properties.getEducationalQualificationMandatoryFields());

        properties.setServiceHistoryMandatoryFields("svc1,svc2");
        assertEquals("svc1,svc2", properties.getServiceHistoryMandatoryFields());

        properties.setAchievementsMandatoryFields("ach1,ach2");
        assertEquals("ach1,ach2", properties.getAchievementsMandatoryFields());

        properties.setFieldWeight(1.5d);
        assertEquals(1.5d, properties.getFieldWeight());

        properties.setCommunityBaseUrl("http://community");
        assertEquals("http://community", properties.getCommunityBaseUrl());

        properties.setCommunityPostCountApiUrl("http://community/posts");
        assertEquals("http://community/posts", properties.getCommunityPostCountApiUrl());

        properties.setRedisTimeout("3000");
        assertEquals("3000", properties.getRedisTimeout());

        properties.setRedisHostName("localhost");
        assertEquals("localhost", properties.getRedisHostName());

        properties.setRedisPort("6379");
        assertEquals("6379", properties.getRedisPort());

        properties.setRedisDataHostName("datahost");
        assertEquals("datahost", properties.getRedisDataHostName());

        properties.setRedisDataPort("6380");
        assertEquals("6380", properties.getRedisDataPort());

        properties.setRedisMaxIdle(7);
        assertEquals(7, properties.getRedisMaxIdle());

        properties.setRedisMaxTotal(8);
        assertEquals(8, properties.getRedisMaxTotal());

        properties.setRedisMinIdle(2);
        assertEquals(2, properties.getRedisMinIdle());

        properties.setRedisTestOnReturn(Boolean.TRUE);
        assertTrue(properties.getRedisTestOnReturn());

        properties.setRedisTestWhileIdle(Boolean.FALSE);
        assertFalse(properties.getRedisTestWhileIdle());

        properties.setRedisMinEvictableIdleTimeMillis(9999L);
        assertEquals(9999L, properties.getRedisMinEvictableIdleTimeMillis());

        properties.setRedisNumTestsPerEvictionRun(3);
        assertEquals(3, properties.getRedisNumTestsPerEvictionRun());

        properties.setRedisBlockWhenExhausted(Boolean.TRUE);
        assertTrue(properties.getRedisBlockWhenExhausted());

        properties.setDataIndex(11);
        assertEquals(11, properties.getDataIndex());

        properties.setCacheTtl(12);
        assertEquals(12, properties.getCacheTtl());

        properties.setCertificateCountRedisTtl(13);
        assertEquals(13, properties.getCertificateCountRedisTtl());

        properties.setBadgeCountRedisTtl(14);
        assertEquals(14, properties.getBadgeCountRedisTtl());

        properties.setUserProfileIndex("profileIndex");
        assertEquals("profileIndex", properties.getUserProfileIndex());

        properties.setHubGraphService("hubGraph");
        assertEquals("hubGraph", properties.getHubGraphService());

        properties.setUserEnrolmentsTable("user_enrolments");
        assertEquals("user_enrolments", properties.getUserEnrolmentsTable());

        properties.setMasterDataSearchStringRegex("^[a-z]+$");
        assertEquals("^[a-z]+$", properties.getMasterDataSearchStringRegex());

        properties.setEsDegreeIndexName("degreeIndex");
        assertEquals("degreeIndex", properties.getEsDegreeIndexName());

        properties.setEsInstituteIndexName("instituteIndex");
        assertEquals("instituteIndex", properties.getEsInstituteIndexName());

        properties.setEsMasterDataIndexDocType("docType");
        assertEquals("docType", properties.getEsMasterDataIndexDocType());

        properties.setMasterDataSearchStringMinLength("2");
        assertEquals("2", properties.getMasterDataSearchStringMinLength());

        properties.setMasterDataSearchStringMaxLength("50");
        assertEquals("50", properties.getMasterDataSearchStringMaxLength());

        properties.setLearnerServiceHost("http://learner");
        assertEquals("http://learner", properties.getLearnerServiceHost());

        properties.setPasswordResetPath("/reset");
        assertEquals("/reset", properties.getPasswordResetPath());

        properties.setRequiredFieldsProperty("req1,req2");
        assertEquals("req1,req2", properties.getRequiredFieldsProperty());

        properties.setAchievementEsRequiredFieldsMappingPath("/path/to/json");
        assertEquals("/path/to/json", properties.getAchievementEsRequiredFieldsMappingPath());

        properties.setCassandraFetchLimit(100);
        assertEquals(100, properties.getCassandraFetchLimit());

        properties.setRequireEs(true);
        assertTrue(properties.isRequireEs());

        properties.setUserCompetencyTopicName("competencyTopic");
        assertEquals("competencyTopic", properties.getUserCompetencyTopicName());

        properties.setAchievementsAllowedFields("field1,field2");
        assertEquals("field1,field2", properties.getAchievementsAllowedFields());

        properties.setBulkListContextDataFields("ctx1,ctx2");
        assertEquals("ctx1,ctx2", properties.getBulkListContextDataFields());

        properties.setBulkListResponseFields("resp1,resp2");
        assertEquals("resp1,resp2", properties.getBulkListResponseFields());

        properties.setAchievementCacheTtl(200);
        assertEquals(200, properties.getAchievementCacheTtl());

        properties.setDegreeNameRegex("^[A-Za-z ]+$");
        assertEquals("^[A-Za-z ]+$", properties.getDegreeNameRegex());

        properties.setInstituteNameRegex("^[A-Za-z0-9 ]+$");
        assertEquals("^[A-Za-z0-9 ]+$", properties.getInstituteNameRegex());

        properties.setFieldOfStudyRegex("^[A-Za-z]+$");
        assertEquals("^[A-Za-z]+$", properties.getFieldOfStudyRegex());

        properties.setNgoUserProfileFieldWeight(2.5d);
        assertEquals(2.5d, properties.getNgoUserProfileFieldWeight());

        properties.setYearRegex("^[0-9]{4}$");
        assertEquals("^[0-9]{4}$", properties.getYearRegex());

        properties.setUuidRegex("^[a-f0-9-]{36}$");
        assertEquals("^[a-f0-9-]{36}$", properties.getUuidRegex());

        properties.setAchievementJwtSecretKey("secret-key");
        assertEquals("secret-key", properties.getAchievementJwtSecretKey());
    }

    @Test
    void testCustomListGetters() throws Exception {
        setField("masterDataAllowedSortByFields", "name,createdDate,updatedDate");
        List<String> sortByFields = properties.getMasterDataAllowedSortByFields();
        assertEquals(3, sortByFields.size());
        assertEquals("name", sortByFields.get(0));

        setField("masterDataAllowedType", "degree,institute");
        List<String> allowedTypes = properties.getMasterDataAllowedType();
        assertEquals(2, allowedTypes.size());
        assertEquals("degree", allowedTypes.get(0));
        assertEquals("institute", allowedTypes.get(1));

        setField("ngoUserProfileCompletionRequiredFields", "ngoField1,ngoField2,ngoField3");
        List<String> ngoFields = properties.getNgoUserProfileCompletionRequiredFields();
        assertEquals(3, ngoFields.size());
        assertEquals("ngoField3", ngoFields.get(2));
    }
}

