package iuh.fit.se.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RepresentativeVerificationResponse {
    private UUID id;
    private String status;
    private String fullName;
    private String failureReason;
}
