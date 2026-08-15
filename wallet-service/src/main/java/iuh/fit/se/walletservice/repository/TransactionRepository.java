package iuh.fit.se.walletservice.repository;

import iuh.fit.se.walletservice.domain.entity.Transaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.UUID;
import java.util.Optional;
import java.math.BigDecimal;
import iuh.fit.se.walletservice.domain.enums.TransactionStatus;
import iuh.fit.se.walletservice.domain.enums.TransactionType;

public interface TransactionRepository extends JpaRepository<Transaction, UUID> {
    Optional<Transaction> findByReferenceCode(String referenceCode);

    Page<Transaction> findByWalletAccountId(UUID accountId, Pageable pageable);

    @Query("select coalesce(sum(t.amount), 0) from Transaction t where t.status = :status and t.type = :type")
    BigDecimal sumByStatusAndType(TransactionStatus status, TransactionType type);

    long countByStatus(TransactionStatus status);
}
