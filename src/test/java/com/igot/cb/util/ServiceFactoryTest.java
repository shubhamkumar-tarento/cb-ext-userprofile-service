package com.igot.cb.util;

import static org.junit.Assert.*;
import org.junit.Test;

public class ServiceFactoryTest {

    @Test
    public void testGetEncryptionServiceInstance() {
        EncryptionService service = ServiceFactory.getEncryptionServiceInstance();
        assertNotNull("Encryption service should not be null", service);
        assertTrue("Should be instance of DefaultEncryptionServiceImpl",
            service instanceof DefaultEncryptionServiceImpl);
        EncryptionService service2 = ServiceFactory.getEncryptionServiceInstance();
        assertSame("Should return same instance", service, service2);
    }

    @Test
    public void testGetDecryptionServiceInstance() {
        DecryptionService service = ServiceFactory.getDecryptionServiceInstance();
        assertNotNull("Decryption service should not be null", service);
        assertTrue("Should be instance of DefaultDecryptionServiceImpl",
            service instanceof DefaultDecryptionServiceImpl);
        DecryptionService service2 = ServiceFactory.getDecryptionServiceInstance();
        assertSame("Should return same instance", service, service2);
    }

    @Test
    public void testStaticInitialization() {
        EncryptionService encService = ServiceFactory.getEncryptionServiceInstance();
        DecryptionService decService = ServiceFactory.getDecryptionServiceInstance();
        assertNotNull("Encryption service should be initialized", encService);
        assertNotNull("Decryption service should be initialized", decService);
        assertNotSame("Services should be different instances", encService, decService);
    }
}