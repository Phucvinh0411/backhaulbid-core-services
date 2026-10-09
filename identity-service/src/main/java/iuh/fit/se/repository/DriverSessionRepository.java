package iuh.fit.se.repository;

import iuh.fit.se.domain.entity.DriverSession;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface DriverSessionRepository extends JpaRepository<DriverSession, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from DriverSession s where s.id = :id")
    Optional<DriverSession> lockById(@Param("id") UUID id);
}
