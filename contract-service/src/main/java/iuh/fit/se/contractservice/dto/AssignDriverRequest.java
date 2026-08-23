package iuh.fit.se.contractservice.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record AssignDriverRequest(@NotNull UUID driverId) {
}
