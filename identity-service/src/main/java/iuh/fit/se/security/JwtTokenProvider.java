package iuh.fit.se.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import iuh.fit.se.domain.entity.Account;
import iuh.fit.se.domain.entity.RefreshToken;
import iuh.fit.se.repository.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.security.Key;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtTokenProvider {

    @Value("${app.jwt.secret}")
    private String jwtSecret;

    @Value("${app.jwt.access-expiration-ms:900000}") // 15 minutes = 900000 ms
    private long jwtExpirationMs;

    @Value("${app.jwt.refresh-expiration-ms:604800000}") // 7 days = 604800000 ms
    private long refreshTokenDurationMs;

    private final RefreshTokenRepository refreshTokenRepository;

    private Key getSigningKey() {
        byte[] keyBytes = Decoders.BASE64.decode(jwtSecret);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    // Tạo Access Token
    public String generateAccessToken(Account account) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + jwtExpirationMs);

        return Jwts.builder()
                .setSubject(account.getId().toString())
                .claim("phone", account.getPhone())
                .claim("role", account.getRole().name())
                .setIssuedAt(now)
                .setExpiration(expiryDate)
                .signWith(getSigningKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    /**
     * Driver-session access token: subject is the session (not an account), scoped to one trip, profile and
     * assignment version. The gateway refuses it outside the driver allowlist; contract re-checks it.
     */
    public String generateDriverAccessToken(iuh.fit.se.domain.entity.DriverSession session) {
        Date now = new Date();
        return Jwts.builder()
                .setSubject(session.getId().toString())
                .setIssuer("backhaulbid-identity")
                .setAudience("backhaulbid-driver")
                .claim("role", "DRIVER")
                .claim("auth_type", "DRIVER_ASSIGNMENT")
                .claim("trip_id", session.getTripId().toString())
                .claim("driver_profile_id", session.getDriverProfileId().toString())
                .claim("assignment_id", session.getGrantId().toString())
                .claim("assignment_version", session.getAssignmentVersion())
                .setIssuedAt(now)
                .setExpiration(new Date(now.getTime() + jwtExpirationMs))
                .signWith(getSigningKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    public long accessTtlSeconds() {
        return jwtExpirationMs / 1000;
    }

    public String generateGoogleOnboardingToken(GoogleUserInfo googleUser) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + 15 * 60 * 1000L);
        return Jwts.builder()
                .setSubject(googleUser.sub())
                .setIssuer("backhaulbid-identity")
                .setAudience("backhaulbid-google-onboarding")
                .claim("purpose", "GOOGLE_REGISTER")
                .claim("email", googleUser.email())
                .claim("name", googleUser.fullName())
                .claim("picture", googleUser.avatarUrl())
                .setIssuedAt(now)
                .setExpiration(expiryDate)
                .signWith(getSigningKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    public GoogleUserInfo parseGoogleOnboardingToken(String token) {
        try {
            Claims claims = Jwts.parserBuilder()
                    .setSigningKey(getSigningKey())
                    .requireIssuer("backhaulbid-identity")
                    .requireAudience("backhaulbid-google-onboarding")
                    .build()
                    .parseClaimsJws(token)
                    .getBody();
            if (!"GOOGLE_REGISTER".equals(claims.get("purpose", String.class))) {
                throw new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.UNAUTHORIZED,
                        "Phiên đăng ký Google không hợp lệ");
            }
            String sub = claims.getSubject();
            String email = claims.get("email", String.class);
            if (sub == null || sub.isBlank() || email == null || email.isBlank()) {
                throw new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.UNAUTHORIZED,
                        "Phiên đăng ký Google không hợp lệ");
            }
            return new GoogleUserInfo(
                    sub,
                    email,
                    claims.get("name", String.class),
                    claims.get("picture", String.class));
        } catch (JwtException | IllegalArgumentException ex) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.UNAUTHORIZED,
                    "Phiên đăng ký Google không hợp lệ hoặc đã hết hạn");
        }
    }

    // Tạo Refresh Token và lưu Database
    public RefreshToken generateRefreshToken(Account account) {
        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setAccount(account);
        refreshToken.setExpiryDate(Instant.now().plusMillis(refreshTokenDurationMs));
        refreshToken.setToken(UUID.randomUUID().toString());

        return refreshTokenRepository.save(refreshToken);
    }

    public RefreshToken validateRefreshToken(String token) {
        RefreshToken refreshToken = refreshTokenRepository.findByToken(token)
                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.UNAUTHORIZED, "Refresh token không tồn tại!"));

        if (refreshToken.getExpiryDate().compareTo(Instant.now()) < 0) {
            refreshTokenRepository.delete(refreshToken);
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.UNAUTHORIZED,
                    "Refresh token đã hết hạn. Vui lòng đăng nhập lại!");
        }
        return refreshToken;
    }

    // Lấy ID (subject) từ JWT
    public String getAccountIdFromJWT(String token) {
        Claims claims = Jwts.parserBuilder()
                .setSigningKey(getSigningKey())
                .build()
                .parseClaimsJws(token)
                .getBody();

        return claims.getSubject();
    }

    // Lấy Role từ JWT
    public String getRoleFromJWT(String token) {
        Claims claims = Jwts.parserBuilder()
                .setSigningKey(getSigningKey())
                .build()
                .parseClaimsJws(token)
                .getBody();

        return claims.get("role", String.class);
    }

    // Validate Token
    public boolean validateToken(String authToken) {
        try {
            Jwts.parserBuilder().setSigningKey(getSigningKey()).build().parseClaimsJws(authToken);
            return true;
        } catch (SecurityException | MalformedJwtException ex) {
            log.error("Invalid JWT signature");
        } catch (ExpiredJwtException ex) {
            log.error("Expired JWT token");
        } catch (UnsupportedJwtException ex) {
            log.error("Unsupported JWT token");
        } catch (IllegalArgumentException ex) {
            log.error("JWT claims string is empty.");
        }
        return false;
    }
}
