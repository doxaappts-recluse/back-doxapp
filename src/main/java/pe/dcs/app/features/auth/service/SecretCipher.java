package pe.dcs.app.features.auth.service;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

/** Cifrado AES-256-GCM de secretos en reposo (secreto TOTP). Formato: {@code v1:<iv b64>:<cifrado b64>}. */
@Component
public class SecretCipher {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final String base64Key;
    private SecretKey key;

    public SecretCipher(@Value("${app.security.mfa-key:}") String base64Key) {
        this.base64Key = base64Key;
    }

    @PostConstruct
    void init() {
        byte[] raw;
        try {
            raw = Base64.getDecoder().decode(base64Key == null ? "" : base64Key.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("MFA_ENC_KEY debe estar en Base64", e);
        }
        if (raw.length != 32) {
            throw new IllegalStateException("MFA_ENC_KEY (app.security.mfa-key) es obligatoria: 32 bytes en Base64 (openssl rand -base64 32)");
        }
        this.key = new SecretKeySpec(raw, "AES");
    }

    public String encrypt(String plain) {
        try {
            byte[] iv = new byte[12];
            RANDOM.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, iv));
            byte[] ct = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            return "v1:" + Base64.getEncoder().encodeToString(iv) + ":" + Base64.getEncoder().encodeToString(ct);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("No se pudo cifrar el secreto", e);
        }
    }

    public String decrypt(String stored) {
        try {
            String[] parts = stored.split(":");
            if (parts.length != 3 || !parts[0].equals("v1")) {
                throw new IllegalArgumentException("formato de secreto no reconocido");
            }
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, Base64.getDecoder().decode(parts[1])));
            return new String(cipher.doFinal(Base64.getDecoder().decode(parts[2])), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("No se pudo descifrar el secreto", e);
        }
    }
}
