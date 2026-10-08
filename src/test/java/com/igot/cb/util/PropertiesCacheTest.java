package com.igot.cb.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.util.Properties;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.test.util.ReflectionTestUtils;

@RunWith(MockitoJUnitRunner.class)
public class PropertiesCacheTest {

    private PropertiesCache propertiesCache;
    private Properties testConfigProp;

    @Before
    public void setUp() {
        // Get the singleton instance
        propertiesCache = PropertiesCache.getInstance();

        // Create and inject test properties
        testConfigProp = new Properties();
        testConfigProp.setProperty("test.key", "property.value");
        testConfigProp.setProperty("another.key", "another.value");
        ReflectionTestUtils.setField(propertiesCache, "configProp", testConfigProp);
    }

    @Test
    public void testReadProperty_PropertyExists() {
        // Act - this will return environment variable if it exists, otherwise the property value
        String result = propertiesCache.readProperty("test.key");

        // Assert - The test passes if we get a non-null value
        // (either the property value or an environment variable if it happens to exist)
        assertEquals(testConfigProp.getProperty("test.key"), result);
    }

    @Test
    public void testReadProperty_NonExistentKey() {
        // Arrange - make sure we test a key that likely doesn't exist as env var
        String randomKey = "non.existent.key." + System.currentTimeMillis();

        // Act
        String result = propertiesCache.readProperty(randomKey);

        // Assert
        assertNull(result);
    }

    @Test
    public void testGetProperty_PropertyExists() {
        // This tests the getProperty method which returns the key as default
        String result = propertiesCache.getProperty("another.key");
        assertEquals(testConfigProp.getProperty("another.key"), result);
    }

    @Test
    public void testGetProperty_NonExistentKey() {
        // For non-existent keys, getProperty returns the key itself
        String randomKey = "random.key." + System.currentTimeMillis();
        String result = propertiesCache.getProperty(randomKey);
        assertEquals(randomKey, result);
    }

    @Test
    public void testGetProperty_AllBranches() {
        // Create a custom properties instance for this test
        PropertiesCache instance = PropertiesCache.getInstance();
        Properties customProps = new Properties();
        customProps.setProperty("existing.key", "property.value");
        ReflectionTestUtils.setField(instance, "configProp", customProps);

        // Test case 1: Key exists in properties
        String result1 = instance.getProperty("existing.key");
        // This will either be the property value or an env var if it exists with that name
        // But the key definitely exists in properties, so it will never return null or the key itself
        assertEquals("property.value", result1); // Self-equality to handle env var possibility

        // Test case 2: Key doesn't exist in properties - should return the key itself
        String nonExistentKey = "non.existent.key." + System.currentTimeMillis();
        String result2 = instance.getProperty(nonExistentKey);
        assertEquals(nonExistentKey, result2);

        // Test case 3: Try a key that's unlikely to be an env var but exists in properties
        String uniqueKey = "unique.test.key." + System.currentTimeMillis();
        customProps.setProperty(uniqueKey, "unique.value");
        String result3 = instance.getProperty(uniqueKey);
        assertEquals("unique.value", result3);
    }

    @Test
    public void testGetProperty_EnvVarTakesPrecedence() {
        // NUMBER_OF_PROCESSORS is a standard, reliably non-blank Windows environment variable.
        // This exercises the branch where System.getenv(key) is not blank and is returned directly.
        String envKey = "NUMBER_OF_PROCESSORS";
        String envValue = System.getenv(envKey);
        org.junit.Assume.assumeTrue("Environment variable not present, skipping", StringUtilsNotBlank(envValue));

        String result = propertiesCache.getProperty(envKey);
        assertEquals(envValue, result);
    }

    @Test
    public void testReadProperty_EnvVarTakesPrecedence() {
        String envKey = "NUMBER_OF_PROCESSORS";
        String envValue = System.getenv(envKey);
        org.junit.Assume.assumeTrue("Environment variable not present, skipping", StringUtilsNotBlank(envValue));

        String result = propertiesCache.readProperty(envKey);
        assertEquals(envValue, result);
    }

    private static boolean StringUtilsNotBlank(String value) {
        return value != null && !value.trim().isEmpty();
    }

    @Test
    public void testGetInstance_ReturnsSameSingletonInstance() {
        PropertiesCache instance1 = PropertiesCache.getInstance();
        PropertiesCache instance2 = PropertiesCache.getInstance();
        assertEquals(instance1, instance2);
    }
}