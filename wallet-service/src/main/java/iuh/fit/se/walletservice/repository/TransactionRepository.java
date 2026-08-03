package iuh.fit.se.walletservice.repository;

import iuh.fit.se.walletservice.domain.entity.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface TransactionRepository extends JpaRepository<Transaction, UUID> {
}
