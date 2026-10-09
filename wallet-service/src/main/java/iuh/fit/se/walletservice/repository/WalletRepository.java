package iuh.fit.se.walletservice.repository;

import iuh.fit.se.walletservice.domain.entity.Wallet;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import java.util.Optional;
import java.util.UUID;

public interface WalletRepository extends JpaRepository<Wallet, UUID> {
    Optional<Wallet> findByAccountId(UUID accountId);

    @Modifying
    @Query(value = "INSERT INTO wallets (account_id) VALUES (:accountId) ON CONFLICT (account_id) DO NOTHING", nativeQuery = true)
    int ensureExistsByAccountId(@Param("accountId") UUID accountId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select w from Wallet w where w.accountId = :accountId")
    Optional<Wallet> findByAccountIdForUpdate(@Param("accountId") UUID accountId);
}
