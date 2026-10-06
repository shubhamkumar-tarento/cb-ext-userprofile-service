package com.igot.cb.util;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedStatic;

public class UserUtilityTest {

    private DecryptionService mockDecryptionService;

    @Before
    public void setup() {
        mockDecryptionService = mock(DecryptionService.class);
    }

    @Test
    public void testDecryptSpecificUserData() {
        try (MockedStatic<ServiceFactory> mockedFactory = mockStatic(ServiceFactory.class)) {
            mockedFactory.when(ServiceFactory::getDecryptionServiceInstance)
                .thenReturn(mockDecryptionService);
            when(mockDecryptionService.decryptData(anyString(), eq(false)))
                .thenAnswer(i -> "decrypted_" + i.getArgument(0));
            Map<String, Object> userMap = new HashMap<>();
            userMap.put("email", "encrypted_email@test.com");
            userMap.put("phone", "encrypted_1234567890");
            userMap.put("name", "John Doe");
            List<String> fieldsToDecrypt = Arrays.asList("email", "phone", "address");
            Map<String, Object> result = UserUtility.decryptSpecificUserData(userMap, fieldsToDecrypt);
            assertEquals("decrypted_encrypted_email@test.com", result.get("email"));
            assertEquals("decrypted_encrypted_1234567890", result.get("phone"));
            assertEquals("John Doe", result.get("name"));
            assertFalse(result.containsKey("address"));
            verify(mockDecryptionService).decryptData("encrypted_email@test.com", false);
            verify(mockDecryptionService).decryptData("encrypted_1234567890", false);
            verify(mockDecryptionService, times(2)).decryptData(anyString(), eq(false));
        }
    }

    @Test
    public void testDecryptSpecificUserDataWithEmptyMap() {
        try (MockedStatic<ServiceFactory> mockedFactory = mockStatic(ServiceFactory.class)) {
            mockedFactory.when(ServiceFactory::getDecryptionServiceInstance)
                .thenReturn(mockDecryptionService);
            Map<String, Object> userMap = new HashMap<>();
            List<String> fieldsToDecrypt = Arrays.asList("email", "phone");
            Map<String, Object> result = UserUtility.decryptSpecificUserData(userMap, fieldsToDecrypt);
            assertTrue(result.isEmpty());
            verify(mockDecryptionService, never()).decryptData(anyString(), anyBoolean());
        }
    }

    @Test
    public void testInit() {
        try (MockedStatic<ServiceFactory> mockedFactory = mockStatic(ServiceFactory.class);
             MockedStatic<PropertiesCache> mockedProperties = mockStatic(PropertiesCache.class)) {
            mockedFactory.when(ServiceFactory::getDecryptionServiceInstance)
                    .thenReturn(mockDecryptionService);
            PropertiesCache mockPropCache = mock(PropertiesCache.class);
            mockedProperties.when(PropertiesCache::getInstance)
                    .thenReturn(mockPropCache);
            when(mockPropCache.getProperty("userkey.encryption"))
                    .thenReturn("key1,key2");
            when(mockPropCache.getProperty("userkey.decryption"))
                    .thenReturn("decrypt1,decrypt2");
            when(mockPropCache.getProperty("userkey.masked"))
                    .thenReturn("masked1,masked2");
            Method initMethod = UserUtility.class.getDeclaredMethod("init");
            initMethod.setAccessible(true);
            initMethod.invoke(null);
            verify(mockPropCache).getProperty("userkey.encryption");
            verify(mockPropCache).getProperty("userkey.decryption");
            verify(mockPropCache).getProperty("userkey.masked");
        } catch (Exception e) {
            fail("Test failed: " + e.getMessage());
        }
    }

    @Test
    public void testDecryptSpecificUserDataWithNullValues() {
        try (MockedStatic<ServiceFactory> mockedFactory = mockStatic(ServiceFactory.class)) {
            mockedFactory.when(ServiceFactory::getDecryptionServiceInstance)
                .thenReturn(mockDecryptionService);
            Map<String, Object> userMap = new HashMap<>();
            userMap.put("email", null);
            List<String> fieldsToDecrypt = Arrays.asList("email");
            Map<String, Object> result = UserUtility.decryptSpecificUserData(userMap, fieldsToDecrypt);
            assertNull(result.get("email"));
            verify(mockDecryptionService, never()).decryptData(anyString(), anyBoolean());
        }
    }
}