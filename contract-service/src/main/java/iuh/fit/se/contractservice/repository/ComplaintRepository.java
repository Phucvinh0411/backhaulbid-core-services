package iuh.fit.se.contractservice.repository;

import iuh.fit.se.contractservice.domain.entity.Complaint;
import iuh.fit.se.contractservice.domain.enums.ComplaintCategory;
import iuh.fit.se.contractservice.domain.enums.ComplaintStatus;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ComplaintRepository extends JpaRepository<Complaint, UUID> {
    long countByStatus(ComplaintStatus status);
    List<Complaint> findAllByOrderByCreatedAtDesc();

    List<Complaint> findByReporterIdOrRespondentIdOrderByCreatedAtDesc(UUID reporterId, UUID respondentId);

    List<Complaint> findByCategoryOrderByCreatedAtDesc(ComplaintCategory category);

    List<Complaint> findByCategoryIsNullOrderByCreatedAtDesc();

    @Query("""
            select c from Complaint c
            where (c.reporterId = :accountId or c.respondentId = :accountId)
              and c.category = :category
            order by c.createdAt desc
            """)
    List<Complaint> findPartyComplaintsByCategory(@Param("accountId") UUID accountId,
                                                  @Param("category") ComplaintCategory category);

    @Query("""
            select c from Complaint c
            where (c.reporterId = :accountId or c.respondentId = :accountId)
              and c.category is null
            order by c.createdAt desc
            """)
    List<Complaint> findPartyComplaintsWithoutCategory(@Param("accountId") UUID accountId);
}
