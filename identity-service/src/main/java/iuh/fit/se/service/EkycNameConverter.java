package iuh.fit.se.service;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.springframework.stereotype.Component;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Keep existing entity/business-name API while encrypting new stored representative names. */
@Converter @Component
public class EkycNameConverter implements AttributeConverter<String, String> {
    private static final String PREFIX="enc:v1:";
    private final EkycEvidenceCipher cipher;

    /** Uses the evidence cipher to protect representative names in new database writes. */
    public EkycNameConverter(EkycEvidenceCipher cipher) { this.cipher=cipher; }

    /** Encrypts a representative name while preserving null database values. */
    @Override public String convertToDatabaseColumn(String name) {
        if (name==null) return null;
        byte[] plain=name.getBytes(StandardCharsets.UTF_8);
        try { return PREFIX+Base64.getEncoder().encodeToString(cipher.encrypt(plain, "ekyc-representative-name")); }
        finally { Arrays.fill(plain, (byte)0); }
    }
    /** Decrypts names written by this converter and leaves historical plaintext readable. */
    @Override public String convertToEntityAttribute(String stored) {
        if (stored==null || !stored.startsWith(PREFIX)) return stored; // Read historical rows without changing status.
        byte[] plain=cipher.decrypt(Base64.getDecoder().decode(stored.substring(PREFIX.length())), "ekyc-representative-name");
        try { return new String(plain, StandardCharsets.UTF_8); }
        finally { Arrays.fill(plain, (byte)0); }
    }
}
