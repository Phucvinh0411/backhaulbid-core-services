package iuh.fit.se.service;

import iuh.fit.se.domain.dto.request.AccountStatusUpdateRequest;
import iuh.fit.se.domain.entity.Account;
import iuh.fit.se.domain.enums.AccountRole;
import iuh.fit.se.domain.enums.AccountStatus;
import iuh.fit.se.repository.AccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminAccountServiceTest {
    @Mock
    private AccountRepository accountRepository;

    private AdminAccountService service;
    private UUID adminId;
    private Account account;

    @BeforeEach
    void setUp() {
        service = new AdminAccountService(accountRepository);
        adminId = UUID.randomUUID();
        account = Account.builder()
                .id(UUID.randomUUID())
                .phone("0901234567")
                .email("carrier@example.test")
                .role(AccountRole.CARRIER)
                .status(AccountStatus.INACTIVE)
                .build();
    }

    @Test
    void listsAccountsWithSearchAndRoleFilters() {
        when(accountRepository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(account));

        var result = service.list("carrier@example", AccountRole.CARRIER, AccountStatus.INACTIVE);

        assertThat(result).singleElement().satisfies(item -> {
            assertThat(item.id()).isEqualTo(account.getId());
            assertThat(item.displayName()).isEqualTo("carrier@example.test");
        });
    }

    @Test
    void adminCanActivateAnotherAccount() {
        when(accountRepository.findById(account.getId())).thenReturn(java.util.Optional.of(account));
        when(accountRepository.save(any(Account.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var result = service.updateStatus(adminId, account.getId(), new AccountStatusUpdateRequest(AccountStatus.ACTIVE));

        assertThat(result.status()).isEqualTo(AccountStatus.ACTIVE);
    }

    @Test
    void adminCannotChangeOwnStatus() {
        assertThatThrownBy(() -> service.updateStatus(
                adminId,
                adminId,
                new AccountStatusUpdateRequest(AccountStatus.BLOCKED)))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
                .hasMessageContaining("cannot change their own status");
    }
}
