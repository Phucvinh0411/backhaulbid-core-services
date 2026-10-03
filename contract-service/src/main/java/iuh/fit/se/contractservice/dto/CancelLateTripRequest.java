package iuh.fit.se.contractservice.dto;

import jakarta.validation.constraints.Size;

public record CancelLateTripRequest(@Size(max = 500) String reason) {}
