package pe.dcs.app.util;

import java.util.Base64;

public class CryptoUtils {

    private CryptoUtils() {
    }

    public static String encodeSecretBase64(String secret) {

        Base64.Encoder encoder = Base64.getEncoder();

        return encoder.encodeToString(secret.getBytes());
    }
}
