package com.igot.cb.util;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

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
import org.mockito.junit.MockitoJUnitRunner;

import com.igot.cb.exceptions.ProjectCommonException;

@RunWith(MockitoJUnitRunner.class)
public class DefaultDecryptionServiceImplTest {

    private DefaultDecryptionServiceImpl decryptionService;

    @Before
    public void setUp() {
        decryptionService = new DefaultDecryptionServiceImpl();
    }

    @Test
    public void testConstructorWithConfigFallback() throws Exception {
        try (MockedStatic<ProjectUtil> projectUtilMock = mockStatic(ProjectUtil.class)) {
            projectUtilMock.when(() -> ProjectUtil.getConfigValue(Constants.SUNBIRD_ENCRYPTION))
                    .thenReturn("ON");
            DefaultDecryptionServiceImpl service = new DefaultDecryptionServiceImpl();
            projectUtilMock.verify(() -> ProjectUtil.getConfigValue(Constants.SUNBIRD_ENCRYPTION));
            Field field = DefaultDecryptionServiceImpl.class.getDeclaredField("sunbirdEncryption");
            field.setAccessible(true);
            String value = (String) field.get(service);
            assertNotNull("sunbirdEncryption should be initialized", value);
        }
    }

    @Test
    public void testDecryptDataStringWhenEncryptionOff() throws Exception {
        Field field = DefaultDecryptionServiceImpl.class.getDeclaredField("sunbirdEncryption");
        field.setAccessible(true);
        field.set(decryptionService, "OFF");
        String testData = "testData";
        String result = decryptionService.decryptData(testData);
        assertEquals("Data should not be modified when encryption is OFF", testData, result);
    }

    @Test
    public void testDecryptDataStringWhenEncryptionOn() throws Exception {
        Field field = DefaultDecryptionServiceImpl.class.getDeclaredField("sunbirdEncryption");
        field.setAccessible(true);
        field.set(decryptionService, "ON");
        try (MockedStatic<DefaultDecryptionServiceImpl> staticMock = mockStatic(DefaultDecryptionServiceImpl.class)) {
            staticMock.when(() -> DefaultDecryptionServiceImpl.decrypt(anyString(), eq(false)))
                    .thenReturn("decrypted");
            String result = decryptionService.decryptData("encrypted");
            assertEquals("Data should be decrypted", "decrypted", result);
            staticMock.verify(() -> DefaultDecryptionServiceImpl.decrypt("encrypted", false));
        }
    }

    @Test
    public void testDecryptDataMapWhenEncryptionOn() throws Exception {
        Field field = DefaultDecryptionServiceImpl.class.getDeclaredField("sunbirdEncryption");
        field.setAccessible(true);
        field.set(decryptionService, "ON");
        Map<String, Object> testData = new HashMap<>();
        testData.put("key1", "value1");
        testData.put("key2", 123);
        testData.put("key3", new HashMap<>()); // Nested map
        try (MockedStatic<DefaultDecryptionServiceImpl> staticMock = mockStatic(DefaultDecryptionServiceImpl.class)) {
            staticMock.when(() -> DefaultDecryptionServiceImpl.decrypt(anyString(), eq(false)))
                    .thenReturn("decrypted");
            Map<String, Object> result = decryptionService.decryptData(testData);
            assertEquals("String value should be decrypted", "decrypted", result.get("key1"));
            assertEquals("Number value should be decrypted", "decrypted", result.get("key2"));
            assertTrue("Nested map should not be decrypted", result.get("key3") instanceof Map);
            staticMock.verify(() -> DefaultDecryptionServiceImpl.decrypt("value1", false));
            staticMock.verify(() -> DefaultDecryptionServiceImpl.decrypt("123", false));
        }
    }

