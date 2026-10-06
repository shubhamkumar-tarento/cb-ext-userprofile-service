package com.igot.cb.util;

import java.util.List;
import java.util.Map;

public class UserUtility {

    private UserUtility() {
    }

    static {
        init();
    }

    public static Map<String, Object> decryptSpecificUserData(
            Map<String, Object> userMap, List<String> fieldsToDecrypt) {
        DecryptionService service = ServiceFactory.getDecryptionServiceInstance();
        for (String key : fieldsToDecrypt) {
            userMap.computeIfPresent(key, (k, v) -> service.decryptData((String) v, false));
        }
        return userMap;
    }

    private static void init() {
        PropertiesCache.getInstance().getProperty("userkey.encryption");
        PropertiesCache.getInstance().getProperty("userkey.decryption");
        PropertiesCache.getInstance().getProperty("userkey.masked");
    }

}
