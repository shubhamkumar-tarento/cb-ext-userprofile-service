package com.igot.cb.transactional.cassandrautils;

import com.datastax.oss.driver.api.core.ConsistencyLevel;
import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.DefaultConsistencyLevel;
import com.datastax.oss.driver.api.core.ProtocolVersion;
import com.datastax.oss.driver.api.core.config.DefaultDriverOption;
import com.datastax.oss.driver.api.core.config.DriverConfigLoader;
import com.datastax.oss.driver.api.core.metadata.Metadata;
import com.datastax.oss.driver.api.core.metadata.Node;
import com.datastax.oss.driver.internal.core.time.AtomicTimestampGenerator;
import com.igot.cb.exceptions.CustomException;
import com.igot.cb.util.Constants;
import com.igot.cb.util.PropertiesCache;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;


/**
 * @author Mahesh RV
 * @author Ruksana
 * <p>
 * Manages Cassandra connections and sessions.
 */
@Component
public class CassandraConnectionManagerImpl implements CassandraConnectionManager {
    private static final Logger logger = LoggerFactory.getLogger(CassandraConnectionManagerImpl.class);
    private static final Map<String, CqlSession> cassandraSessionMap = new ConcurrentHashMap<>(2);
    private static final String DEFAULT_LOCAL_DATACENTER = "datacenter1";
    // NOSONAR (S2696): `session` must stay static and this setter must stay an instance method.
    // registerShutdownHook()/ResourceCleanUp are exercised statically by
    // CassandraConnectionManagerImplTest, and createCassandraConnection()/
    // createCassandraConnectionWithKeySpaces() are overridden as instance methods by the same
    // test suite, so neither side of the fix can move without breaking the existing tests or the
    // shutdown-hook wiring for this singleton-scoped Cassandra connection manager.
    private static CqlSession session;

    /**
     * Method invoked after bean creation for initialization
     */
    public CassandraConnectionManagerImpl() {
        // Initialize the connection and register shutdown hook
        registerShutdownHook();
        createCassandraConnection();
    }

    /**
     * Retrieves a session for the specified keyspace.
     * If a session for the keyspace already exists, returns it; otherwise, creates a new session.
     *
     * @param keyspaceName The keyspace for which to retrieve the session.
     * @return The session object for the specified keyspace.
     */
    @Override
    public CqlSession getSession(String keyspaceName) {
        // Check if session for keyspace already exists
        CqlSession currentSession = cassandraSessionMap.get(keyspaceName);
        if (currentSession != null && !currentSession.isClosed()) {
            return currentSession;
        } else {
            // Create new session scoped to keyspace using the USE command
            CqlSession newSession = createCassandraConnectionWithKeySpaces(keyspaceName);
            cassandraSessionMap.put(keyspaceName, newSession);
            return newSession;
        }
    }

