package iuh.fit.se.service;

import iuh.fit.se.domain.entity.Account;
import iuh.fit.se.repository.AccountRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.ApplicationArguments;
import org.springframework.security.crypto.password.PasswordEncoder;

import static iuh.fit.se.domain.enums.AccountRole.ADMIN;
import static iuh.fit.se.domain.enums.AccountRole.CARRIER;
import static iuh.fit.se.domain.enums.AccountRole.SHIPPER;
import static iuh.fit.se.domain.enums.AccountStatus.ACTIVE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DemoAccountSeederTest {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private ApplicationArguments applicationArguments;

    @Test
    void run_missingDemoAccounts_createsThreeActiveRoleAccounts() {
        // Given
        when(accountRepository.existsByPhone("0900000001")).thenReturn(false);
        when(accountRepository.existsByPhone("0900000002")).thenReturn(false);
        when(accountRepository.existsByPhone("0900000003")).thenReturn(false);
        when(passwordEncoder.encode("Admin@123")).thenReturn("admin-hash");
        when(passwordEncoder.encode("Carrier@123")).thenReturn("carrier-hash");
        when(passwordEncoder.encode("Shipper@123")).thenReturn("shipper-hash");
        DemoAccountSeeder seeder = new DemoAccountSeeder(
                accountRepository,
                passwordEncoder,
                "Admin@123",
                "Carrier@123",
                "Shipper@123"
        );
        ArgumentCaptor<Account> accountCaptor = ArgumentCaptor.forClass(Account.class);

        // When
        seeder.run(applicationArguments);

        // Then
        verify(accountRepository, times(3)).save(accountCaptor.capture());
        assertThat(accountCaptor.getAllValues())
                .extracting(
                        Account::getPhone,
                        Account::getEmail,
                        Account::getPasswordHash,
                        Account::getRole,
                        Account::getStatus
                )
                .containsExactlyInAnyOrder(
                        tuple("0900000001", "admin.demo@backhaulbid.local", "admin-hash", ADMIN, ACTIVE),
                        tuple("0900000002", "carrier.demo@backhaulbid.local", "carrier-hash", CARRIER, ACTIVE),
                        tuple("0900000003", "shipper.demo@backhaulbid.local", "shipper-hash", SHIPPER, ACTIVE)
                );
    }

    @Test
    void run_existingDemoAccounts_skipsCreation() {
        // Given
        when(accountRepository.existsByPhone("0900000001")).thenReturn(true);
        when(accountRepository.existsByPhone("0900000002")).thenReturn(true);
        when(accountRepository.existsByPhone("0900000003")).thenReturn(true);
        DemoAccountSeeder seeder = new DemoAccountSeeder(
                accountRepository,
                passwordEncoder,
                "Admin@123",
                "Carrier@123",
                "Shipper@123"
        );

        // When
        seeder.run(applicationArguments);

        // Then
        verify(accountRepository, never()).save(any(Account.class));
        verify(passwordEncoder, never()).encode(anyString());
    }
}
