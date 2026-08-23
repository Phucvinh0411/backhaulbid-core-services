package iuh.fit.se.domain.dto.response;

import java.util.UUID;

public record CurrentAccountResponse(
        UUID accountId,
        String phone,
        String email,
        String role,
        String fullName,
        String avatarUrl) {
}
