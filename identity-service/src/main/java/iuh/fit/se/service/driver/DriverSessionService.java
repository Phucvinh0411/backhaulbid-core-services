package iuh.fit.se.service.driver;

import com.fasterxml.jackson.databind.ObjectMapper;
import iuh.fit.se.domain.entity.DriverSession;
import iuh.fit.se.repository.DriverSessionRepository;
import iuh.fit.se.security.JwtTokenProvider;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Driver sign-in with a "mã nhận chuyến": no Account, phone or password. Identity issues the JWT; contract
 * decides on every redeem/refresh whether the session is still the trip's current assignment.
 */
@Service
public class DriverSessionService {
    public static final Duration SESSION_TTL = Duration.ofDays(7);
    /** A lost redeem response can be retried with the same code and request ID for this long. */
    public static final Duration REDEEM_REPLAY_TTL = Duration.ofMinutes(2);
    /** Two uploaders refreshing at once, or a lost refresh response, reuse the rotation result for this long. */
    public static final Duration ROTATION_GRACE = Duration.ofSeconds(30);
    private static final Pattern UUID_TEXT = Pattern.compile("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");
    private static final Pattern SECRET = Pattern.compile("[A-Za-z0-9_-]{43}");

    /** Token pair plus the normalised session the app stores. */
    public record DriverTokens(String accessToken, String refreshToken, long expiresIn, Instant refreshExpiresAt,
                               DriverSessionView session) {
        @Override public String toString() { return "DriverTokens(" + session.sessionId() + ", <tokens redacted>)"; }
    }

    public record DriverSessionView(String type, UUID sessionId, String role, UUID tripId, UUID driverProfileId,
                                    UUID assignmentId, long assignmentVersion) {
    }

    private record Code(UUID id, String secret) {
    }

    private final DriverSessionRepository sessions;
    private final ContractDriverClient contract;
    private final JwtTokenProvider tokens;
    private final DriverReplayCipher replay;
    private final DriverRedeemThrottle throttle;
    private final ObjectMapper json;
    private final Clock clock;
    private final TransactionTemplate tx;
    private final SecureRandom random = new SecureRandom();

    public DriverSessionService(DriverSessionRepository sessions, ContractDriverClient contract, JwtTokenProvider tokens,
                                DriverReplayCipher replay, DriverRedeemThrottle throttle, ObjectMapper json, Clock clock,
                                PlatformTransactionManager transactions) {
        this.sessions = sessions;
        this.contract = contract;
        this.tokens = tokens;
        this.replay = replay;
        this.throttle = throttle;
        this.json = json;
        this.clock = clock;
        this.tx = new TransactionTemplate(transactions);
    }

    public DriverTokens redeem(String rawCode, UUID requestId, String clientKey) {
        if (requestId == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Thiếu mã yêu cầu.");
        if (!replay.available()) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Đăng nhập tài xế chưa được cấu hình.");
        Code code = parse(rawCode).orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Mã nhận chuyến không đúng định dạng."));
        throttle.hit(clientKey, code.id().toString());
        ContractDriverClient.Exchange exchange = contract.exchange(code.id(), code.secret(), requestId);
        DriverTokens issued = tx.execute(status -> persistRedeem(exchange, requestId));
        // The assignment may have changed between contract's decision and now: never return a dead session.
        ContractDriverClient.Status live = contract.status(exchange.sessionId());
        if (!live.readable() || live.assignmentVersion() != exchange.assignmentVersion()) {
            revokeLocally(exchange.sessionId());
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Phân công của chuyến đã thay đổi. Nhờ chủ xe cấp mã mới.");
        }
        return issued;
    }

    private DriverTokens persistRedeem(ContractDriverClient.Exchange exchange, UUID requestId) {
        Instant now = clock.instant();
        DriverSession existing = sessions.lockById(exchange.sessionId()).orElse(null);
        if (existing != null) {
            // Contract only returns an existing session for the same code + request ID inside its window.
            if (!existing.getRedeemRequestId().equals(requestId) || existing.getRevokedAt() != null) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Mã nhận chuyến đã được sử dụng. Nhờ chủ xe cấp mã mới.");
            }
            if (existing.getReplayCipher() != null && existing.getReplayExpiresAt() != null && existing.getReplayExpiresAt().isAfter(now)) {
                return readReplay(existing);
            }
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Mã nhận chuyến đã được sử dụng. Nhờ chủ xe cấp mã mới.");
        }
        String refreshSecret = newSecret();
        DriverSession session = DriverSession.builder().id(exchange.sessionId()).grantId(exchange.grantId())
                .tripId(exchange.tripId()).driverProfileId(exchange.driverProfileId())
                .assignmentVersion(exchange.assignmentVersion()).redeemRequestId(requestId)
                .refreshHash(hash(refreshSecret)).expiresAt(now.plus(SESSION_TTL)).createdAt(now).build();
        DriverTokens pair = pair(session, refreshSecret);
        storeReplay(session, pair, now.plus(REDEEM_REPLAY_TTL));
        sessions.save(session);
        return pair;
    }

