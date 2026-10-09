package iuh.fit.se.contractservice.service.driveraccess;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * "Mã nhận chuyến": {@code <uuidv7>.<secret>}. The secret is 32 CSPRNG bytes in base64url without padding
 * (43 characters, case-sensitive); only its SHA-256 is stored. The UUID part is normalised to lower case.
 */
public final class DriverAccessCode {
    private static final Pattern SECRET = Pattern.compile("[A-Za-z0-9_-]{43}");
    private static final Pattern UUID_TEXT = Pattern.compile("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");

    public record Parsed(UUID grantId, String secret) {
        @Override public String toString() { return "DriverAccessCode(" + grantId + ".<redacted>)"; }
    }

    private DriverAccessCode() {
    }

    public static String newSecret(SecureRandom random) {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public static String format(UUID grantId, String secret) {
        return grantId.toString().toLowerCase(Locale.ROOT) + "." + secret;
    }

    /** Accepts surrounding whitespace (paste/QR); rejects anything else rather than guessing. */
    public static Optional<Parsed> parse(String code) {
        if (code == null) return Optional.empty();
        String trimmed = code.strip();
        int dot = trimmed.indexOf('.');
        if (dot < 0 || trimmed.indexOf('.', dot + 1) >= 0) return Optional.empty();
        String id = trimmed.substring(0, dot), secret = trimmed.substring(dot + 1);
        if (!UUID_TEXT.matcher(id).matches() || !SECRET.matcher(secret).matches()) return Optional.empty();
        UUID grantId = UUID.fromString(id.toLowerCase(Locale.ROOT));
        if (!UuidV7.isV7(grantId)) return Optional.empty();
        return Optional.of(new Parsed(grantId, secret));
    }

    public static String hash(String secret) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(secret.getBytes(StandardCharsets.US_ASCII)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is not available", impossible);
        }
    }

    public static boolean matches(String secret, String storedHash) {
        return storedHash != null && MessageDigest.isEqual(hash(secret).getBytes(StandardCharsets.US_ASCII),
                storedHash.getBytes(StandardCharsets.US_ASCII));
    }
}
