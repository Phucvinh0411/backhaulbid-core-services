package iuh.fit.se.repository;

import iuh.fit.se.domain.entity.Account;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AccountRepository extends JpaRepository<Account, UUID> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select a from Account a where a.id = :id")
    Optional<Account> lockForCapture(@org.springframework.data.repository.query.Param("id") UUID id);
    Optional<Account> findByPhone(String phone);
    @EntityGraph(attributePaths = "userProfile")
    Optional<Account> findWithUserProfileById(UUID accountId);
    boolean existsByPhone(String phone);
    boolean existsByEmail(String email);
    List<Account> findAllByOrderByCreatedAtDesc();
}
