package com.igot.cb.util;

import com.igot.cb.exceptions.ProjectCommonException;
import com.igot.cb.exceptions.ResponseCode;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class DefaultDecryptionServiceImpl implements DecryptionService {

    private static final Logger logger = LoggerFactory.getLogger(DefaultDecryptionServiceImpl.class);

    // Default AES key, kept only as a fallback for deployments that have not set
    // Constants.AES_SECRET_KEY (env var AES_ENCRYPTION_KEY or the equivalent properties entry).
    // It must stay the same as DefaultEncryptionServiceImpl's default: both must agree on the key
    // used to decrypt data already persisted in storage (e.g. user keys). Rotate via the env
    // var/properties entry, never by changing this literal, or existing encrypted data becomes
    // unreadable.
    private static final byte[] defaultKeyValue =
            new byte[] {'T', 'h', 'i', 's', 'A', 's', 'I', 'S', 'e', 'r', 'c', 'e', 'K', 't', 'e', 'y'}; // NOSONAR

    private static String sunbirdEncryptionSalt = "";

    private String sunbirdEncryption = "";

    private static Cipher c;

    static {
        try {
            sunbirdEncryptionSalt = DefaultEncryptionServiceImpl.getSalt();
            Key key = generateKey();
            // NOTE: SonarQube S5542 "Use a secure padding scheme" flags the line below.
            // ALGORITHM = "AES" (see DecryptionService), so Cipher.getInstance(ALGORITHM) resolves to the
            // JDK default transformation "AES/ECB/PKCS5Padding". This is intentionally left UNCHANGED here:
            // this cipher decrypts data that was encrypted with the same transformation and is already
            // persisted in storage (e.g. user keys). Switching the mode/padding (e.g. to AES/GCM) would
            // make all previously-encrypted data unreadable. Changing this requires a human-approved
            // migration strategy (e.g. dual-read with a new scheme, re-encrypt existing data, etc.).
            c = Cipher.getInstance(ALGORITHM);
            c.init(Cipher.DECRYPT_MODE, key);
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        }
    }

    public DefaultDecryptionServiceImpl() {
        sunbirdEncryption = System.getenv(Constants.SUNBIRD_ENCRYPTION);
        if (StringUtils.isBlank(sunbirdEncryption)) {
            sunbirdEncryption = ProjectUtil.getConfigValue(Constants.SUNBIRD_ENCRYPTION);
        }
    }

    @Override
    public Map<String, Object> decryptData(Map<String, Object> data) {
        if (Constants.ON.equalsIgnoreCase(sunbirdEncryption)) {
            if (data == null) {
                return data;
            }
            Iterator<Map.Entry<String, Object>> itr = data.entrySet().iterator();
            while (itr.hasNext()) {
                Map.Entry<String, Object> entry = itr.next();
                if (!(entry.getValue() instanceof Map || entry.getValue() instanceof List)
                        && null != entry.getValue()) {
                    data.put(entry.getKey(), decrypt(entry.getValue() + "", false));
                }
            }
        }
        return data;
    }

    @Override
    public List<Map<String, Object>> decryptData(
            List<Map<String, Object>> data) {
        if (Constants.ON.equalsIgnoreCase(sunbirdEncryption)) {
            if (data == null || data.isEmpty()) {
                return data;
            }

            for (Map<String, Object> map : data) {
                decryptData(map);
            }
        }
        return data;
    }

    @Override
    public String decryptData(String data) {
        return decryptData(data, false);
    }

    @Override
    public String decryptData(String data, boolean throwExceptionOnFailure) {
        if (Constants.ON.equalsIgnoreCase(sunbirdEncryption)) {
            if (StringUtils.isBlank(data)) {
                return data;
            } else {
                return decrypt(data, throwExceptionOnFailure);
            }
        } else {
            return data;
        }
    }

    public static String decrypt(
            String value, boolean throwExceptionOnFailure) {
        try {
            String dValue = null;
            String valueToDecrypt = value.trim();
            for (int i = 0; i < ITERATIONS; i++) {
                byte[] decordedValue = new BASE64Decoder().decodeBuffer(valueToDecrypt);
                byte[] decValue = c.doFinal(decordedValue);
                dValue =
                        new String(decValue, StandardCharsets.UTF_8).substring(sunbirdEncryptionSalt.length());
                valueToDecrypt = dValue;
            }
            return dValue;
        } catch (Exception ex) {
            // This could happen with masked email and phone number. Not others.
            logger.error("DefaultDecryptionServiceImpl:decrypt: ignorable errorMsg = ", ex);
            if (throwExceptionOnFailure) {
                logger.info("Throwing exception error upon explicit ask by callers for value {}", value);
                ProjectCommonException.throwServerErrorException(ResponseCode.SERVER_ERROR);
            }
        }
        return value;
    }

    private static Key generateKey() {
        String configuredKey = ProjectUtil.getConfigValue(Constants.AES_SECRET_KEY);
        byte[] resolvedKeyValue =
                StringUtils.isNotBlank(configuredKey) ? configuredKey.getBytes(StandardCharsets.UTF_8) : defaultKeyValue;
        return new SecretKeySpec(resolvedKeyValue, ALGORITHM);
    }

}
