package iuh.fit.se.security;

import iuh.fit.se.domain.entity.Account;
import iuh.fit.se.domain.enums.AccountRole;
import iuh.fit.se.repository.RefreshTokenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;
import iuh.fit.se.domain.entity.RefreshToken;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JwtTokenProviderGoogleTest {

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    private JwtTokenProvider provider;

    @BeforeEach
    void setUp() {
        provider = new JwtTokenProvider(refreshTokenRepository);
        String rawSecret = "backhaulbid-unit-test-hmac-secret-key-at-least-32-bytes";
        ReflectionTestUtils.setField(
                provider,
                "jwtSecret",
                Base64.getEncoder().encodeToString(rawSecret.getBytes(StandardCharsets.UTF_8)));
        ReflectionTestUtils.setField(provider, "jwtExpirationMs", 900_000L);
    }

    @Test
    void googleOnboardingTokenRoundTripPreservesClaims() {
        GoogleUserInfo expected = new GoogleUserInfo(
                "google-sub-123",
                "user@gmail.com",
                "Tran Van C",
                "https://lh3.googleusercontent.com/avatar");

        String token = provider.generateGoogleOnboardingToken(expected);
        GoogleUserInfo parsed = provider.parseGoogleOnboardingToken(token);

        assertThat(parsed).isEqualTo(expected);
    }

    @Test
    void parseGoogleOnboardingTokenRejectsAccessTokenAndTamperedToken() {
        Account account = Account.builder()
                .id(UUID.randomUUID())
                .phone("0901234567")
                .role(AccountRole.SHIPPER)
                .build();
        String accessToken = provider.generateAccessToken(account);

        assertThatThrownBy(() -> provider.parseGoogleOnboardingToken(accessToken))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);

        assertThatThrownBy(() -> provider.parseGoogleOnboardingToken("tampered.jwt.token"))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void validateRefreshTokenThrowsUnauthorizedWhenMissingOrExpired() {
        when(refreshTokenRepository.findByToken("missing")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> provider.validateRefreshToken("missing"))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);

        RefreshToken expired = new RefreshToken();
        expired.setToken("expired");
        expired.setExpiryDate(Instant.now().minusSeconds(5));
        when(refreshTokenRepository.findByToken("expired")).thenReturn(Optional.of(expired));

        assertThatThrownBy(() -> provider.validateRefreshToken("expired"))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
        verify(refreshTokenRepository).delete(expired);
    }
}
