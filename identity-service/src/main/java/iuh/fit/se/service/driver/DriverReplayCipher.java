package iuh.fit.se.service.driver;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

/**
 * AES-256-GCM for the short-lived replay copy of a token pair (lost-response retry). The key lives only in
 * the backend environment ({@code DRIVER_REPLAY_KEY}); ciphertext is bound to the session ID.
 */
@Component
public class DriverReplayCipher {
    private static final byte VERSION = 1;
    private final byte[] key;
    private final SecureRandom random = new SecureRandom();

    public DriverReplayCipher(@Value("${app.driver.replay-key:}") String encodedKey) {
        byte[] decoded;
        try {
            decoded = encodedKey == null || encodedKey.isBlank() ? new byte[0] : Base64.getDecoder().decode(encodedKey.trim());
        } catch (IllegalArgumentException invalid) {
            decoded = new byte[0];
        }
        key = decoded.length == 32 ? decoded : null;
    }

    public boolean available() {
        return key != null;
    }

    public byte[] encrypt(String plain, String associated) {
        if (key == null) throw new IllegalStateException("Replay key is not configured");
        try {
            byte[] nonce = new byte[12];
            random.nextBytes(nonce);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, nonce));
            cipher.updateAAD(associated.getBytes(StandardCharsets.UTF_8));
            byte[] body = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            byte[] out = new byte[1 + nonce.length + body.length];
            out[0] = VERSION;
            System.arraycopy(nonce, 0, out, 1, nonce.length);
            System.arraycopy(body, 0, out, 1 + nonce.length, body.length);
            return out;
        } catch (Exception failure) {
            throw new IllegalStateException("Replay encryption failed", failure);
        }
    }

    public String decrypt(byte[] encrypted, String associated) {
        if (key == null || encrypted == null || encrypted.length < 14 || encrypted[0] != VERSION) {
            throw new IllegalStateException("Replay copy is unavailable");
        }
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, Arrays.copyOfRange(encrypted, 1, 13)));
            cipher.updateAAD(associated.getBytes(StandardCharsets.UTF_8));
            return new String(cipher.doFinal(encrypted, 13, encrypted.length - 13), StandardCharsets.UTF_8);
        } catch (Exception failure) {
            throw new IllegalStateException("Replay copy is unavailable", failure);
        }
    }
}
