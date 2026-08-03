package iuh.fit.se.domain.dto.response;

import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
public class AuthResponse {
    private UUID accountId;
    private String accessToken;
    private String refreshToken;
    private String phone;
    private String role;
}
