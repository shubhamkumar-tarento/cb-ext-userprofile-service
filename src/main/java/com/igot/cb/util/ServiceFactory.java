package com.igot.cb.util;

public class ServiceFactory {

    private ServiceFactory() {
    }

    private static EncryptionService encryptionService;
    private static DecryptionService decryptionService;

    static {
        encryptionService = new DefaultEncryptionServiceImpl();
        decryptionService = new DefaultDecryptionServiceImpl();
    }

    /**
     * this method will provide encryptionServiceImple instance. by default it will provide
     * DefaultEncryptionServiceImpl instance to get a particular service impl instance , need to
     * change the object creation and provided logic.
     *
     * @return EncryptionService
     */
    public static EncryptionService getEncryptionServiceInstance() {
        return encryptionService;
    }

    /**
     * this method will provide decryptionServiceImple instance. by default it will provide
     * DefaultDecryptionServiceImpl instance to get a particular service impl instance , need to
     * change the object creation and provided logic.
     *
     * @return DecryptionService
     */
    public static DecryptionService getDecryptionServiceInstance() {
        return decryptionService;
    }


}
