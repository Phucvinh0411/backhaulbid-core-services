package iuh.fit.se.domain.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserMeResponse {
    private UUID accountId;
    private String phone;
    private String email;
    private String role;
    private String fullName;
    private String avatarUrl;
    private String address;
    private String companyName;
}
