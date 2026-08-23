package iuh.fit.se.domain.dto.request;

import iuh.fit.se.domain.enums.AccountStatus;
import jakarta.validation.constraints.NotNull;

public record AccountStatusUpdateRequest(@NotNull AccountStatus status) {
}
