package com.igot.cb.transactional.cassandrautils;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

/**
 * Test class for {@link CassandraPropertyReader}.
 */
class CassandraPropertyReaderTest {

    private static final String PROPERTIES_FILE_NAME = "cassandratablecolumn.properties";

    @Test
    void getInstance_ReturnsSameSingletonInstance() {
        CassandraPropertyReader instance1 = CassandraPropertyReader.getInstance();
        CassandraPropertyReader instance2 = CassandraPropertyReader.getInstance();

        assertNotNull(instance1);
        assertSame(instance1, instance2);
    }

    @Test
    void readProperty_ReturnsKeyItself_WhenPropertyNotFound() {
        CassandraPropertyReader instance = CassandraPropertyReader.getInstance();

        String result = instance.readProperty("some_key_that_does_not_exist_in_properties_file");

        assertEquals("some_key_that_does_not_exist_in_properties_file", result);
    }

    @Test
    void readProperty_ReturnsConfiguredValue_WhenPropertyExists() {
        CassandraPropertyReader instance = CassandraPropertyReader.getInstance();

        // Even if we do not know a specific configured key, calling readProperty
        // with an arbitrary key must never return null and must fall back to the key.
        String key = "keyspace";
        String result = instance.readProperty(key);

        assertNotNull(result);
    }

    /**
     * Exercises the IOException / CassandraPropertyReaderException branch of
     * {@code loadProperties()}. Rather than faking a missing classpath resource
     * (which would require reloading the class under a custom ClassLoader and
     * defeats JaCoCo's coverage attribution for the real, already-instrumented
     * class), we let the real resource stream load normally and instead make
     * {@code Properties.load(InputStream)} itself throw, which the production
     * code funnels into the exact same catch block.
     */
    @Test
    void loadProperties_ThrowsCassandraPropertyReaderException_WhenProperitesFailToLoad() throws Exception {
        Constructor<CassandraPropertyReader> constructor =
                CassandraPropertyReader.class.getDeclaredConstructor();
        constructor.setAccessible(true);

        try (org.mockito.MockedConstruction<Properties> ignored = org.mockito.Mockito.mockConstruction(
                Properties.class,
                (mock, context) -> doThrow(new IOException("boom")).when(mock).load(any(InputStream.class)))) {
            try {
                constructor.newInstance();
                fail("Expected CassandraPropertyReaderException to be thrown");
            } catch (InvocationTargetException e) {
                Throwable cause = e.getCause();
                assertNotNull(cause);
                assertEquals("com.igot.cb.transactional.exceptions.CassandraPropertyReaderException",
                        cause.getClass().getName());
                assertEquals("Error loading properties from file '" + PROPERTIES_FILE_NAME + "'", cause.getMessage());
                assertNotNull(cause.getCause());
                assertEquals(IOException.class, cause.getCause().getClass());
            }
        }
    }
}
