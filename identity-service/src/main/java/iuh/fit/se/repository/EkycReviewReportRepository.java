package iuh.fit.se.repository;

import iuh.fit.se.domain.entity.EkycReviewReport;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.*;

public interface EkycReviewReportRepository extends JpaRepository<EkycReviewReport, UUID> {
    Optional<EkycReviewReport> findFirstByAccountIdOrderBySubmittedAtDesc(UUID accountId);
    Page<EkycReviewReport> findByStatusOrderBySubmittedAtAsc(String status, Pageable page);
    @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select r from EkycReviewReport r where r.id=:id")
    Optional<EkycReviewReport> lockById(@Param("id") UUID id);
    @Modifying @Query("update EkycReviewReport r set r.status='SUPERSEDED', r.frontEncrypted=null, r.backEncrypted=null, r.selfieEncrypted=null, r.evidenceExpiresAt=:now, r.version=r.version+1 where r.accountId=:owner and r.status='PENDING'")
    int supersedePending(@Param("owner") UUID owner, @Param("now") Instant now);
    @Modifying @Query("update EkycReviewReport r set r.frontEncrypted=null, r.backEncrypted=null, r.selfieEncrypted=null, r.version=r.version+1 where r.evidenceExpiresAt<=:now and (r.frontEncrypted is not null or r.backEncrypted is not null or r.selfieEncrypted is not null)")
    int purgeExpiredEvidence(@Param("now") Instant now);
}
