package iuh.fit.se.contractservice.service.driveraccess;

import java.security.SecureRandom;
import java.time.Clock;
import java.util.UUID;

/**
 * RFC 9562 §5.7 UUIDv7: 48-bit Unix milliseconds, version 7, 12 random bits, variant 10, 62 random bits.
 * Used as an identifier only; possession of a UUID never grants access (RFC 9562 §8).
 */
public final class UuidV7 {
    private final Clock clock;
    private final SecureRandom random;

    public UuidV7(Clock clock, SecureRandom random) {
        this.clock = clock;
        this.random = random;
    }

    public UUID next() {
        long millis = clock.millis() & 0xFFFF_FFFF_FFFFL;
        long randA = random.nextInt(1 << 12);
        long msb = (millis << 16) | (0x7L << 12) | randA;
        long lsb = (random.nextLong() & 0x3FFF_FFFF_FFFF_FFFFL) | 0x8000_0000_0000_0000L;
        return new UUID(msb, lsb);
    }

    public static boolean isV7(UUID value) {
        return value != null && value.version() == 7 && value.variant() == 2;
    }
}
