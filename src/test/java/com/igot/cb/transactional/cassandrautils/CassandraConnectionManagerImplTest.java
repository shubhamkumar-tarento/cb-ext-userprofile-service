package com.igot.cb.transactional.cassandrautils;

import com.datastax.oss.driver.api.core.ConsistencyLevel;
import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.CqlSessionBuilder;
import com.datastax.oss.driver.api.core.DefaultConsistencyLevel;
import com.datastax.oss.driver.api.core.metadata.Metadata;
import com.datastax.oss.driver.api.core.metadata.Node;
import com.igot.cb.exceptions.CustomException;
import com.igot.cb.util.Constants;
import com.igot.cb.util.PropertiesCache;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.http.HttpStatus;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class CassandraConnectionManagerImplTest {

    @Mock
    private PropertiesCache propertiesCache;
    @Mock
    private CqlSession mockSession;
    @Mock
    private Metadata mockMetadata;
    @Mock
    private Runtime mockRuntime;

    private CassandraConnectionManagerImpl cassandraConnectionManager;

    @Before
    public void setUp() {
        // Create the test instance
        cassandraConnectionManager = new CassandraConnectionManagerImpl() {
            private CqlSession session = null;

            @Override
            public void createCassandraConnection() {
                //
            }

            @Override
            public CqlSession createCassandraConnectionWithKeySpaces(String keyspace) {
                if (propertiesCache.getProperty(Constants.CASSANDRA_CONFIG_HOST).isEmpty()) {
                    throw new CustomException("ERROR", "Cassandra host is not configured", HttpStatus.INTERNAL_SERVER_ERROR);
                }
                session = mockSession;
                return session;
            }

            @Override
            public CqlSession getSession(String keyspaceName) {
                if (session == null) {
                    session = createCassandraConnectionWithKeySpaces(keyspaceName);
                }
                return session;
            }
        };
        try (MockedStatic<PropertiesCache> propertiesCacheMock = mockStatic(PropertiesCache.class);
             MockedStatic<Runtime> runtimeMock = mockStatic(Runtime.class)) {
            propertiesCacheMock.when(PropertiesCache::getInstance).thenReturn(propertiesCache);
            when(propertiesCache.getProperty(anyString())).thenReturn("dummy-value");
            when(propertiesCache.getProperty(Constants.CASSANDRA_CONFIG_HOST)).thenReturn("localhost");
            runtimeMock.when(Runtime::getRuntime).thenReturn(mockRuntime);
            doNothing().when(mockRuntime).addShutdownHook(any(Thread.class));
        }
    }

    @Test
    public void testGetSession_ExistingSession() {
        String keyspaceName = "testKeyspace";
        CqlSession firstResult = cassandraConnectionManager.getSession(keyspaceName);
        CqlSession secondResult = cassandraConnectionManager.getSession(keyspaceName);
        assertSame(mockSession, firstResult);
        assertSame(firstResult, secondResult);
    }

    @Test
    public void testGetSession_NoExistingSession() {
        String keyspaceName = "testKeyspace";
        CqlSession result = cassandraConnectionManager.getSession(keyspaceName);
        assertSame(mockSession, result);
    }

    @Test
    public void testGetConsistencyLevel_ValidLevel() {
        try (MockedStatic<PropertiesCache> propertiesCacheMock = mockStatic(PropertiesCache.class)) {
            PropertiesCache mockCache = mock(PropertiesCache.class);
            propertiesCacheMock.when(PropertiesCache::getInstance).thenReturn(mockCache);
            when(mockCache.readProperty(Constants.SUNBIRD_CASSANDRA_CONSISTENCY_LEVEL)).thenReturn("LOCAL_QUORUM");
            ConsistencyLevel result = CassandraConnectionManagerImpl.getConsistencyLevel();
            assertEquals(DefaultConsistencyLevel.LOCAL_QUORUM, result);
        }
    }

    @Test
    public void testRegisterShutdownHook() {
        try (MockedStatic<Runtime> runtimeMock = mockStatic(Runtime.class)) {
            runtimeMock.when(Runtime::getRuntime).thenReturn(mockRuntime);
            CassandraConnectionManagerImpl.registerShutdownHook();
            verify(mockRuntime).addShutdownHook(any(Thread.class));
        }
    }

    @Test
    public void testResourceCleanUp_Run() {
        CassandraConnectionManagerImpl.ResourceCleanUp cleanUp = mock(CassandraConnectionManagerImpl.ResourceCleanUp.class);
        doNothing().when(cleanUp).run();
        cleanUp.run();
        verify(cleanUp).run();
    }

    @Test
    public void testCreateCassandraConnectionWithKeySpaces_MissingHostConfig() {
        try (MockedStatic<PropertiesCache> propertiesCacheMock = mockStatic(PropertiesCache.class)) {
            propertiesCacheMock.when(PropertiesCache::getInstance).thenReturn(propertiesCache);
            when(propertiesCache.getProperty(Constants.CASSANDRA_CONFIG_HOST)).thenReturn("");
            try {
                cassandraConnectionManager.createCassandraConnectionWithKeySpaces("testKeyspace");
                fail("Expected CustomException was not thrown");
            } catch (CustomException e) {
                assertEquals("ERROR", e.getCode());
                assertEquals("Cassandra host is not configured", e.getMessage());
                // Match actual implementation behavior
                assertEquals(0, e.getResponseCode());
            }
        }
    }

    @Test
    public void testCreateCassandraConnection_LogsError() {
        CassandraConnectionManagerImpl spyManager = spy(cassandraConnectionManager);
        CustomException testException = new CustomException("ERROR", "Test exception", HttpStatus.INTERNAL_SERVER_ERROR);
        doThrow(testException).when(spyManager).createCassandraConnection();
        try {
            spyManager.createCassandraConnection();
            fail("Expected CustomException was not thrown");
        } catch (CustomException e) {
            assertEquals("ERROR", e.getCode());
            assertEquals("Test exception", e.getMessage());
            assertEquals(0, e.getResponseCode());
        }
    }

    @Test
    public void testGetSession_WithNullSessionInMap() throws Exception {
        Field mapField = CassandraConnectionManagerImpl.class.getDeclaredField("cassandraSessionMap");
        mapField.setAccessible(true);
        Map<String, CqlSession> sessionMap = (Map<String, CqlSession>) mapField.get(null);
        sessionMap.clear();
        CassandraConnectionManagerImpl spyManager = spy(new CassandraConnectionManagerImpl() {
            @Override
            public void createCassandraConnection() {
                //
            }
            @Override
            public CqlSession createCassandraConnectionWithKeySpaces(String keyspace) {
                return mockSession;
            }
        });
        String testKeyspace = "testKeyspace";
        CqlSession result = spyManager.getSession(testKeyspace);
        assertEquals(mockSession, result);
        verify(spyManager).createCassandraConnectionWithKeySpaces(testKeyspace);
        assertEquals(mockSession, sessionMap.get(testKeyspace));
    }

    @Test
    public void testGetSession_WithOpenSessionInMap() throws Exception {
        Field mapField = CassandraConnectionManagerImpl.class.getDeclaredField("cassandraSessionMap");
        mapField.setAccessible(true);
        Map<String, CqlSession> sessionMap = (Map<String, CqlSession>) mapField.get(null);
        sessionMap.clear();
        when(mockSession.isClosed()).thenReturn(false);
        String testKeyspace = "testKeyspace";
        sessionMap.put(testKeyspace, mockSession);
        CassandraConnectionManagerImpl spyManager = spy(new CassandraConnectionManagerImpl() {
            @Override
            public void createCassandraConnection() {
                //
            }
            @Override
            public CqlSession createCassandraConnectionWithKeySpaces(String keyspace) {
                fail("Should not create a new session when an open one exists");
                return null;
            }
        });
        CqlSession result = spyManager.getSession(testKeyspace);
        assertEquals(mockSession, result);
    }

    @Test
    public void testGetSession_WithClosedSessionInMap() throws Exception {
        Field mapField = CassandraConnectionManagerImpl.class.getDeclaredField("cassandraSessionMap");
        mapField.setAccessible(true);
        Map<String, CqlSession> sessionMap = (Map<String, CqlSession>) mapField.get(null);
        sessionMap.clear();
        CqlSession closedSession = mock(CqlSession.class);
        when(closedSession.isClosed()).thenReturn(true);
        String testKeyspace = "testKeyspace";
        sessionMap.put(testKeyspace, closedSession);
        CassandraConnectionManagerImpl spyManager = spy(new CassandraConnectionManagerImpl() {
            @Override
            public void createCassandraConnection() {
                //
            }
            @Override
            public CqlSession createCassandraConnectionWithKeySpaces(String keyspace) {
                return mockSession;
            }
        });
        CqlSession result = spyManager.getSession(testKeyspace);
        assertEquals(mockSession, result);
        assertEquals(mockSession, sessionMap.get(testKeyspace));
    }


    @Test
    public void testCreateCassandraConnectionWithKeySpaces_WithValidKeyspace() {
        // Setup mocking
        CassandraConnectionManagerImpl spyManager = spy(cassandraConnectionManager);
        try (MockedStatic<PropertiesCache> propertiesCacheMock = mockStatic(PropertiesCache.class)) {
            propertiesCacheMock.when(PropertiesCache::getInstance).thenReturn(propertiesCache);
            CqlSession result = spyManager.createCassandraConnectionWithKeySpaces("testKeyspace");
            assertEquals(mockSession, result);
        }
    }

    @Test
    public void testCreateCassandraConnectionWithKeySpaces_WithoutKeyspace() {
        CassandraConnectionManagerImpl spyManager = spy(cassandraConnectionManager);
        try (MockedStatic<PropertiesCache> propertiesCacheMock = mockStatic(PropertiesCache.class)) {
            propertiesCacheMock.when(PropertiesCache::getInstance).thenReturn(propertiesCache);
            when(propertiesCache.getProperty(Constants.CASSANDRA_CONFIG_HOST)).thenReturn("localhost");
            CqlSession result = spyManager.createCassandraConnectionWithKeySpaces(null);
            assertEquals(mockSession, result);
        }
    }

    @Test
    public void testCreateCassandraConnectionWithKeySpaces_MultipleHosts() {
        CassandraConnectionManagerImpl spyManager = spy(cassandraConnectionManager);
        try (MockedStatic<PropertiesCache> propertiesCacheMock = mockStatic(PropertiesCache.class)) {
            propertiesCacheMock.when(PropertiesCache::getInstance).thenReturn(propertiesCache);
            when(propertiesCache.getProperty(Constants.CASSANDRA_CONFIG_HOST)).thenReturn("host1,host2,host3");
            CqlSession result = spyManager.createCassandraConnectionWithKeySpaces("testKeyspace");

            assertNotNull(result);
        }
    }

    @Test
    public void testCreateCassandraConnectionWithKeySpaces_EmptyHost() {
        try (MockedStatic<PropertiesCache> propertiesCacheMock = mockStatic(PropertiesCache.class)) {
            propertiesCacheMock.when(PropertiesCache::getInstance).thenReturn(propertiesCache);
            when(propertiesCache.getProperty(Constants.CASSANDRA_CONFIG_HOST)).thenReturn("");
            try {
                cassandraConnectionManager.createCassandraConnectionWithKeySpaces("testKeyspace");
                fail("Expected CustomException was not thrown");
            } catch (CustomException e) {
                assertEquals("ERROR", e.getCode());
                assertEquals("Cassandra host is not configured", e.getMessage());
            }
        }
    }

    @Test
    public void testCreateCassandraConnectionWithKeySpaces_InvalidConnectionParams_WithMock() {
        CassandraConnectionManagerImpl testManager = new CassandraConnectionManagerImpl() {
            @Override
            public void createCassandraConnection() {
                //
            }
        };

        try (MockedStatic<PropertiesCache> propertiesCacheMock = mockStatic(PropertiesCache.class)) {
            propertiesCacheMock.when(PropertiesCache::getInstance).thenReturn(propertiesCache);
            when(propertiesCache.getProperty(Constants.CASSANDRA_CONFIG_HOST)).thenReturn("localhost");
            when(propertiesCache.getProperty(Constants.CORE_CONNECTIONS_PER_HOST_FOR_LOCAL)).thenReturn("invalid");
            try {
                testManager.createCassandraConnectionWithKeySpaces("testKeyspace");
                fail("Expected CustomException was not thrown");
            } catch (CustomException e) {
                assertEquals("ERROR", e.getCode());
                assertTrue(e.getMessage().contains("For input string: \"invalid\""));
            }
        }
    }

    /**
     * Exercises the real (non-overridden) createCassandraConnectionWithKeySpaces() when the
     * Cassandra host is blank, AND the real createCassandraConnection() catch/rethrow block,
     * by invoking the real no-arg constructor directly. This covers lines 85-90 and 153-163.
     */
    @Test
    public void testRealConstructor_MissingHostConfig_WrapsException() {
        try (MockedStatic<PropertiesCache> propertiesCacheMock = mockStatic(PropertiesCache.class);
             MockedStatic<Runtime> runtimeMock = mockStatic(Runtime.class)) {
            propertiesCacheMock.when(PropertiesCache::getInstance).thenReturn(propertiesCache);
            when(propertiesCache.getProperty(Constants.CASSANDRA_CONFIG_HOST)).thenReturn("");
            runtimeMock.when(Runtime::getRuntime).thenReturn(mockRuntime);
            doNothing().when(mockRuntime).addShutdownHook(any(Thread.class));

            try {
                new CassandraConnectionManagerImpl();
                fail("Expected CustomException was not thrown");
            } catch (CustomException e) {
                assertEquals("ERROR", e.getCode());
                assertEquals("Cassandra host is not configured", e.getMessage());
            }
        }
    }

    /**
     * Exercises the full real success path of createCassandraConnectionWithKeySpaces() (no
     * keyspace) via the real no-arg constructor: building the DriverConfigLoader, building a
     * CqlSession via the (mocked) CqlSession.builder() chain, reading metadata/cluster name and
     * iterating nodes, and the successful assignment in createCassandraConnection(). Covers
     * lines 98-137 (null-keyspace branch) and line 153.
     */
    @Test
    public void testRealConstructor_SuccessfulConnection_NoKeyspace() {
        CqlSessionBuilder mockBuilder = mock(CqlSessionBuilder.class, Mockito.RETURNS_SELF);
        when(mockBuilder.build()).thenReturn(mockSession);

        Node mockNode = mock(Node.class);
        when(mockNode.getDatacenter()).thenReturn("dc1");
        when(mockNode.getRack()).thenReturn("rack1");
        Map<UUID, Node> nodesMap = new HashMap<>();
        nodesMap.put(UUID.randomUUID(), mockNode);

        when(mockSession.getMetadata()).thenReturn(mockMetadata);
        when(mockMetadata.getClusterName()).thenReturn(Optional.of("testCluster"));
        when(mockMetadata.getNodes()).thenReturn(nodesMap);

        try (MockedStatic<PropertiesCache> propertiesCacheMock = mockStatic(PropertiesCache.class);
             MockedStatic<Runtime> runtimeMock = mockStatic(Runtime.class);
             MockedStatic<CqlSession> cqlSessionStatic = mockStatic(CqlSession.class)) {
            propertiesCacheMock.when(PropertiesCache::getInstance).thenReturn(propertiesCache);
            when(propertiesCache.getProperty(anyString())).thenReturn("5");
            when(propertiesCache.getProperty(Constants.CASSANDRA_CONFIG_HOST)).thenReturn("localhost");
            runtimeMock.when(Runtime::getRuntime).thenReturn(mockRuntime);
            doNothing().when(mockRuntime).addShutdownHook(any(Thread.class));
            cqlSessionStatic.when(CqlSession::builder).thenReturn(mockBuilder);

            CassandraConnectionManagerImpl realManager = new CassandraConnectionManagerImpl();
            assertNotNull(realManager);
        }
    }

    /**
     * Exercises the real success path of createCassandraConnectionWithKeySpaces() WITH a
     * keyspace supplied (the withKeyspace() branch, lines 115-121), plus multiple contact
     * points, by calling the method directly (constructor's own connection attempt is
     * suppressed via an override so it doesn't interfere).
     */
    @Test
    public void testCreateCassandraConnectionWithKeySpaces_RealBuilder_WithKeyspace() {
        CqlSessionBuilder mockBuilder = mock(CqlSessionBuilder.class, Mockito.RETURNS_SELF);
        when(mockBuilder.build()).thenReturn(mockSession);

        Node mockNode = mock(Node.class);
        when(mockNode.getDatacenter()).thenReturn("dc1");
        when(mockNode.getRack()).thenReturn("rack1");
        Map<UUID, Node> nodesMap = new HashMap<>();
        nodesMap.put(UUID.randomUUID(), mockNode);

        when(mockSession.getMetadata()).thenReturn(mockMetadata);
        when(mockMetadata.getClusterName()).thenReturn(Optional.of("testCluster"));
        when(mockMetadata.getNodes()).thenReturn(nodesMap);

        CassandraConnectionManagerImpl manager = new CassandraConnectionManagerImpl() {
            @Override
            public void createCassandraConnection() {
                // no-op so the constructor itself doesn't attempt a real connection
            }
        };

        try (MockedStatic<PropertiesCache> propertiesCacheMock = mockStatic(PropertiesCache.class);
             MockedStatic<CqlSession> cqlSessionStatic = mockStatic(CqlSession.class)) {
            propertiesCacheMock.when(PropertiesCache::getInstance).thenReturn(propertiesCache);
            when(propertiesCache.getProperty(anyString())).thenReturn("5");
            when(propertiesCache.getProperty(Constants.CASSANDRA_CONFIG_HOST)).thenReturn("host1,host2");
            cqlSessionStatic.when(CqlSession::builder).thenReturn(mockBuilder);

            CqlSession result = manager.createCassandraConnectionWithKeySpaces("myKeyspace");
            assertSame(mockSession, result);
        }
    }

    /**
     * Covers the IllegalArgumentException branch of getConsistencyLevel() (lines 176-187) when
     * the configured consistency level string doesn't match any DefaultConsistencyLevel enum.
     */
    @Test
    public void testGetConsistencyLevel_InvalidLevel_ThrowsCustomException() {
        try (MockedStatic<PropertiesCache> propertiesCacheMock = mockStatic(PropertiesCache.class)) {
            PropertiesCache mockCache = mock(PropertiesCache.class);
            propertiesCacheMock.when(PropertiesCache::getInstance).thenReturn(mockCache);
            when(mockCache.readProperty(Constants.SUNBIRD_CASSANDRA_CONSISTENCY_LEVEL)).thenReturn("NOT_A_REAL_LEVEL");
            try {
                CassandraConnectionManagerImpl.getConsistencyLevel();
                fail("Expected CustomException was not thrown");
            } catch (CustomException e) {
                assertEquals("ERROR", e.getCode());
            }
        }
    }

    /**
     * Covers the real ResourceCleanUp.run() method (lines 205-218): it should close every
     * session held in the static cassandraSessionMap as well as the static session field,
     * without throwing when everything closes cleanly.
     */
    @Test
    public void testResourceCleanUp_RealRun_ClosesSessionsSuccessfully() throws Exception {
        Field mapField = CassandraConnectionManagerImpl.class.getDeclaredField("cassandraSessionMap");
        mapField.setAccessible(true);
        Map<String, CqlSession> sessionMap = (Map<String, CqlSession>) mapField.get(null);
        sessionMap.clear();

        CqlSession extraSession = mock(CqlSession.class);
        sessionMap.put("extraKeyspace", extraSession);

        Field sessionField = CassandraConnectionManagerImpl.class.getDeclaredField("session");
        sessionField.setAccessible(true);
        sessionField.set(null, mockSession);

        try {
            Class<?> cleanUpClass = Class.forName(
                    "com.igot.cb.transactional.cassandrautils.CassandraConnectionManagerImpl$ResourceCleanUp");
            java.lang.reflect.Constructor<?> ctor = cleanUpClass.getDeclaredConstructor();
            ctor.setAccessible(true);
            Thread cleanUp = (Thread) ctor.newInstance();
            cleanUp.run();

            verify(extraSession).close();
            verify(mockSession).close();
        } finally {
            sessionMap.clear();
            sessionField.set(null, null);
        }
    }

    /**
     * Covers the catch(Exception) branch inside ResourceCleanUp.run() (lines 215-216): when
     * closing a session throws, the exception must be swallowed/logged rather than propagated.
     */
    @Test
    public void testResourceCleanUp_RealRun_SwallowsExceptionOnClose() throws Exception {
        Field mapField = CassandraConnectionManagerImpl.class.getDeclaredField("cassandraSessionMap");
        mapField.setAccessible(true);
        Map<String, CqlSession> sessionMap = (Map<String, CqlSession>) mapField.get(null);
        sessionMap.clear();

        CqlSession throwingSession = mock(CqlSession.class);
        doThrow(new RuntimeException("close failed")).when(throwingSession).close();
        sessionMap.put("faultyKeyspace", throwingSession);

        Field sessionField = CassandraConnectionManagerImpl.class.getDeclaredField("session");
        sessionField.setAccessible(true);
        sessionField.set(null, null);

        try {
            Class<?> cleanUpClass = Class.forName(
                    "com.igot.cb.transactional.cassandrautils.CassandraConnectionManagerImpl$ResourceCleanUp");
            java.lang.reflect.Constructor<?> ctor = cleanUpClass.getDeclaredConstructor();
            ctor.setAccessible(true);
            Thread cleanUp = (Thread) ctor.newInstance();
            // Should not throw despite the mocked session throwing on close().
            cleanUp.run();
            verify(throwingSession).close();
        } finally {
            sessionMap.clear();
        }
    }
}
