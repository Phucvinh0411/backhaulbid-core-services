package iuh.fit.se.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BusinessLookupResponse {
    private String taxCode;
    private String companyName;
    private String address;
    private String representative;
    private String status;       // "Đang hoạt động", "Ngừng hoạt động", etc.
    private boolean valid;       // true if business is active & found
    private String message;      // Human-readable message
}
