package com.igot.cb.util;

import static org.junit.Assert.*;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.crypto.Cipher;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import com.igot.cb.exceptions.ProjectCommonException;

@RunWith(MockitoJUnitRunner.class)
public class DefaultEncryptionServiceImplTest {

    private DefaultEncryptionServiceImpl encryptionService;

    @Before
    public void setUp() {
        encryptionService = new DefaultEncryptionServiceImpl();
    }

    @Test
    public void testStaticInitialization() throws Exception {
        // Verify static fields initialization using reflection
        Field encryptionKeyField = DefaultEncryptionServiceImpl.class.getDeclaredField("encryptionKey");
        encryptionKeyField.setAccessible(true);
        String encryptionKey = (String) encryptionKeyField.get(null);
        assertNotNull("encryptionKey should be initialized", encryptionKey);

        Field cipherField = DefaultEncryptionServiceImpl.class.getDeclaredField("c");
        cipherField.setAccessible(true);
        Cipher cipher = (Cipher) cipherField.get(null);
        assertNotNull("Cipher should be initialized", cipher);
    }

    @Test
    public void testConstructor() throws Exception {
        // Test with ProjectUtil config fallback
        try (MockedStatic<ProjectUtil> projectUtilMock = Mockito.mockStatic(ProjectUtil.class)) {
            projectUtilMock.when(() -> ProjectUtil.getConfigValue(Constants.SUNBIRD_ENCRYPTION))
                    .thenReturn("TEST_VALUE");

            DefaultEncryptionServiceImpl service = new DefaultEncryptionServiceImpl();

            Field sunbirdEncryptionField = DefaultEncryptionServiceImpl.class.getDeclaredField("sunbirdEncryption");
            sunbirdEncryptionField.setAccessible(true);
            String sunbirdEncryption = (String) sunbirdEncryptionField.get(service);

            // Verify the field was initialized
            assertNotNull("sunbirdEncryption should be initialized", sunbirdEncryption);

            // Verify the method was called
            projectUtilMock.verify(() -> ProjectUtil.getConfigValue(Constants.SUNBIRD_ENCRYPTION));
        }
    }

    @Test
    public void testEncryptDataMapWhenEncryptionOn() throws Exception {
        // Set sunbirdEncryption to ON using reflection
        Field field = DefaultEncryptionServiceImpl.class.getDeclaredField("sunbirdEncryption");
        field.setAccessible(true);
        field.set(encryptionService, "ON");

        // Create test data
        Map<String, Object> testData = new HashMap<>();
        testData.put("string", "value");
        testData.put("number", 123);

        Map<String, Object> nestedMap = new HashMap<>();
        nestedMap.put("key", "value");
        testData.put("map", nestedMap);

        List<String> nestedList = new ArrayList<>();
        nestedList.add("item");
        testData.put("list", nestedList);

        // Test encryption
        Map<String, Object> result = encryptionService.encryptData(testData);

        // Verify results
        assertNotNull("Result should not be null", result);
        assertNotEquals("String value should be encrypted", "value", result.get("string"));
        assertNotEquals("Number value should be encrypted", "123", result.get("number"));
        assertEquals("Nested map should not be encrypted", nestedMap, result.get("map"));
        assertEquals("Nested list should not be encrypted", nestedList, result.get("list"));
    }

    @Test
    public void testEncryptDataMapWhenEncryptionOff() throws Exception {
        // Set sunbirdEncryption to OFF using reflection
        Field field = DefaultEncryptionServiceImpl.class.getDeclaredField("sunbirdEncryption");
        field.setAccessible(true);
        field.set(encryptionService, "OFF");

        // Create test data
        Map<String, Object> testData = new HashMap<>();
        testData.put("key", "value");

        // Test encryption is bypassed
        Map<String, Object> result = encryptionService.encryptData(testData);

        // Verify nothing was encrypted
        assertEquals("Data should not be modified when encryption is OFF", testData, result);
        assertEquals("Value should remain unchanged", "value", result.get("key"));
    }

    @Test
    public void testEncryptDataWithNullMap() {
        // Set sunbirdEncryption to ON using reflection
        try {
            Field field = DefaultEncryptionServiceImpl.class.getDeclaredField("sunbirdEncryption");
            field.setAccessible(true);
            field.set(encryptionService, "ON");
        } catch (Exception e) {
            fail("Failed to set up test: " + e.getMessage());
        }

        // Test with null map
        Map<String, Object> result = encryptionService.encryptData((Map<String, Object>) null);
        assertNull("Null map should return null", result);
    }