    public DriverTokens refresh(String refreshToken) {
        Code code = parse(refreshToken).orElseThrow(DriverSessionService::sessionEnded);
        // Live check first (network, outside the row lock); fails closed when contract is unreachable.
        ContractDriverClient.Status live = contract.status(code.id());
        if (!live.readable()) {
            revokeLocally(code.id());
            throw sessionEnded();
        }
        return tx.execute(status -> rotate(code, live));
    }

    private DriverTokens rotate(Code code, ContractDriverClient.Status live) {
        Instant now = clock.instant();
        DriverSession session = sessions.lockById(code.id()).orElseThrow(DriverSessionService::sessionEnded);
        if (session.getRevokedAt() != null || !session.getExpiresAt().isAfter(now)
                || session.getAssignmentVersion() != live.assignmentVersion()) throw sessionEnded();
        String presented = hash(code.secret());
        if (MessageDigest.isEqual(ascii(presented), ascii(session.getRefreshHash()))) {
            String next = newSecret();
            session.setPreviousRefreshHash(session.getRefreshHash());
            session.setRefreshHash(hash(next));
            session.setRotatedAt(now);
            DriverTokens pair = pair(session, next);
            storeReplay(session, pair, now.plus(ROTATION_GRACE));
            sessions.save(session);
            return pair;
        }
        boolean graceReuse = session.getPreviousRefreshHash() != null
                && MessageDigest.isEqual(ascii(presented), ascii(session.getPreviousRefreshHash()))
                && session.getRotatedAt() != null && session.getRotatedAt().plus(ROTATION_GRACE).isAfter(now)
                && session.getReplayCipher() != null && session.getReplayExpiresAt() != null && session.getReplayExpiresAt().isAfter(now);
        if (graceReuse) return readReplay(session);
        throw sessionEnded();
    }

    /** Revokes at contract first (so access tokens and GPS stop at once), then the refresh state here. */
    public void logout(UUID sessionId) {
        contract.revoke(sessionId, "LOGOUT");
        revokeLocally(sessionId);
    }

    private void revokeLocally(UUID sessionId) {
        tx.executeWithoutResult(status -> sessions.lockById(sessionId).ifPresent(session -> {
            if (session.getRevokedAt() == null) session.setRevokedAt(clock.instant());
            session.setReplayCipher(null);
            session.setReplayExpiresAt(null);
            sessions.save(session);
        }));
    }

    private DriverTokens pair(DriverSession session, String refreshSecret) {
        String access = tokens.generateDriverAccessToken(session);
        var view = new DriverSessionView("DRIVER_ASSIGNMENT", session.getId(), "DRIVER", session.getTripId(),
                session.getDriverProfileId(), session.getGrantId(), session.getAssignmentVersion());
        return new DriverTokens(access, session.getId() + "." + refreshSecret, tokens.accessTtlSeconds(), session.getExpiresAt(), view);
    }

    private void storeReplay(DriverSession session, DriverTokens pair, Instant until) {
        try {
            session.setReplayCipher(replay.encrypt(json.writeValueAsString(pair), session.getId().toString()));
            session.setReplayExpiresAt(until);
        } catch (Exception failure) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Không lưu được phiên đăng nhập.");
        }
    }

    private DriverTokens readReplay(DriverSession session) {
        try {
            return json.readValue(replay.decrypt(session.getReplayCipher(), session.getId().toString()), DriverTokens.class);
        } catch (Exception failure) {
            throw sessionEnded();
        }
    }

    /** Same shape as contract's code: UUIDv7 + "." + 43 base64url characters (case-sensitive). */
    static Optional<Code> parse(String raw) {
        if (raw == null) return Optional.empty();
        String value = raw.strip();
        int dot = value.indexOf('.');
        if (dot < 0 || value.indexOf('.', dot + 1) >= 0) return Optional.empty();
        String id = value.substring(0, dot), secret = value.substring(dot + 1);
        if (!UUID_TEXT.matcher(id).matches() || !SECRET.matcher(secret).matches()) return Optional.empty();
        UUID parsed = UUID.fromString(id.toLowerCase(Locale.ROOT));
        if (parsed.version() != 7 || parsed.variant() != 2) return Optional.empty();
        return Optional.of(new Code(parsed, secret));
    }

    private String newSecret() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static String hash(String secret) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(secret.getBytes(StandardCharsets.US_ASCII)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static byte[] ascii(String value) {
        return value == null ? new byte[0] : value.getBytes(StandardCharsets.US_ASCII);
    }

    private static ResponseStatusException sessionEnded() {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Phiên tài xế đã kết thúc. Nhập mã nhận chuyến mới từ chủ xe.");
    }
}
