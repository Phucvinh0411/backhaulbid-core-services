package iuh.fit.se.contractservice.dto;

import iuh.fit.se.contractservice.domain.enums.AccountRole;
import iuh.fit.se.contractservice.domain.enums.ComplaintCategory;
import iuh.fit.se.contractservice.domain.enums.ComplaintDecision;
import iuh.fit.se.contractservice.domain.enums.ComplaintStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ComplaintResponse(
        UUID id,
        UUID tripId,
        UUID reporterId,
        UUID respondentId,
        ComplaintCategory category,
        String title,
        String description,
        String evidenceUrl,
        ComplaintStatus status,
        ComplaintDecision decision,
        String resolution,
        Instant createdAt,
        Instant updatedAt,
        List<MessageResponse> messages
) {
    public record MessageResponse(UUID id, UUID senderId, AccountRole senderRole, String message, Instant createdAt) {
    }
}
