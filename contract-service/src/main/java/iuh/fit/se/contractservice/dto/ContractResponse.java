package iuh.fit.se.contractservice.dto;

import iuh.fit.se.contractservice.domain.entity.Contract;
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
        Instant createdAt,
        Instant updatedAt,
        TripResponse trip,
        List<SignatureResponse> signatures
) {
    public static ContractResponse from(Contract contract) {
        return new ContractResponse(
                contract.getId(),
                contract.getTrip() == null ? null : contract.getTrip().getId(),
                contract.getShipperId(),
                contract.getCarrierId(),
                contract.getContractCode(),
                contract.getPdfUrl(),
                contract.getPdfHashSha256(),
                contract.getStatus(),
                contract.getCreatedAt(),
                contract.getUpdatedAt(),
                contract.getTrip() == null ? null : TripResponse.from(contract.getTrip()),
                contract.getSignatures() == null
                        ? List.of()
                        : contract.getSignatures().stream().map(SignatureResponse::from).toList()
        );
    }

    public record SignatureResponse(
            UUID accountId,
            AccountRole role,
            Instant signedAt
    ) {
        static SignatureResponse from(iuh.fit.se.contractservice.domain.entity.ContractSignature signature) {
            return new SignatureResponse(signature.getAccountId(), signature.getRole(), signature.getSignedAt());
        }
    }
}
