package iuh.fit.se.repository;

import iuh.fit.se.domain.entity.EkycCaptureSession;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.*;

public interface EkycCaptureSessionRepository extends JpaRepository<EkycCaptureSession, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from EkycCaptureSession s where s.id = :id")
    Optional<EkycCaptureSession> lockById(@Param("id") UUID id);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<EkycCaptureSession> findByAccountIdAndStateIn(UUID accountId, Collection<EkycCaptureSession.State> states);
    long countByAccountIdAndCreatedAtAfter(UUID accountId, Instant since);
}
