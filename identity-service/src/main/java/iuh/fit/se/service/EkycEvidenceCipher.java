package iuh.fit.se.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

/** AES-GCM ciphertext is bound to the report ID and part; no plaintext fallback. */
@Component
public class EkycEvidenceCipher {
    private final byte[] key;
    private final SecureRandom random = new SecureRandom();

    /** Loads a valid 256-bit evidence key or marks encrypted evidence storage unavailable. */
    public EkycEvidenceCipher(@Value("${app.ekyc.evidence-key:}") String encodedKey) {
        byte[] decoded;
        try { decoded = Base64.getDecoder().decode(encodedKey); }
        catch (IllegalArgumentException invalid) { decoded = new byte[0]; }
        key = decoded.length == 32 ? decoded : null;
    }
    /** Indicates whether the configured key can encrypt and decrypt evidence. */
    public boolean available() { return key != null; }

    /** Encrypts bytes with AES-GCM and binds them to the supplied report context. */
    public byte[] encrypt(byte[] plain, String context) {
        requireKey();
        try {
            byte[] nonce = new byte[12]; random.nextBytes(nonce);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, nonce));
            cipher.updateAAD(context.getBytes(StandardCharsets.UTF_8));
            byte[] encrypted = cipher.doFinal(plain);
            byte[] envelope = new byte[1 + nonce.length + encrypted.length];
            envelope[0] = 1; System.arraycopy(nonce, 0, envelope, 1, nonce.length);
            System.arraycopy(encrypted, 0, envelope, 13, encrypted.length);
            return envelope;
        } catch (Exception failure) { throw unavailable(); }
    }
    /** Decrypts an envelope only when its context-bound authentication tag is valid. */
    public byte[] decrypt(byte[] encrypted, String context) {
        requireKey();
        try {
            if (encrypted.length < 29 || encrypted[0] != 1) throw new IllegalArgumentException();
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"),
                    new GCMParameterSpec(128, Arrays.copyOfRange(encrypted, 1, 13)));
            cipher.updateAAD(context.getBytes(StandardCharsets.UTF_8));
            return cipher.doFinal(encrypted, 13, encrypted.length - 13);
        } catch (Exception failure) { throw unavailable(); }
    }
    /** Fails closed when no valid evidence encryption key is configured. */
    private void requireKey() { if (!available()) throw unavailable(); }

    /** Creates the consistent unavailable response used for encryption failures. */
    private ResponseStatusException unavailable() {
        return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Verification evidence storage unavailable");
    }
}