    @Test
    public void testDecryptDataListWhenEncryptionOn() throws Exception {
        Field field = DefaultDecryptionServiceImpl.class.getDeclaredField("sunbirdEncryption");
        field.setAccessible(true);
        field.set(decryptionService, "ON");
        Map<String, Object> map1 = new HashMap<>();
        map1.put("key1", "value1");
        Map<String, Object> map2 = new HashMap<>();
        map2.put("key2", "value2");
        List<Map<String, Object>> testData = new ArrayList<>();
        testData.add(map1);
        testData.add(map2);
        try (MockedStatic<DefaultDecryptionServiceImpl> staticMock = mockStatic(DefaultDecryptionServiceImpl.class)) {
            staticMock.when(() -> DefaultDecryptionServiceImpl.decrypt(anyString(), eq(false)))
                    .thenReturn("decrypted");
            List<Map<String, Object>> result = decryptionService.decryptData(testData);
            assertEquals("Value in first map should be decrypted", "decrypted", result.get(0).get("key1"));
            assertEquals("Value in second map should be decrypted", "decrypted", result.get(1).get("key2"));
            staticMock.verify(() -> DefaultDecryptionServiceImpl.decrypt("value1", false));
            staticMock.verify(() -> DefaultDecryptionServiceImpl.decrypt("value2", false));
        }
    }

    @Test
    public void testHandleNullAndEmptyInputs() throws Exception {
        Field field = DefaultDecryptionServiceImpl.class.getDeclaredField("sunbirdEncryption");
        field.setAccessible(true);
        field.set(decryptionService, "ON");
        assertNull("Null map should return null", decryptionService.decryptData((Map<String, Object>) null));
        assertNull("Null list should return null", decryptionService.decryptData((List<Map<String, Object>>) null));
        List<Map<String, Object>> emptyList = new ArrayList<>();
        assertEquals("Empty list should return empty list", emptyList, decryptionService.decryptData(emptyList));
        assertNull("Null string should return null", decryptionService.decryptData((String) null));
        assertEquals("Empty string should return empty string", "", decryptionService.decryptData(""));
    }

    @Test
    public void testDecryptDataListWhenEncryptionOff() throws Exception {
        Field field = DefaultDecryptionServiceImpl.class.getDeclaredField("sunbirdEncryption");
        field.setAccessible(true);
        field.set(decryptionService, "OFF");
        Map<String, Object> map1 = new HashMap<>();
        map1.put("key1", "value1");
        List<Map<String, Object>> testData = new ArrayList<>();
        testData.add(map1);
        List<Map<String, Object>> result = decryptionService.decryptData(testData);
        assertEquals("List should remain unchanged when encryption is OFF", testData, result);
        assertEquals("Value should remain unchanged", "value1", result.get(0).get("key1"));
    }

    @Test
    public void testDecryptDataMapWithNullEntryValueWhenEncryptionOn() throws Exception {
        // Covers the branch where an entry's value is null (not a Map/List)
        // so it should be skipped and not passed to decrypt().
        Field field = DefaultDecryptionServiceImpl.class.getDeclaredField("sunbirdEncryption");
        field.setAccessible(true);
        field.set(decryptionService, "ON");
        Map<String, Object> testData = new HashMap<>();
        testData.put("nullValue", null);
        Map<String, Object> result = decryptionService.decryptData(testData);
        assertNotNull("Result should not be null", result);
        assertNull("Null value should remain null", result.get("nullValue"));
    }

    @Test
    public void testEncryptThenDecryptRoundTripUsesRealCipher() {
        // Exercises the real (non-mocked) success path of the static decrypt()
        // method: base64 decode, AES doFinal, substring and loop iteration,
        // by round-tripping a value through the real encrypt/decrypt ciphers.
        String original = "roundTripValue123";
        String encrypted = DefaultEncryptionServiceImpl.encrypt(original);
        assertNotNull("Encrypted value should not be null", encrypted);
        String decrypted = DefaultDecryptionServiceImpl.decrypt(encrypted, false);
        assertEquals("Decrypted value should match the original", original, decrypted);
    }

    @Test
    public void testStaticDecryptMethod() throws Exception {
        Field cipherField = DefaultDecryptionServiceImpl.class.getDeclaredField("c");
        cipherField.setAccessible(true);
        Cipher originalCipher = (Cipher) cipherField.get(null);
        cipherField.set(null, null);
        try {
            String result = DefaultDecryptionServiceImpl.decrypt("test", false);
            assertEquals("Should return original value on exception", "test", result);
            try {
                DefaultDecryptionServiceImpl.decrypt("test", true);
                fail("Should have thrown exception");
            } catch (ProjectCommonException e) {
                // expected: decrypt(..., true) throws on failure, nothing further to assert here
            }
        } finally {
            cipherField.set(null, originalCipher);
        }
    }
}