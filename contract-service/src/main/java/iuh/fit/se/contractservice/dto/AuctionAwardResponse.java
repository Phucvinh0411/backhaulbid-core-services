package iuh.fit.se.contractservice.dto;

import java.util.UUID;

public record AuctionAwardResponse(String auctionId, UUID tripId, UUID contractId, boolean latePolicyEnabled) {}