    @Test
    public void testEncryptDataListWhenEncryptionOn() throws Exception {
        // Set sunbirdEncryption to ON using reflection
        Field field = DefaultEncryptionServiceImpl.class.getDeclaredField("sunbirdEncryption");
        field.setAccessible(true);
        field.set(encryptionService, "ON");

        // Create test data
        List<Map<String, Object>> testList = new ArrayList<>();
        Map<String, Object> map1 = new HashMap<>();
        map1.put("key1", "value1");
        Map<String, Object> map2 = new HashMap<>();
        map2.put("key2", "value2");
        testList.add(map1);
        testList.add(map2);

        // Test encryption
        List<Map<String, Object>> result = encryptionService.encryptData(testList);

        // Verify results
        assertNotNull("Result should not be null", result);
        assertEquals("List size should be preserved", 2, result.size());
        assertNotEquals("Value in first map should be encrypted", "value1", result.get(0).get("key1"));
        assertNotEquals("Value in second map should be encrypted", "value2", result.get(1).get("key2"));
    }

    @Test
    public void testEncryptDataListWithNullAndEmptyList() {
        // Set sunbirdEncryption to ON using reflection
        try {
            Field field = DefaultEncryptionServiceImpl.class.getDeclaredField("sunbirdEncryption");
            field.setAccessible(true);
            field.set(encryptionService, "ON");
        } catch (Exception e) {
            fail("Failed to set up test: " + e.getMessage());
        }

        // Test with null list - Add explicit cast to resolve ambiguity
        List<Map<String, Object>> nullResult = encryptionService.encryptData((List<Map<String, Object>>) null);
        assertNull("Null list should return null", nullResult);

        // Test with empty list
        List<Map<String, Object>> emptyList = new ArrayList<>();
        List<Map<String, Object>> emptyResult = encryptionService.encryptData(emptyList);
        assertEquals("Empty list should be returned as-is", 0, emptyResult.size());
    }

    @Test
    public void testEncryptDataStringWhenEncryptionOn() throws Exception {
        // Set sunbirdEncryption to ON using reflection
        Field field = DefaultEncryptionServiceImpl.class.getDeclaredField("sunbirdEncryption");
        field.setAccessible(true);
        field.set(encryptionService, "ON");

        // Test with non-empty string
        String result = encryptionService.encryptData("test");
        assertNotNull("Result should not be null", result);
        assertNotEquals("String should be encrypted", "test", result);

        // Test with empty string
        String emptyResult = encryptionService.encryptData("");
        assertEquals("Empty string should remain empty", "", emptyResult);

        // Test with null string - Add explicit cast to resolve ambiguity
        String nullResult = encryptionService.encryptData((String) null);
        assertNull("Null string should remain null", nullResult);
    }
    @Test
    public void testEncryptDataStringWhenEncryptionOff() throws Exception {
        // Set sunbirdEncryption to OFF using reflection
        Field field = DefaultEncryptionServiceImpl.class.getDeclaredField("sunbirdEncryption");
        field.setAccessible(true);
        field.set(encryptionService, "OFF");

        // Test string remains unchanged
        String result = encryptionService.encryptData("test");
        assertEquals("String should remain unchanged when encryption is OFF", "test", result);
    }

    @Test
    public void testEncryptMethod() {
        // Test encrypt static method
        String original = "testValue";
        String encrypted = DefaultEncryptionServiceImpl.encrypt(original);

        assertNotNull("Encrypted value should not be null", encrypted);
        assertNotEquals("Encrypted value should be different from original", original, encrypted);
    }

    @Test(expected = ProjectCommonException.class)
    public void testEncryptMethodWhenCipherFails() throws Exception {
        // Save original cipher
        Field cipherField = DefaultEncryptionServiceImpl.class.getDeclaredField("c");
        cipherField.setAccessible(true);
        Cipher originalCipher = (Cipher) cipherField.get(null);

        try {
            // Set cipher to null to force exception
            cipherField.set(null, null);

            // Should throw exception
            DefaultEncryptionServiceImpl.encrypt("test");
        } finally {
            // Restore original cipher
            cipherField.set(null, originalCipher);
        }
    }

    @Test
    public void testGetSalt() {
        String salt = DefaultEncryptionServiceImpl.getSalt();
        assertNotNull("Salt should not be null", salt);
        assertFalse("Salt should not be empty", salt.isEmpty());
    }
}