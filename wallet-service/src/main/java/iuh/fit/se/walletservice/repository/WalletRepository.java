package iuh.fit.se.walletservice.repository;

import iuh.fit.se.walletservice.domain.entity.Wallet;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface WalletRepository extends JpaRepository<Wallet, UUID> {
    Optional<Wallet> findByAccountId(UUID accountId);
}
