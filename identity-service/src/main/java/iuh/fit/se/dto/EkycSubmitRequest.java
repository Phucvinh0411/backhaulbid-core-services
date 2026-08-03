package iuh.fit.se.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class EkycSubmitRequest {
    @Size(max = 50)
    private String identityNumber;

    @Size(max = 255)
    private String fullName;

    @NotNull
    @Size(max = 500)
    private String frontImageUrl;

    @NotNull
    @Size(max = 500)
    private String backImageUrl;

    @NotNull
    @Size(max = 500)
    private String selfieImageUrl;

    @NotNull
    private Boolean ocrPassed;

    @NotNull
    private Boolean documentLivenessPassed;

    @NotNull
    private Boolean documentAuthenticityPassed;

    @NotNull
    private Boolean livenessPassed;

    @NotNull
    private Boolean faceMatched;

    @NotNull
    @DecimalMin("0.0")
    @DecimalMax("100.0")
    private Double faceMatchScore;
}
