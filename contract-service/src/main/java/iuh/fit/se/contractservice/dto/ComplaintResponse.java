package iuh.fit.se.contractservice.dto;

import iuh.fit.se.contractservice.domain.entity.Complaint;
import iuh.fit.se.contractservice.domain.enums.AccountRole;
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
    public static ComplaintResponse from(Complaint complaint) {
        return new ComplaintResponse(
                complaint.getId(),
                complaint.getTripId(),
                complaint.getReporterId(),
                complaint.getRespondentId(),
                complaint.getTitle(),
                complaint.getDescription(),
                complaint.getEvidenceUrl(),
                complaint.getStatus(),
                complaint.getDecision(),
                complaint.getResolution(),
                complaint.getCreatedAt(),
                complaint.getUpdatedAt(),
                complaint.getMessages() == null ? List.of() : complaint.getMessages().stream().map(MessageResponse::from).toList()
        );
    }

    public record MessageResponse(UUID id, UUID senderId, AccountRole senderRole, String message, Instant createdAt) {
        static MessageResponse from(iuh.fit.se.contractservice.domain.entity.ComplaintMessage message) {
            return new MessageResponse(message.getId(), message.getSenderId(), message.getSenderRole(), message.getMessage(), message.getCreatedAt());
        }
    }
}