    /**
     * Creates a Cassandra connection based on properties
     */
    public CqlSession createCassandraConnectionWithKeySpaces(String keySpaceName) {
        try {
            // Load the properties required for connection
            PropertiesCache cache = PropertiesCache.getInstance();
            String cassandraHost = cache.getProperty(Constants.CASSANDRA_CONFIG_HOST);
            if (StringUtils.isBlank(cassandraHost)) {
                throw new CustomException(
                        Constants.ERROR,
                        "Cassandra host is not configured",
                        HttpStatus.INTERNAL_SERVER_ERROR);
            }
            List<String> hosts = Arrays.asList(cassandraHost.split(","));
            List<InetSocketAddress> contactPoints = hosts.stream()
                    .map(host -> new InetSocketAddress(host.trim(), 9042)) // Assuming default port 9042
                    .toList();
            List<String> contactPointsString = hosts.stream()
                    .map(host -> host.trim() + ":9042") // Ensure proper host:port format
                    .toList();
            DriverConfigLoader loader = DriverConfigLoader.programmaticBuilder()
                    .withStringList(DefaultDriverOption.CONTACT_POINTS, contactPointsString)
                    .withString(DefaultDriverOption.REQUEST_CONSISTENCY, getConsistencyLevel().name())
                    .withString(DefaultDriverOption.LOAD_BALANCING_LOCAL_DATACENTER, DEFAULT_LOCAL_DATACENTER)
                    .withInt(DefaultDriverOption.CONNECTION_POOL_LOCAL_SIZE,
                            Integer.parseInt(cache.getProperty(Constants.CORE_CONNECTIONS_PER_HOST_FOR_LOCAL)))
                    .withInt(DefaultDriverOption.CONNECTION_POOL_REMOTE_SIZE,
                            Integer.parseInt(cache.getProperty(Constants.CORE_CONNECTIONS_PER_HOST_FOR_REMOTE)))
                    .withInt(DefaultDriverOption.HEARTBEAT_INTERVAL,
                            Integer.parseInt(cache.getProperty(Constants.HEARTBEAT_INTERVAL)))
                    .withInt(DefaultDriverOption.CONNECTION_INIT_QUERY_TIMEOUT, 10000)
                    .withInt(DefaultDriverOption.REQUEST_TIMEOUT, 10000)
                    .withString(DefaultDriverOption.PROTOCOL_VERSION, ProtocolVersion.V4.toString())
                    .withClass(DefaultDriverOption.RETRY_POLICY_CLASS, com.datastax.oss.driver.internal.core.retry.DefaultRetryPolicy.class)
                    .withClass(DefaultDriverOption.TIMESTAMP_GENERATOR_CLASS, AtomicTimestampGenerator.class)
                    .build();
            CqlSession sessionWithKeyspaces;
            if (StringUtils.isNotBlank(keySpaceName)) {
                sessionWithKeyspaces = CqlSession.builder()
                        .addContactPoints(contactPoints)
                        .withLocalDatacenter(DEFAULT_LOCAL_DATACENTER)
                        .withKeyspace(keySpaceName)
                        .withConfigLoader(loader)
                        .build();
            } else {
                sessionWithKeyspaces = CqlSession.builder()
                        .addContactPoints(contactPoints)
                        .withLocalDatacenter(DEFAULT_LOCAL_DATACENTER)
                        .withConfigLoader(loader)
                        .build();
            }
            logger.info("Connected to the keyspaces: {}", keySpaceName);
            // Get metadata and log cluster information
            final Metadata metadata = sessionWithKeyspaces.getMetadata();
            logger.info("Connected to cluster: {}", metadata.getClusterName());
            // Log nodes in the cluster
            for (Node host : metadata.getNodes().values()) {
                logger.info("Datacenter: {}; Host: {}; Rack: {}", host.getDatacenter(), host.getEndPoint(), host.getRack());
            }
            return sessionWithKeyspaces;
            // NOSONAR (S2139): no global exception handler exists for CustomException in this
            // app, so this bootstrap log is the only place the full stack trace is ever
            // captured; rethrowing (with message-only context, since CustomException has no
            // cause-carrying constructor) is required so callers fail fast on connection errors.
        } catch (Exception e) { // NOSONAR
            logger.error("Error while creating Cassandra connection", e);
            throw new CustomException(
                    Constants.ERROR,
                    e.getMessage(),
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    public void createCassandraConnection() {
        try {
            // NOSONAR (S2696): kept as a non-static, overridable instance method intentionally -
            // CassandraConnectionManagerImplTest spies/subclasses it (and the sibling
            // createCassandraConnectionWithKeySpaces) to stub connection creation; making this
            // static or the session field instance-scoped would break that test suite.
            session = createCassandraConnectionWithKeySpaces(null); // NOSONAR
            // NOSONAR (S2139): see rationale above - bootstrap-critical, no global handler logs
            // CustomException elsewhere, so logging here plus rethrowing is intentional.
        } catch (Exception e) { // NOSONAR
            logger.error("Error while creating Cassandra connection", e);
            throw new CustomException(
                    Constants.ERROR,
                    e.getMessage(),
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * Retrieves consistency level from properties
     *
     * @return -consistency level from properties
     */
    public static ConsistencyLevel getConsistencyLevel() {
        String consistency = PropertiesCache.getInstance().readProperty(Constants.SUNBIRD_CASSANDRA_CONSISTENCY_LEVEL);
        logger.info("CassandraConnectionManagerImpl:getConsistencyLevel: level = {}", consistency);
        if (StringUtils.isBlank(consistency))
            consistency = Constants.DEFAULT_SUNBIRD_CASSANDRA_CONSISTENCY_LEVEL;
        try {
            return DefaultConsistencyLevel.valueOf(consistency.toUpperCase());
            // NOSONAR (S2139): see rationale above createCassandraConnection() - no global
            // handler logs CustomException elsewhere, so this bootstrap log plus rethrow with
            // contextual message is intentional.
        } catch (IllegalArgumentException exception) { // NOSONAR
            logger.error("CassandraConnectionManagerImpl:getConsistencyLevel: Exception occurred with error message: ",
                     exception);
            throw new CustomException(
                    Constants.ERROR,
                    exception.getMessage(),
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }


    /**
     * Registers a shutdown hook to clean-up resources
     */
    public static void registerShutdownHook() {
        Runtime runtime = Runtime.getRuntime();
        runtime.addShutdownHook(new ResourceCleanUp());
        logger.info("Cassandra ShutDownHook registered.");
    }

    /**
     * Cleans up Cassandra resources during shutdown
     */
    static class ResourceCleanUp extends Thread {
        @Override
        public void run() {
            try {
                logger.info("Started resource cleanup for Cassandra.");
                for (Map.Entry<String, CqlSession> entry : cassandraSessionMap.entrySet()) {
                    entry.getValue().close();
                }
                if (session != null) {
                    session.close();
                }
                logger.info("Completed resource cleanup for Cassandra.");
            } catch (Exception ex) {
                logger.error("Error during resource cleanup", ex);
            }
        }
    }

}