package iuh.fit.se.fleetservice.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmptyRouteRequest {
    
    @NotBlank(message = "Mã xe không được để trống")
    private String truckId;

    @NotBlank(message = "Mã công ty không được để trống")
    private String companyId;

    @NotNull(message = "Thời gian dự kiến xe rỗng không được để trống")
    @Future(message = "Thời gian dự kiến xe rỗng phải ở trong tương lai")
    private LocalDateTime expectedEmptyTime;

    @NotNull(message = "Vĩ độ không được để trống")
    private Double latitude;

    @NotNull(message = "Kinh độ không được để trống")
    private Double longitude;

    private String origin;

    private String destination;
}
