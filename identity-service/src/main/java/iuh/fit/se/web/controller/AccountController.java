package iuh.fit.se.web.controller;

import iuh.fit.se.domain.dto.request.UpdateAvatarRequest;
import iuh.fit.se.domain.dto.request.UpdateEmailRequest;
import iuh.fit.se.domain.dto.response.CurrentAccountResponse;
import iuh.fit.se.domain.entity.Account;
import iuh.fit.se.domain.entity.UserProfile;
import iuh.fit.se.repository.AccountRepository;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Locale;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/accounts")
@RequiredArgsConstructor
public class AccountController {
    private final AccountRepository accountRepository;

    @GetMapping("/me")
    public CurrentAccountResponse me(Authentication authentication) {
        UUID accountId = UUID.fromString(authentication.getName());
        Account account = accountRepository.findWithUserProfileById(accountId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found"));
        return toResponse(account);
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
        return toResponse(account);
    }

    @PatchMapping("/me/email")
    @Transactional
    public CurrentAccountResponse updateEmail(
            Authentication authentication,
            @Valid @RequestBody UpdateEmailRequest request) {
        UUID accountId = UUID.fromString(authentication.getName());
        Account account = accountRepository.findWithUserProfileById(accountId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found"));
        if (account.getGoogleSub() != null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Tài khoản đã liên kết Google nên không thể thay đổi email đăng nhập");
        }
        String normalized = request.email().trim().toLowerCase(Locale.ROOT);
        if (normalized.equalsIgnoreCase(account.getEmail())) {
            return toResponse(account);
        }
        if (accountRepository.existsByEmailIgnoreCaseAndIdNot(normalized, accountId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email đã được sử dụng bởi tài khoản khác");
        }
        account.setEmail(normalized);
        account.setEmailVerified(false);
        accountRepository.save(account);
        return toResponse(account);
    }

    private CurrentAccountResponse toResponse(Account account) {
        String fullName = account.getUserProfile() == null ? null : account.getUserProfile().getFullName();
        String avatarUrl = account.getUserProfile() == null ? null : account.getUserProfile().getAvatarUrl();
        String provider = account.getAuthProvider() != null ? account.getAuthProvider().name() : "LOCAL";
        return new CurrentAccountResponse(
                account.getId(),
                account.getPhone(),
                account.getEmail(),
                account.getRole().name(),
                fullName,
                avatarUrl,
                account.isEmailVerified(),
                provider);
    }
}
