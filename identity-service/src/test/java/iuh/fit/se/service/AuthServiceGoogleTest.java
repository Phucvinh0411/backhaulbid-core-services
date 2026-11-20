package iuh.fit.se.service;

import iuh.fit.se.domain.dto.request.CompleteGoogleRegistrationRequest;
import iuh.fit.se.domain.dto.request.GoogleLoginRequest;
import iuh.fit.se.domain.dto.request.LoginRequest;
import iuh.fit.se.domain.dto.response.AuthResponse;
import iuh.fit.se.domain.dto.response.GoogleAuthResponse;
import iuh.fit.se.domain.entity.Account;
import iuh.fit.se.domain.entity.RefreshToken;
import iuh.fit.se.domain.entity.UserProfile;
import iuh.fit.se.domain.enums.AccountRole;
import iuh.fit.se.domain.enums.AccountStatus;
import iuh.fit.se.domain.enums.AuthProvider;
import iuh.fit.se.mapper.AuthMapper;
import iuh.fit.se.repository.AccountRepository;
import iuh.fit.se.security.GoogleTokenVerifier;
import iuh.fit.se.security.GoogleUserInfo;
import iuh.fit.se.security.JwtTokenProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceGoogleTest {

    @Mock
    private AccountRepository accountRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private JwtTokenProvider jwtTokenProvider;
    @Mock
    private AuthMapper authMapper;
    @Mock
    private GoogleTokenVerifier googleTokenVerifier;

    @InjectMocks
    private AuthService authService;

    @Test
    void loginWithGoogleReturnsAuthenticatedWhenExistingGoogleSubMatches() {
        UUID accountId = UUID.randomUUID();
        GoogleUserInfo userInfo = new GoogleUserInfo("sub-1", "shipper@gmail.com", "Nguyen Van A", "https://lh3.googleusercontent.com/a");
        Account account = Account.builder()
                .id(accountId)
                .phone("0912345678")
                .email("shipper@gmail.com")
                .googleSub("sub-1")
                .authProvider(AuthProvider.GOOGLE)
                .emailVerified(true)
                .role(AccountRole.SHIPPER)
                .status(AccountStatus.ACTIVE)
                .build();
        account.setUserProfile(UserProfile.builder().account(account).fullName("Nguyen Van A").build());
        RefreshToken refreshToken = RefreshToken.builder().token("refresh-1").account(account).build();
        AuthResponse expectedAuth = AuthResponse.builder()
                .accountId(accountId)
                .accessToken("access-1")
                .refreshToken("refresh-1")
                .phone("0912345678")
                .role("SHIPPER")
                .build();

        when(googleTokenVerifier.verify("google-id-token")).thenReturn(userInfo);
        when(accountRepository.findByGoogleSub("sub-1")).thenReturn(Optional.of(account));
        when(jwtTokenProvider.generateAccessToken(account)).thenReturn("access-1");
        when(jwtTokenProvider.generateRefreshToken(account)).thenReturn(refreshToken);
        when(authMapper.toResponse(account, "access-1", "refresh-1")).thenReturn(expectedAuth);

        GoogleAuthResponse response = authService.loginWithGoogle(new GoogleLoginRequest("google-id-token"));

        assertThat(response.needsOnboarding()).isFalse();
        assertThat(response.auth()).isEqualTo(expectedAuth);
    }

    @Test
    void loginWithGoogleRejectsUnverifiedLocalAccountMatchedByEmail() {
        UUID accountId = UUID.randomUUID();
        GoogleUserInfo userInfo = new GoogleUserInfo("sub-2", "carrier@gmail.com", "Carrier Co", null);
        Account account = Account.builder()
                .id(accountId)
                .phone("0988776655")
                .email("carrier@gmail.com")
                .passwordHash("hashed")
                .authProvider(AuthProvider.LOCAL)
                .emailVerified(false)
                .role(AccountRole.CARRIER)
                .status(AccountStatus.ACTIVE)
                .build();

        when(googleTokenVerifier.verify("id-tok")).thenReturn(userInfo);
        when(accountRepository.findByGoogleSub("sub-2")).thenReturn(Optional.empty());
        when(accountRepository.findByEmailIgnoreCase("carrier@gmail.com")).thenReturn(Optional.of(account));

        assertThatThrownBy(() -> authService.loginWithGoogle(new GoogleLoginRequest("id-tok")))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException rse = (ResponseStatusException) ex;
                    assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(rse.getReason()).isEqualTo(
                            "Email này đã được đăng ký bằng số điện thoại. Vui lòng đăng nhập bằng số điện thoại và mật khẩu.");
                });
        assertThat(account.getGoogleSub()).isNull();
    }

    @Test
    void loginWithGoogleLinksVerifiedLocalAccountMatchedByEmail() {
        UUID accountId = UUID.randomUUID();
        GoogleUserInfo userInfo = new GoogleUserInfo("sub-2", "carrier@gmail.com", "Carrier Co", null);
        Account account = Account.builder()
                .id(accountId)
                .phone("0988776655")
                .email("carrier@gmail.com")
                .passwordHash("hashed")
                .authProvider(AuthProvider.LOCAL)
                .emailVerified(true)
                .role(AccountRole.CARRIER)
                .status(AccountStatus.ACTIVE)
                .build();
        RefreshToken refreshToken = RefreshToken.builder().token("refresh-2").account(account).build();
        AuthResponse expectedAuth = AuthResponse.builder().accountId(accountId).accessToken("access-2").refreshToken("refresh-2").build();

        when(googleTokenVerifier.verify("id-tok")).thenReturn(userInfo);
        when(accountRepository.findByGoogleSub("sub-2")).thenReturn(Optional.empty());
        when(accountRepository.findByEmailIgnoreCase("carrier@gmail.com")).thenReturn(Optional.of(account));
        when(accountRepository.save(account)).thenReturn(account);
        when(jwtTokenProvider.generateAccessToken(account)).thenReturn("access-2");
        when(jwtTokenProvider.generateRefreshToken(account)).thenReturn(refreshToken);
        when(authMapper.toResponse(account, "access-2", "refresh-2")).thenReturn(expectedAuth);

        GoogleAuthResponse response = authService.loginWithGoogle(new GoogleLoginRequest("id-tok"));

        assertThat(response.needsOnboarding()).isFalse();
        assertThat(account.getGoogleSub()).isEqualTo("sub-2");
        verify(accountRepository).save(account);
    }

    @Test
    void refreshRejectsInactiveAccountWithForbidden() {
        Account locked = Account.builder()
                .id(UUID.randomUUID())
                .phone("0988776655")
                .role(AccountRole.CARRIER)
                .status(AccountStatus.BLOCKED)
                .build();
        RefreshToken refreshToken = RefreshToken.builder().token("ref-locked").account(locked).build();
        when(jwtTokenProvider.validateRefreshToken("ref-locked")).thenReturn(refreshToken);

        assertThatThrownBy(() -> authService.refresh("ref-locked"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException rse = (ResponseStatusException) ex;
                    assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
                    assertThat(rse.getReason()).isEqualTo("Tài khoản đã bị khóa hoặc ngừng hoạt động");
                });
    }

    @Test
    void loginWithGoogleRejectsAdminAccount() {
        GoogleUserInfo userInfo = new GoogleUserInfo("sub-admin", "admin@gmail.com", "Admin", null);
        Account admin = Account.builder()
                .id(UUID.randomUUID())
                .phone("0900000001")
                .email("admin@gmail.com")
                .role(AccountRole.ADMIN)
                .status(AccountStatus.ACTIVE)
                .build();

        when(googleTokenVerifier.verify("tok")).thenReturn(userInfo);
        when(accountRepository.findByGoogleSub("sub-admin")).thenReturn(Optional.empty());
        when(accountRepository.findByEmailIgnoreCase("admin@gmail.com")).thenReturn(Optional.of(admin));

        assertThatThrownBy(() -> authService.loginWithGoogle(new GoogleLoginRequest("tok")))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void loginWithGoogleReturnsOnboardingChallengeForNewUser() {
        GoogleUserInfo userInfo = new GoogleUserInfo("sub-new", "newuser@gmail.com", "New User", "https://lh3.googleusercontent.com/pic");
        when(googleTokenVerifier.verify("tok")).thenReturn(userInfo);
        when(accountRepository.findByGoogleSub("sub-new")).thenReturn(Optional.empty());
        when(accountRepository.findByEmailIgnoreCase("newuser@gmail.com")).thenReturn(Optional.empty());
        when(jwtTokenProvider.generateGoogleOnboardingToken(userInfo)).thenReturn("onboard-jwt");

        GoogleAuthResponse response = authService.loginWithGoogle(new GoogleLoginRequest("tok"));

        assertThat(response.needsOnboarding()).isTrue();
        assertThat(response.onboardingToken()).isEqualTo("onboard-jwt");
        assertThat(response.email()).isEqualTo("newuser@gmail.com");
        assertThat(response.fullName()).isEqualTo("New User");
        assertThat(response.auth()).isNull();
    }

    @Test
    void completeGoogleRegistrationCreatesShipperAccountAndProfile() {
        GoogleUserInfo userInfo = new GoogleUserInfo("sub-new", "newuser@gmail.com", "Nguyen Thi B", "https://lh3.googleusercontent.com/b");
        when(jwtTokenProvider.parseGoogleOnboardingToken("onboard-jwt")).thenReturn(userInfo);
        when(accountRepository.findByGoogleSub("sub-new")).thenReturn(Optional.empty());
        when(accountRepository.existsByEmailIgnoreCase("newuser@gmail.com")).thenReturn(false);
        when(accountRepository.existsByPhone("0911223344")).thenReturn(false);

        Account savedAccount = Account.builder()
                .id(UUID.randomUUID())
                .phone("0911223344")
                .email("newuser@gmail.com")
                .googleSub("sub-new")
                .authProvider(AuthProvider.GOOGLE)
                .emailVerified(true)
                .role(AccountRole.SHIPPER)
                .status(AccountStatus.ACTIVE)
                .build();
        RefreshToken refreshToken = RefreshToken.builder().token("ref-new").account(savedAccount).build();
        AuthResponse expectedAuth = AuthResponse.builder()
                .accountId(savedAccount.getId())
                .accessToken("acc-new")
                .refreshToken("ref-new")
                .phone("0911223344")
                .role("SHIPPER")
                .build();

        when(accountRepository.save(any(Account.class))).thenReturn(savedAccount);
        when(jwtTokenProvider.generateAccessToken(savedAccount)).thenReturn("acc-new");
        when(jwtTokenProvider.generateRefreshToken(savedAccount)).thenReturn(refreshToken);
        when(authMapper.toResponse(savedAccount, "acc-new", "ref-new")).thenReturn(expectedAuth);

        AuthResponse response = authService.completeGoogleRegistration(
                new CompleteGoogleRegistrationRequest("onboard-jwt", AccountRole.SHIPPER, "0911223344"));

        ArgumentCaptor<Account> captor = ArgumentCaptor.forClass(Account.class);
        verify(accountRepository).save(captor.capture());
        Account created = captor.getValue();
        assertThat(created.getPasswordHash()).isNull();
        assertThat(created.getAuthProvider()).isEqualTo(AuthProvider.GOOGLE);
        assertThat(created.isEmailVerified()).isTrue();
        assertThat(created.getUserProfile()).isNotNull();
        assertThat(created.getUserProfile().getFullName()).isEqualTo("Nguyen Thi B");
        assertThat(response).isEqualTo(expectedAuth);
    }

    @Test
    void completeGoogleRegistrationRejectsDuplicatePhoneAndInvalidRole() {
        assertThatThrownBy(() -> authService.completeGoogleRegistration(
                new CompleteGoogleRegistrationRequest("onboard-jwt", AccountRole.ADMIN, "0911223344")))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);

        GoogleUserInfo userInfo = new GoogleUserInfo("sub-new", "newuser@gmail.com", "B", null);
        when(jwtTokenProvider.parseGoogleOnboardingToken("onboard-jwt")).thenReturn(userInfo);
        when(accountRepository.findByGoogleSub("sub-new")).thenReturn(Optional.empty());
        when(accountRepository.existsByEmailIgnoreCase("newuser@gmail.com")).thenReturn(false);
        when(accountRepository.existsByPhone("0911223344")).thenReturn(true);

        assertThatThrownBy(() -> authService.completeGoogleRegistration(
                new CompleteGoogleRegistrationRequest("onboard-jwt", AccountRole.CARRIER, "0911223344")))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void passwordLoginRejectsGoogleOnlyAccountCleanly() {
        Account googleOnly = Account.builder()
                .id(UUID.randomUUID())
                .phone("0911223344")
                .passwordHash(null)
                .authProvider(AuthProvider.GOOGLE)
                .role(AccountRole.SHIPPER)
                .status(AccountStatus.ACTIVE)
                .build();
        when(accountRepository.findByPhone("0911223344")).thenReturn(Optional.of(googleOnly));

        LoginRequest request = new LoginRequest();
        request.setPhone("0911223344");
        request.setPassword("Password123!");

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
