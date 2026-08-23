package iuh.fit.se.walletservice.repository;

import iuh.fit.se.walletservice.domain.entity.WithdrawalRequest;
import iuh.fit.se.walletservice.domain.enums.WithdrawalStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface WithdrawalRequestRepository extends JpaRepository<WithdrawalRequest, UUID> {
    Page<WithdrawalRequest> findByAccountId(UUID accountId, Pageable pageable);

    Page<WithdrawalRequest> findByStatus(WithdrawalStatus status, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select withdrawal from WithdrawalRequest withdrawal where withdrawal.id = :id")
    WithdrawalRequest findByIdForUpdate(@Param("id") UUID id);
}
