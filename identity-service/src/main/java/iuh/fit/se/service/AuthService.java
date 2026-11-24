package iuh.fit.se.service;

import iuh.fit.se.domain.dto.request.CompleteGoogleRegistrationRequest;
import iuh.fit.se.domain.dto.request.GoogleLoginRequest;
import iuh.fit.se.domain.dto.request.LoginRequest;
import iuh.fit.se.domain.dto.request.RegisterRequest;
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
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import iuh.fit.se.domain.dto.response.UserMeResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final AccountRepository accountRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final AuthMapper authMapper;
    private final GoogleTokenVerifier googleTokenVerifier;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (request.getRole()==null || !Set.of(AccountRole.SHIPPER,
                AccountRole.CARRIER, AccountRole.DRIVER).contains(request.getRole()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Public registration requires SHIPPER, CARRIER or DRIVER");
        if (accountRepository.existsByPhone(request.getPhone())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Số điện thoại này đã được đăng ký");
        }
        String email = request.getEmail() == null || request.getEmail().isBlank()
                ? null
                : request.getEmail().trim().toLowerCase(Locale.ROOT);
        if (email != null && accountRepository.existsByEmailIgnoreCase(email)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Email này đã được sử dụng");
        }

        Account account = Account.builder()
                .phone(request.getPhone())
                .email(email)
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .role(request.getRole())
                .status(AccountStatus.ACTIVE)
                .build();

        account = accountRepository.save(account);

        return issueAuthResponse(account);
    }

    public AuthResponse login(LoginRequest request) {
        Account account = accountRepository.findByPhone(request.getPhone())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));

        if (account.getPasswordHash() == null) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Tài khoản này đăng nhập bằng Google, vui lòng dùng Đăng nhập với Google");
        }
        if (!passwordEncoder.matches(request.getPassword(), account.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid password");
        }

        if (account.getStatus() != AccountStatus.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Account is not active");
        }

        return issueAuthResponse(account);
    }

    @Transactional
    public GoogleAuthResponse loginWithGoogle(GoogleLoginRequest request) {
        GoogleUserInfo googleUser = googleTokenVerifier.verify(request.idToken());
        Optional<Account> existing = accountRepository.findByGoogleSub(googleUser.sub());
        boolean matchedByGoogleSub = existing.isPresent();
        if (existing.isEmpty()) {
            existing = accountRepository.findByEmailIgnoreCase(googleUser.email());
        }
        if (existing.isEmpty()) {
            String onboardingToken = jwtTokenProvider.generateGoogleOnboardingToken(googleUser);
            return GoogleAuthResponse.onboarding(
                    onboardingToken,
                    googleUser.email(),
                    googleUser.fullName(),
                    googleUser.avatarUrl());
        }

        Account account = existing.get();
        if (account.getRole() == AccountRole.ADMIN || account.getRole() == AccountRole.DRIVER) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Tài khoản với vai trò này không hỗ trợ đăng nhập bằng Google");
        }
        if (account.getStatus() != AccountStatus.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Account is not active");
        }
        if (account.getGoogleSub() != null && !account.getGoogleSub().equals(googleUser.sub())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Email đã được liên kết với một tài khoản Google khác");
        }
        if (!matchedByGoogleSub && !account.isEmailVerified()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Email này đã được đăng ký bằng số điện thoại. Vui lòng đăng nhập bằng số điện thoại và mật khẩu.");
        }
        boolean dirty = false;
        if (account.getGoogleSub() == null) {
            account.setGoogleSub(googleUser.sub());
            dirty = true;
        }
        if (!account.isEmailVerified()) {
            account.setEmailVerified(true);
            dirty = true;
        }
        if (account.getUserProfile() == null
                && (googleUser.fullName() != null || googleUser.avatarUrl() != null)) {
            account.setUserProfile(UserProfile.builder()
                    .account(account)
                    .fullName(googleUser.fullName())
                    .avatarUrl(googleUser.avatarUrl())
                    .build());
            dirty = true;
        }
        if (dirty) {
            account = accountRepository.save(account);
        }
        return GoogleAuthResponse.authenticated(issueAuthResponse(account));
    }

    @Transactional
    public AuthResponse completeGoogleRegistration(CompleteGoogleRegistrationRequest request) {
        if (request.role() != AccountRole.SHIPPER && request.role() != AccountRole.CARRIER) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Đăng ký qua Google chỉ áp dụng cho Chủ hàng (SHIPPER) hoặc Chủ xe (CARRIER)");
        }
        GoogleUserInfo googleUser = jwtTokenProvider.parseGoogleOnboardingToken(request.onboardingToken());
        String phone = request.phone().trim();
        if (accountRepository.findByGoogleSub(googleUser.sub()).isPresent()
                || accountRepository.existsByEmailIgnoreCase(googleUser.email())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Tài khoản Google này đã được đăng ký");
        }
        if (accountRepository.existsByPhone(phone)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Số điện thoại đã được sử dụng");
        }

        Account account = Account.builder()
                .phone(phone)
                .email(googleUser.email())
                .googleSub(googleUser.sub())
                .authProvider(AuthProvider.GOOGLE)
                .emailVerified(true)
                .role(request.role())
                .status(AccountStatus.ACTIVE)
                .build();
        if (googleUser.fullName() != null || googleUser.avatarUrl() != null) {
            account.setUserProfile(UserProfile.builder()
                    .account(account)
                    .fullName(googleUser.fullName())
                    .avatarUrl(googleUser.avatarUrl())
                    .build());
        }
        account = accountRepository.save(account);
        return issueAuthResponse(account);
    }

    private AuthResponse issueAuthResponse(Account account) {
        String accessToken = jwtTokenProvider.generateAccessToken(account);
        RefreshToken refreshToken = jwtTokenProvider.generateRefreshToken(account);
        return authMapper.toResponse(account, accessToken, refreshToken.getToken());
    }

    public AuthResponse refresh(String token) {
        RefreshToken refreshToken = jwtTokenProvider.validateRefreshToken(token);
        Account account = refreshToken.getAccount();
        if (account.getStatus() != AccountStatus.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Tài khoản đã bị khóa hoặc ngừng hoạt động");
        }

        String newAccessToken = jwtTokenProvider.generateAccessToken(account);
        return authMapper.toResponse(account, newAccessToken, refreshToken.getToken());
    }

    @Transactional(readOnly = true)
    public UserMeResponse getMe(String identifier) {
        Account account;
        try {
            UUID accountId = UUID.fromString(identifier);
            account = accountRepository.findById(accountId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Account not found: " + identifier));
        } catch (IllegalArgumentException e) {
            account = accountRepository.findByPhone(identifier)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Account not found: " + identifier));
        }

        String fullName = account.getUserProfile() != null ? account.getUserProfile().getFullName() : null;
        String avatarUrl = account.getUserProfile() != null ? account.getUserProfile().getAvatarUrl() : null;
        String address = account.getUserProfile() != null ? account.getUserProfile().getAddress() : null;
        String companyName = account.getCompany() != null ? account.getCompany().getCompanyName() : null;

        return UserMeResponse.builder()
                .accountId(account.getId())
                .phone(account.getPhone())
                .email(account.getEmail())
                .role(account.getRole() != null ? account.getRole().name() : null)
                .fullName(fullName)
                .avatarUrl(avatarUrl)
                .address(address)
                .companyName(companyName)
                .build();
    }
}
