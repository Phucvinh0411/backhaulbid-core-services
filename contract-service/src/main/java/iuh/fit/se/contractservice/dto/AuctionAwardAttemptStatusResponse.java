package iuh.fit.se.contractservice.dto;

import iuh.fit.se.contractservice.domain.enums.ContractStatus;

import java.time.Instant;
import java.util.UUID;

public record AuctionAwardAttemptStatusResponse(
        String auctionId,
        String awardAttemptId,
        UUID tripId,
        UUID contractId,
        ContractStatus status,
        boolean carrierSigned,
        boolean shipperSigned,
        Instant signingDeadlineAt
) {}
