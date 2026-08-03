package iuh.fit.se.domain.dto.request;

import iuh.fit.se.domain.enums.AccountRole;
import lombok.Data;

@Data
public class RegisterRequest {
    private String phone;
    private String email;
    private String password;
    private AccountRole role;
}
