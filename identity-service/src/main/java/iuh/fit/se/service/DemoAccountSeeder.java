package iuh.fit.se.service;

import iuh.fit.se.domain.entity.Account;
import iuh.fit.se.domain.enums.AccountRole;
import iuh.fit.se.domain.enums.AccountStatus;
import iuh.fit.se.repository.AccountRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@ConditionalOnProperty(
        prefix = "app.demo-accounts",
        name = "enabled",
        havingValue = "true"
)
@Slf4j
public class DemoAccountSeeder implements ApplicationRunner {

    private final AccountRepository accountRepository;
    private final PasswordEncoder passwordEncoder;
    private final String adminPassword;
    private final String carrierPassword;
    private final String shipperPassword;

    public DemoAccountSeeder(
            AccountRepository accountRepository,
            PasswordEncoder passwordEncoder,
            @Value("${app.demo-accounts.admin-password}") String adminPassword,
            @Value("${app.demo-accounts.carrier-password}") String carrierPassword,
            @Value("${app.demo-accounts.shipper-password}") String shipperPassword
    ) {
        this.accountRepository = accountRepository;
        this.passwordEncoder = passwordEncoder;
        this.adminPassword = adminPassword;
        this.carrierPassword = carrierPassword;
        this.shipperPassword = shipperPassword;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        seedAccount(
                "0900000001",
                "admin.demo@backhaulbid.local",
                adminPassword,
                AccountRole.ADMIN
        );
        seedAccount(
                "0900000002",
                "carrier.demo@backhaulbid.local",
                carrierPassword,
                AccountRole.CARRIER
        );
        seedAccount(
                "0900000003",
                "shipper.demo@backhaulbid.local",
                shipperPassword,
                AccountRole.SHIPPER
        );
    }

    private void seedAccount(String phone, String email, String rawPassword, AccountRole role) {
        if (accountRepository.existsByPhone(phone)) {
            log.debug("Demo account already exists: phone={}, role={}", phone, role);
            return;
        }

        Account account = Account.builder()
                .phone(phone)
                .email(email)
                .passwordHash(passwordEncoder.encode(rawPassword))
                .role(role)
                .status(AccountStatus.ACTIVE)
                .build();

        accountRepository.save(account);
        log.info("Created local demo account: phone={}, role={}", phone, role);
    }
}
