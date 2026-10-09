package iuh.fit.se.repository;
import iuh.fit.se.domain.entity.EkycReviewAudit;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;
public interface EkycReviewAuditRepository extends JpaRepository<EkycReviewAudit, UUID> {}
