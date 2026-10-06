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

public class DefaultEncryptionServiceImpl implements EncryptionService {

    private static final Logger logger = LoggerFactory.getLogger(DefaultEncryptionServiceImpl.class);

    private static final byte[] keyValue =
            new byte[] {'T', 'h', 'i', 's', 'A', 's', 'I', 'S', 'e', 'r', 'c', 'e', 'K', 't', 'e', 'y'};

    private static String encryptionKey = "";

    private String sunbirdEncryption = "";

    private static Cipher c;

    static {
        try {
            encryptionKey = getSalt();
            Key key = generateKey();
            // NOTE: SonarQube S5542 "Use a secure padding scheme" flags the line below.
            // ALGORITHM = "AES" (see EncryptionService), so Cipher.getInstance(ALGORITHM) resolves to the
            // JDK default transformation "AES/ECB/PKCS5Padding". This is intentionally left UNCHANGED here:
            // this cipher encrypts data that is persisted in storage (e.g. user keys) and must stay
            // compatible with DefaultDecryptionServiceImpl, which decrypts already-stored data using the
            // same transformation. Switching the mode/padding (e.g. to AES/GCM) would make all
            // previously-encrypted data undecryptable. Changing this requires a human-approved migration
            // strategy (e.g. dual-read with a new scheme, re-encrypt existing data, etc.).
            c = Cipher.getInstance(ALGORITHM);
            c.init(Cipher.ENCRYPT_MODE, key);
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        }
    }

    public DefaultEncryptionServiceImpl() {
        sunbirdEncryption = System.getenv(Constants.SUNBIRD_ENCRYPTION);
        if (StringUtils.isBlank(sunbirdEncryption)) {
            sunbirdEncryption = ProjectUtil.getConfigValue(Constants.SUNBIRD_ENCRYPTION);
        }
    }

    @Override
    public Map<String, Object> encryptData(Map<String, Object> data) {
        if (Constants.ON.equalsIgnoreCase(sunbirdEncryption)) {
            if (data == null) {
                return data;
            }
            Iterator<Map.Entry<String, Object>> itr = data.entrySet().iterator();
            while (itr.hasNext()) {
                Map.Entry<String, Object> entry = itr.next();
                if (!(entry.getValue() instanceof Map || entry.getValue() instanceof List)
                        && null != entry.getValue()) {
                    data.put(entry.getKey(), encrypt(entry.getValue() + ""));
                }
            }
        }
        return data;
    }

    @Override
    public List<Map<String, Object>> encryptData(
            List<Map<String, Object>> data) {
        if (Constants.ON.equalsIgnoreCase(sunbirdEncryption)) {
            if (data == null || data.isEmpty()) {
                return data;
            }
            for (Map<String, Object> map : data) {
                encryptData(map);
            }
        }
        return data;
    }

    @Override
    public String encryptData(String data) {
        if (Constants.ON.equalsIgnoreCase(sunbirdEncryption)) {
            if (StringUtils.isNotBlank(data)) {
                return encrypt(data);
            } else {
                return data;
            }
        } else {
            return data;
        }
    }

    /**
     * this method is used to encrypt the password.
     *
     * @param value String password
     * @return encrypted password.
     */
    @SuppressWarnings("restriction")
    public static String encrypt(String value) {
        String valueToEnc = null;
        String eValue = value;
        for (int i = 0; i < ITERATIONS; i++) {
            valueToEnc = encryptionKey + eValue;
            byte[] encValue = new byte[0];
            try {
                encValue = c.doFinal(valueToEnc.getBytes(StandardCharsets.UTF_8));
            } catch (Exception e) {
                logger.error("Exception while encrypting user data, with message : " + e.getMessage(), e);
                throw new ProjectCommonException(
                        ResponseCode.SERVER_ERROR,
                        ResponseCode.SERVER_ERROR.getErrorMessage(),
                        ResponseCode.SERVER_ERROR.getStatusCode());
            }
            eValue = new BASE64Encoder().encode(encValue);
        }
        return eValue;
    }

    private static Key generateKey() {
        return new SecretKeySpec(keyValue, ALGORITHM);
    }

    /** @return */
    public static String getSalt() {
        if (!StringUtils.isBlank(encryptionKey)) {
            return encryptionKey;
        } else {
            encryptionKey = System.getenv(Constants.ENCRYPTION_KEY);
            if (StringUtils.isBlank(encryptionKey)) {
                logger.info("Salt value is not provided by Env");
                encryptionKey = ProjectUtil.getConfigValue(Constants.ENCRYPTION_KEY);
            }
        }
        if (StringUtils.isBlank(encryptionKey)) {
            logger.info("throwing exception for invalid salt");
            throw new ProjectCommonException(
                    ResponseCode.INVALID_PARAMETER_VALUE,
                    String.format(
                            ResponseCode.INVALID_PARAMETER_VALUE.getErrorMessage(), Constants.ENCRYPTION_KEY),
                    ResponseCode.SERVER_ERROR.getStatusCode());
        }
        return encryptionKey;
    }

}
