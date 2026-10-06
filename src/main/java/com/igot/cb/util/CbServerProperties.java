package com.igot.cb.util;

import lombok.Getter;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

@Component
@Getter
@Setter
public class CbServerProperties {

    @Value("${redis.insights.index}")
    private int redisInsightIndex;

    @Value("${search.result.redis.ttl}")
    private long searchResultRedisTtl;

    @Value("${sb.api.key}")
    private String sbApiKey;

    @Value("${http.client.request.factory.timeout}")
    private int requestTimeoutMs;

    @Value("${http.pooling.client.cm.max.total.connections}")
    private int maxTotalConnections;

    @Value("${http.pooling.client.cm.default.max.per.route}")
    private int maxConnectionsPerRoute;

    @Value("${redis.pool.max.total}")
    private int redisPoolMaxTotal;

    @Value("${redis.pool.max.idle}")
    private int redisPoolMaxIdle;

    @Value("${redis.pool.min.idle}")
    private int redisPoolMinIdle;

    @Value("${redis.pool.max.wait}")
    private int redisPoolMaxWait;

    @Value("${redis.connection.timeout}")
    private long redisConnectionTimeout;

    @Value("${validation.required-fields.educationalQualification}")
    private String educationalQualificationMandatoryFields;

    @Value("${validation.required-fields.serviceHistory}")
    private String serviceHistoryMandatoryFields;

    @Value("${validation.required-fields.achievement}")
    private String achievementsMandatoryFields;

    @Value("${context.types}")
    private String[] contextType;

    @Value("${basic.profile.fields}")
    private String basicProfileFields;

    @Value("${profile.completion.required.fields}")
    private String profileCompletionRequiredFields;

    @Value("${profile.completion.extended.fields}")
    private List<String> extendedFieldsConfig;

    @Value("${profile.completion.field.weight}")
    private double fieldWeight;

    @Value("${community.base.url}")
    private String communityBaseUrl;

    @Value("${community.post.count.api.url}")
    private String communityPostCountApiUrl;

    @Value("${redis.timeout}")
    private String redisTimeout;

    @Value("${redis.host.name}")
    private String redisHostName;

    @Value("${redis.port}")
    private String redisPort;

    @Value("${redis.data.host.name}")
    private String redisDataHostName;

    @Value("${redis.data.port}")
    private String redisDataPort;

    @Value("${cb.redis.maxIdle}")
    private Integer redisMaxIdle;

    @Value("${cb.redis.maxTotal}")
    private Integer redisMaxTotal;

    @Value("${cb.redis.minIdle}")
    private Integer redisMinIdle;

    @Value("${cb.redis.testOnBorrow}")
    private Boolean redisTestOnBorrow;

    @Value("${cb.redis.testOnReturn}")
    private Boolean redisTestOnReturn;

    @Value("${cb.redis.testWhileIdle}")
    private Boolean redisTestWhileIdle;

    @Value("${cb.redis.minEvictableIdleTimeMillis}")
    private Long redisMinEvictableIdleTimeMillis;

    @Value("${cb.redis.timeBetweenEvictionRunsMillis}")
    private Long redisTimeBetweenEvictionRunsMillis;

    @Value("${cb.redis.numTestsPerEvictionRun}")
    private Integer redisNumTestsPerEvictionRun;

    @Value("${cb.redis.blockWhenExhausted}")
    private Boolean redisBlockWhenExhausted;

    @Value("${cb.data.index}")
    private int dataIndex;

    @Value("${cb.cache.ttl}")
    private int cacheTtl;

    @Value("${cb.certificate.count.redis.key}")
    private String certificateCountRedisKey;

    @Value("${cb.certificate.count.redis.ttl}")
    private int certificateCountRedisTtl;

    @Value("${cb.badge.count.redis.ttl}")
    private int badgeCountRedisTtl;

    public int getCertificateCountRedisTtl() {
        return certificateCountRedisTtl;
    }

    public void setCertificateCountRedisTtl(int certificateCountRedisTtl) {
        this.certificateCountRedisTtl = certificateCountRedisTtl;
    }

    public List<String> getBasicProfileFields() {
        return Arrays.asList(basicProfileFields.split(","));
    }

    public List<String> getProfileCompletionRequiredFields() {
        return Arrays.asList(profileCompletionRequiredFields.split(","));
    }

    @Value("${user.profile.index}")
    public String userProfileIndex;

    @Value("${hub.graph.service}")
    public String hubGraphService;

    @Value("${connection.api}")
    public String connectionApi;

    @Value("${db.table.user-enrolments}")
    private String userEnrolmentsTable;

    @Value("${master.data.search.string.regex}")
    private String masterDataSearchStringRegex;

    @Value("${es.degree.index.name}")
    private String esDegreeIndexName;

    @Value("${es.institute.index.name}")
    private String esInstituteIndexName;

    @Value("${es.masterdata.index.doc.type}")
    private String esMasterDataIndexDocType;

    @Value("${master.data.allowed.type}")
    private String masterDataAllowedType;

    @Value("${master.data.search.string.min.length}")
    private String masterDataSearchStringMinLength;

    @Value("${master.data.search.string.max.length}")
    private String masterDataSearchStringMaxLength;

    @Value("${learner.service.host}")
    private String learnerServiceHost;

    @Value("${password.reset.path}")
    private String passwordResetPath;

    @Value("${master.data.search.allowed.sortby.fields}")
    private String masterDataAllowedSortByFields;

    @Value("${achievement.status.update.required.fields}")
    private String requiredFieldsProperty;

    @Value("${elastic.required.field.achievement.json.path}")
    private String achievementEsRequiredFieldsMappingPath;

    @Value("${cassandra.fetch.limit}")
    private int cassandraFetchLimit;

    public List<String> getMasterDataAllowedSortByFields() {return Arrays.asList(masterDataAllowedSortByFields.split(","));}

    public List<String> getMasterDataAllowedType() {
        return Arrays.asList(masterDataAllowedType.split(","));
    }

    @Value("${achievement.require-es}")
    private boolean requireEs;

    @Value("${user.competency.mapping.event}")
    public String userCompetencyTopicName;

    @Value("${validation.allowed-fields.achievement}")
    private String achievementsAllowedFields;

    @Value("${achievement.bulk.list.context.data.fields:}")
    private String bulkListContextDataFields;

    @Value("${achievement.bulk.list.response.fields:}")
    private String bulkListResponseFields;

    @Value("${achievement.cache.ttl}")
    private int achievementCacheTtl;

    @Value("${validation.regex.degree}")
    private String degreeNameRegex;

    @Value("${validation.regex.institute}")
    private String instituteNameRegex;

    @Value("${validation.regex.fieldOfStudy}")
    private String fieldOfStudyRegex;
  
    @Value("${ngo.user.profile.completion.required.fields}")
    private String ngoUserProfileCompletionRequiredFields;

    public List<String> getNgoUserProfileCompletionRequiredFields() {
        return Arrays.asList(ngoUserProfileCompletionRequiredFields.split(","));
    }

    @Value("${ngo.user.profile.completion.field.weight}")
    private double ngoUserProfileFieldWeight;

    @Value("${validation.regex.year}")
    private String yearRegex;

    @Value("${validation.regex.uuid}")
    private String uuidRegex;

    @Value("${achievement.jwt.secret.key}")
    private String achievementJwtSecretKey;

}