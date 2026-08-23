package iuh.fit.se.web.controller;

import iuh.fit.se.domain.dto.response.CurrentAccountResponse;
import iuh.fit.se.domain.entity.Account;
import iuh.fit.se.repository.AccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

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
        String fullName = account.getUserProfile() == null ? null : account.getUserProfile().getFullName();
        String avatarUrl = account.getUserProfile() == null ? null : account.getUserProfile().getAvatarUrl();
        return new CurrentAccountResponse(
                account.getId(),
                account.getPhone(),
                account.getEmail(),
                account.getRole().name(),
                fullName,
                avatarUrl);
    }
}
