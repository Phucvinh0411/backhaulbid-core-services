package iuh.fit.se.web.controller;

import iuh.fit.se.domain.dto.request.SendEmailOtpRequest;
import iuh.fit.se.domain.dto.request.UpdateAvatarRequest;
import iuh.fit.se.domain.dto.request.UpdateEmailRequest;
import iuh.fit.se.domain.dto.request.UpdatePasswordRequest;
import iuh.fit.se.domain.dto.response.CurrentAccountResponse;
import iuh.fit.se.domain.dto.response.SendEmailOtpResponse;
import iuh.fit.se.domain.entity.Account;
import iuh.fit.se.domain.entity.UserProfile;
import iuh.fit.se.domain.enums.AuthProvider;
import iuh.fit.se.mapper.AccountMapper;
import iuh.fit.se.repository.AccountRepository;
import iuh.fit.se.service.EmailChangeOtpService;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/accounts")
@RequiredArgsConstructor
public class AccountController {
    private final AccountRepository accountRepository;
    private final AccountMapper accountMapper;
    private final EmailChangeOtpService emailChangeOtpService;
    private final PasswordEncoder passwordEncoder;

    @GetMapping("/me")
    public CurrentAccountResponse me(Authentication authentication) {
        UUID accountId = UUID.fromString(authentication.getName());
        Account account = accountRepository.findWithUserProfileById(accountId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found"));
        return accountMapper.toCurrentAccountResponse(account);
    }

    @PatchMapping("/me/avatar")
    @Transactional
    public CurrentAccountResponse updateAvatar(
            Authentication authentication,
            @Valid @RequestBody UpdateAvatarRequest request) {
        UUID accountId = UUID.fromString(authentication.getName());
        Account account = accountRepository.findWithUserProfileById(accountId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found"));
        UserProfile profile = account.getUserProfile();
        if (profile == null) {
            profile = UserProfile.builder().account(account).build();
            account.setUserProfile(profile);
        }
        profile.setAvatarUrl(request.avatarUrl());
        accountRepository.save(account);
        return accountMapper.toCurrentAccountResponse(account);
    }

    @PostMapping("/me/email/otp")
    @Transactional
    public SendEmailOtpResponse requestEmailChangeOtp(
            Authentication authentication,
            @Valid @RequestBody SendEmailOtpRequest request) {
        UUID accountId = UUID.fromString(authentication.getName());
        Account account = accountRepository.findWithUserProfileById(accountId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found"));
        if (isGoogleManaged(account)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Tài khoản đã liên kết Google nên không thể thay đổi email đăng nhập");
        }
        String normalized = request.email().trim().toLowerCase(Locale.ROOT);
        if (normalized.equalsIgnoreCase(account.getEmail())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Email mới phải khác email hiện tại");
        }
        if (accountRepository.existsByEmailIgnoreCaseAndIdNot(normalized, accountId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email đã được sử dụng bởi tài khoản khác");
        }
        String fullName = account.getUserProfile() != null ? account.getUserProfile().getFullName() : null;
        return emailChangeOtpService.sendOtp(accountId, normalized, fullName);
    }

    @PatchMapping("/me/email")
    @Transactional
    public CurrentAccountResponse updateEmail(
            Authentication authentication,
            @Valid @RequestBody UpdateEmailRequest request) {
        UUID accountId = UUID.fromString(authentication.getName());
        Account account = accountRepository.findWithUserProfileById(accountId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found"));
        if (isGoogleManaged(account)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Tài khoản đã liên kết Google nên không thể thay đổi email đăng nhập");
        }
        String normalized = request.email().trim().toLowerCase(Locale.ROOT);
        if (normalized.equalsIgnoreCase(account.getEmail())) {
            return accountMapper.toCurrentAccountResponse(account);
        }
        if (accountRepository.existsByEmailIgnoreCaseAndIdNot(normalized, accountId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email đã được sử dụng bởi tài khoản khác");
        }
        emailChangeOtpService.verifyAndConsumeOtp(accountId, normalized, request.otp());
        account.setEmail(normalized);
        account.setEmailVerified(true);
        accountRepository.save(account);
        return accountMapper.toCurrentAccountResponse(account);
    }

    @PatchMapping("/me/password")
    @Transactional
    public Map<String, String> updatePassword(
            Authentication authentication,
            @Valid @RequestBody UpdatePasswordRequest request) {
        UUID accountId = UUID.fromString(authentication.getName());
        Account account = accountRepository.findWithUserProfileById(accountId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found"));
        if (account.getPasswordHash() == null || isGoogleManaged(account)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Tài khoản đăng nhập qua Google không hỗ trợ đổi mật khẩu tại đây");
        }
        if (!passwordEncoder.matches(request.currentPassword(), account.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Mật khẩu hiện tại không chính xác");
        }
        if (passwordEncoder.matches(request.newPassword(), account.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Mật khẩu mới phải khác mật khẩu hiện tại");
        }
        account.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        accountRepository.save(account);
        return Map.of("message", "Đã cập nhật mật khẩu thành công.");
    }

    private static boolean isGoogleManaged(Account account) {
        return account.getGoogleSub() != null || account.getAuthProvider() == AuthProvider.GOOGLE;
    }
}
