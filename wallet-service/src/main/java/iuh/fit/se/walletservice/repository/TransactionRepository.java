package iuh.fit.se.walletservice.repository;

import iuh.fit.se.walletservice.domain.entity.Transaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import java.util.UUID;
import java.util.Optional;
import java.math.BigDecimal;
import iuh.fit.se.walletservice.domain.enums.TransactionStatus;
import iuh.fit.se.walletservice.domain.enums.TransactionType;

public interface TransactionRepository extends JpaRepository<Transaction, UUID> {
    Optional<Transaction> findByReferenceCode(String referenceCode);

    Optional<Transaction> findByIdempotencyKey(String idempotencyKey);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from Transaction t join fetch t.wallet where t.id = :id")
    Optional<Transaction> findByIdForUpdate(@Param("id") UUID id);

    Page<Transaction> findByWalletAccountId(UUID accountId, Pageable pageable);

    Optional<Transaction> findByIdAndWalletAccountId(UUID id, UUID accountId);

    @Query("select coalesce(sum(t.amount), 0) from Transaction t where t.status = :status and t.type = :type")
    BigDecimal sumByStatusAndType(TransactionStatus status, TransactionType type);

    long countByStatus(TransactionStatus status);
}
