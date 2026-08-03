package iuh.fit.se.service;

import iuh.fit.se.domain.dto.request.LoginRequest;
import iuh.fit.se.domain.dto.request.RegisterRequest;
import iuh.fit.se.domain.dto.response.AuthResponse;
import iuh.fit.se.domain.entity.Account;
import iuh.fit.se.domain.entity.RefreshToken;
import iuh.fit.se.domain.enums.AccountRole;
import iuh.fit.se.domain.enums.AccountStatus;
import iuh.fit.se.repository.AccountRepository;
import iuh.fit.se.security.JwtTokenProvider;
import iuh.fit.se.mapper.AuthMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mapstruct.factory.Mappers;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private static final UUID ACCOUNT_ID = UUID.fromString("8f14e45f-ea43-4a4f-b716-3f8f68f74201");

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Spy
    private AuthMapper authMapper = Mappers.getMapper(AuthMapper.class);

    @InjectMocks
    private AuthService authService;

    @Test
    void register_newPhone_returnsAccountId() {
        // Given
        RegisterRequest request = new RegisterRequest();
        request.setPhone("0901234567");
        request.setEmail("carrier@example.com");
        request.setPassword("Password123");
        request.setRole(AccountRole.CARRIER);
        Account savedAccount = account(AccountStatus.ACTIVE);
        RefreshToken refreshToken = RefreshToken.builder()
                .token("refresh-token")
                .account(savedAccount)
                .build();
        ArgumentCaptor<Account> accountCaptor = ArgumentCaptor.forClass(Account.class);
        when(accountRepository.existsByPhone("0901234567")).thenReturn(false);
        when(passwordEncoder.encode("Password123")).thenReturn("encoded-password");
        when(accountRepository.save(accountCaptor.capture())).thenReturn(savedAccount);
        when(jwtTokenProvider.generateAccessToken(savedAccount)).thenReturn("access-token");
        when(jwtTokenProvider.generateRefreshToken(savedAccount)).thenReturn(refreshToken);

        // When
        AuthResponse actualResponse = authService.register(request);

        // Then
        assertThat(actualResponse.getAccountId()).isEqualTo(ACCOUNT_ID);
        assertThat(actualResponse.getAccessToken()).isEqualTo("access-token");
        assertThat(accountCaptor.getValue().getPhone()).isEqualTo("0901234567");
        assertThat(accountCaptor.getValue().getPasswordHash()).isEqualTo("encoded-password");
    }

    @Test
    void register_existingPhone_throwsException() {
        // Given
        RegisterRequest request = new RegisterRequest();
        request.setPhone("0901234567");
        when(accountRepository.existsByPhone("0901234567")).thenReturn(true);

        // When-Then
        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Phone number already exists");
        verify(accountRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void login_validCredentials_returnsAccountId() {
        // Given
        LoginRequest request = loginRequest();
        Account account = account(AccountStatus.ACTIVE);
        RefreshToken refreshToken = RefreshToken.builder()
                .token("refresh-token")
                .account(account)
                .build();
        when(accountRepository.findByPhone("0901234567")).thenReturn(Optional.of(account));
        when(passwordEncoder.matches("Password123", "encoded-password")).thenReturn(true);
        when(jwtTokenProvider.generateAccessToken(account)).thenReturn("access-token");
        when(jwtTokenProvider.generateRefreshToken(account)).thenReturn(refreshToken);

        // When
        AuthResponse actualResponse = authService.login(request);

        // Then
        assertThat(actualResponse.getAccountId()).isEqualTo(ACCOUNT_ID);
        assertThat(actualResponse.getPhone()).isEqualTo("0901234567");
        assertThat(actualResponse.getRole()).isEqualTo("CARRIER");
    }

    @Test
    void login_unknownPhone_throwsException() {
        // Given
        LoginRequest request = loginRequest();
        when(accountRepository.findByPhone("0901234567")).thenReturn(Optional.empty());

        // When-Then
        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("User not found");
    }

    @Test
    void login_invalidPassword_throwsException() {
        // Given
        LoginRequest request = loginRequest();
        Account account = account(AccountStatus.ACTIVE);
        when(accountRepository.findByPhone("0901234567")).thenReturn(Optional.of(account));
        when(passwordEncoder.matches("Password123", "encoded-password")).thenReturn(false);

        // When-Then
        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Invalid password");
    }

    @Test
    void login_inactiveAccount_throwsException() {
        // Given
        LoginRequest request = loginRequest();
        Account account = account(AccountStatus.INACTIVE);
        when(accountRepository.findByPhone("0901234567")).thenReturn(Optional.of(account));
        when(passwordEncoder.matches("Password123", "encoded-password")).thenReturn(true);

        // When-Then
        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Account is not active");
    }

    @Test
    void refresh_validToken_returnsAccountId() {
        // Given
        Account account = account(AccountStatus.ACTIVE);
        RefreshToken refreshToken = RefreshToken.builder()
                .token("refresh-token")
                .account(account)
                .build();
        when(jwtTokenProvider.validateRefreshToken("refresh-token")).thenReturn(refreshToken);
        when(jwtTokenProvider.generateAccessToken(account)).thenReturn("new-access-token");

        // When
        AuthResponse actualResponse = authService.refresh("refresh-token");

        // Then
        assertThat(actualResponse.getAccountId()).isEqualTo(ACCOUNT_ID);
        assertThat(actualResponse.getAccessToken()).isEqualTo("new-access-token");
        assertThat(actualResponse.getRefreshToken()).isEqualTo("refresh-token");
    }

    @Test
    void refresh_invalidToken_propagatesException() {
        // Given
        when(jwtTokenProvider.validateRefreshToken("invalid-token"))
                .thenThrow(new RuntimeException("Refresh token does not exist"));

        // When-Then
        assertThatThrownBy(() -> authService.refresh("invalid-token"))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Refresh token does not exist");
    }

    private Account account(AccountStatus status) {
        return Account.builder()
                .id(ACCOUNT_ID)
                .phone("0901234567")
                .email("carrier@example.com")
                .passwordHash("encoded-password")
                .role(AccountRole.CARRIER)
                .status(status)
                .build();
    }

    private LoginRequest loginRequest() {
        LoginRequest request = new LoginRequest();
        request.setPhone("0901234567");
        request.setPassword("Password123");
        return request;
    }
}
