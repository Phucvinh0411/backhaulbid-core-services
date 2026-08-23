package iuh.fit.se.contractservice.repository;

import iuh.fit.se.contractservice.domain.entity.Complaint;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ComplaintRepository extends JpaRepository<Complaint, UUID> {
    List<Complaint> findAllByOrderByCreatedAtDesc();

    List<Complaint> findByReporterIdOrRespondentIdOrderByCreatedAtDesc(UUID reporterId, UUID respondentId);
}
