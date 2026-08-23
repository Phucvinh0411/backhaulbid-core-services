package iuh.fit.se.service;

import iuh.fit.se.domain.dto.request.AccountStatusUpdateRequest;
import iuh.fit.se.domain.entity.Account;
import iuh.fit.se.domain.dto.response.AdminAccountResponse;
import iuh.fit.se.domain.enums.AccountRole;
import iuh.fit.se.domain.enums.AccountStatus;
import iuh.fit.se.repository.AccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AdminAccountService {
    private final AccountRepository accountRepository;

    @Transactional(readOnly = true)
    public List<AdminAccountResponse> list(String search, AccountRole role, AccountStatus status) {
        String normalizedSearch = search == null ? "" : search.trim().toLowerCase();
        return accountRepository.findAllByOrderByCreatedAtDesc().stream()
                .filter(account -> role == null || account.getRole() == role)
                .filter(account -> status == null || account.getStatus() == status)
                .filter(account -> normalizedSearch.isBlank() || matches(account, normalizedSearch))
                .map(AdminAccountResponse::from)
                .toList();
    }

    @Transactional
    public AdminAccountResponse updateStatus(UUID adminId, UUID accountId, AccountStatusUpdateRequest request) {
        if (adminId.equals(accountId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Admin cannot change their own status");
        }
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found"));
        account.setStatus(request.status());
        return AdminAccountResponse.from(accountRepository.save(account));
    }

    private boolean matches(Account account, String search) {
        return contains(account.getPhone(), search)
                || contains(account.getEmail(), search)
                || contains(account.getId().toString(), search)
                || (account.getUserProfile() != null && contains(account.getUserProfile().getFullName(), search))
                || (account.getCompany() != null && contains(account.getCompany().getCompanyName(), search));
    }

    private boolean contains(String value, String search) {
        return value != null && value.toLowerCase().contains(search);
    }
}
