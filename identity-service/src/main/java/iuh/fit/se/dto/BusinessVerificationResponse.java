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
public class BusinessVerificationResponse {
    private UUID id;
    private UUID accountId;
    private String accountRole;
    private String contactEmail;
    private String contactPhone;
    private String taxCode;
    private String companyName;
    private String address;
    private String legalRepresentative;
    private String ekycRepresentativeName;
    private Boolean representativeMatched;
    private Boolean requiresAuthorization;
    private String status;
    private String businessLicenseFilename;
    private String authorizationLetterFilename;
    private String rejectionReason;

}
