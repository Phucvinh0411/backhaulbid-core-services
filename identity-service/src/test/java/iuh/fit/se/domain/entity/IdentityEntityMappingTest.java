package iuh.fit.se.domain.entity;

import iuh.fit.se.domain.enums.AccountRole;
import iuh.fit.se.domain.enums.AccountStatus;
import iuh.fit.se.repository.AccountRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class IdentityEntityMappingTest {

    @Autowired
    private AccountRepository accountRepository;

    @Test
    void save_inactiveAccount_populatesAuditTimestamps() {
        // Given
        Account account = Account.builder()
                .phone("0900000001")
                .email("mapping@test.local")
                .passwordHash("encoded-password")
                .role(AccountRole.SHIPPER)
                .status(AccountStatus.INACTIVE)
                .build();

        // When
        Account actualAccount = accountRepository.saveAndFlush(account);

        // Then
        assertThat(actualAccount.getStatus()).isEqualTo(AccountStatus.INACTIVE);
        assertThat(actualAccount.getCreatedAt()).isNotNull();
        assertThat(actualAccount.getUpdatedAt()).isNotNull();
    }
}
