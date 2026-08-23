package iuh.fit.se.walletservice.repository;

import iuh.fit.se.walletservice.domain.entity.PaymentOrder;
import iuh.fit.se.walletservice.domain.enums.PaymentOrderStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface PaymentOrderRepository extends JpaRepository<PaymentOrder, UUID> {
    Page<PaymentOrder> findByAccountId(UUID accountId, Pageable pageable);

    Page<PaymentOrder> findByStatus(PaymentOrderStatus status, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select paymentOrder from PaymentOrder paymentOrder where paymentOrder.invoiceNumber = :invoiceNumber")
    Optional<PaymentOrder> findByInvoiceNumberForUpdate(@Param("invoiceNumber") String invoiceNumber);

    @Query("select paymentOrder from PaymentOrder paymentOrder where paymentOrder.invoiceNumber = :invoiceNumber")
    Optional<PaymentOrder> findByInvoiceNumber(@Param("invoiceNumber") String invoiceNumber);
}
