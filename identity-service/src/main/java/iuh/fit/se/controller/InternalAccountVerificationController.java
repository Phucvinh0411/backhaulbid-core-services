package iuh.fit.se.controller;

import iuh.fit.se.domain.entity.Account;
import iuh.fit.se.domain.enums.VerificationStatus;
import iuh.fit.se.repository.AccountRepository;
import iuh.fit.se.repository.CompanyRepository;
import iuh.fit.se.repository.EkycVerificationRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.UUID;

/**
 * Service-to-service verification check for eKYC and business verification.
 * Guarded by the shared internal token so downstream services can fail closed.
 */
@RestController
@RequestMapping("/internal/accounts")
public class InternalAccountVerificationController {

    public record VerificationEligibility(
            boolean ekycVerified,
            boolean businessVerified,
            boolean fullyVerified) {
    }

    public record SigningContact(
            String email,
            boolean emailVerified,
            String fullName,
            String companyName) {
        public SigningContact(String email, boolean emailVerified, String fullName) {
            this(email, emailVerified, fullName, null);
        }
    }

    private final EkycVerificationRepository ekycRepository;
    private final CompanyRepository companyRepository;
    private final AccountRepository accountRepository;
    private final String expectedToken;

    public InternalAccountVerificationController(
            EkycVerificationRepository ekycRepository,
            CompanyRepository companyRepository,
            AccountRepository accountRepository,
            @Value("${app.internal-token:}") String expectedToken) {
        this.ekycRepository = ekycRepository;
        this.companyRepository = companyRepository;
        this.accountRepository = accountRepository;
        this.expectedToken = expectedToken;
    }

    @GetMapping("/{accountId}/verification-status")
    public VerificationEligibility verificationStatus(
            @PathVariable UUID accountId,
            @RequestHeader(value = "X-Internal-Token", required = false) String token) {
        authorize(token);
        boolean ekycVerified = ekycRepository.findByAccount_Id(accountId)
                .map(v -> v.getStatus() == VerificationStatus.VERIFIED)
                .orElse(false);
        boolean businessVerified = companyRepository.findByAccount_Id(accountId)
                .map(c -> c.getVerificationStatus() == VerificationStatus.VERIFIED)
                .orElse(false);
        return new VerificationEligibility(
                ekycVerified,
                businessVerified,
                ekycVerified && businessVerified);
    }

    @GetMapping("/{accountId}/signing-contact")
    public SigningContact signingContact(
            @PathVariable UUID accountId,
            @RequestHeader(value = "X-Internal-Token", required = false) String token) {
        authorize(token);
        Account account = accountRepository.findWithUserProfileById(accountId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found"));
        String email = account.getEmail() == null || account.getEmail().isBlank() ? null : account.getEmail().trim();
        String fullName = account.getUserProfile() != null ? account.getUserProfile().getFullName() : null;
        String companyName = companyRepository.findByAccount_Id(accountId)
                .map(c -> c.getCompanyName() == null || c.getCompanyName().isBlank() ? null : c.getCompanyName().trim())
                .orElse(null);
        return new SigningContact(email, account.isEmailVerified(), fullName, companyName);
    }

    private void authorize(String token) {
        if (expectedToken == null || expectedToken.isBlank() || token == null
                || !MessageDigest.isEqual(expectedToken.getBytes(StandardCharsets.UTF_8),
                token.getBytes(StandardCharsets.UTF_8))) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid internal service token");
        }
    }
}
