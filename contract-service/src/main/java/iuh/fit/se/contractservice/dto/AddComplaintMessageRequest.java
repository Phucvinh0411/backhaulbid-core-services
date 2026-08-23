package iuh.fit.se.contractservice.dto;

import jakarta.validation.constraints.NotBlank;

public record AddComplaintMessageRequest(@NotBlank String message) {
}
