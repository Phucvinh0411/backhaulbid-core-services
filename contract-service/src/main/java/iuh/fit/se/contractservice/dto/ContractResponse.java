package iuh.fit.se.contractservice.dto;

import iuh.fit.se.contractservice.domain.enums.AccountRole;
import iuh.fit.se.contractservice.domain.enums.ContractStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ContractResponse(
        UUID id,
        UUID tripId,
        UUID shipperId,
        UUID carrierId,
        String contractCode,
        String pdfUrl,
        String pdfHashSha256,
        ContractStatus status,
        Instant signingDeadlineAt,
        Instant createdAt,
        Instant updatedAt,
        TripResponse trip,
        List<SignatureResponse> signatures,
        String shipperName,
        String carrierName
) {
    public record SignatureResponse(
            UUID accountId,
            AccountRole role,
            Instant signedAt
    ) {
    }
}
