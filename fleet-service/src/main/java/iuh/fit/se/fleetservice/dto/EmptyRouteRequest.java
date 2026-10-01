package iuh.fit.se.fleetservice.dto;

import jakarta.validation.constraints.*;
import lombok.*;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmptyRouteRequest {

    // ───── Phương tiện ─────
    @NotBlank(message = "Mã xe / biển số không được để trống")
    private String truckId;

    /** Loại phương tiện (lấy tự động từ Vehicle, frontend có thể gửi kèm để lưu nhanh) */
    private String truckType;

    /** companyId được inject ở server side, không cần frontend gửi */
    private String companyId;

    // ───── Tải trọng ─────
    /** Tải trọng còn trống (tấn). Phải > 0 nếu được cung cấp */
    @DecimalMin(value = "0.1", message = "Tải trọng còn trống phải lớn hơn 0")
    private Double availableCapacity;

    // ───── Điểm xuất phát (A) ─────
    @NotBlank(message = "Địa chỉ điểm xuất phát không được để trống")
    private String origin;

    @NotNull(message = "Vĩ độ điểm xuất phát không được để trống")
    @DecimalMin(value = "-90.0",  message = "Vĩ độ phải >= -90")
    @DecimalMax(value = "90.0",   message = "Vĩ độ phải <= 90")
    private Double latitude;

    @NotNull(message = "Kinh độ điểm xuất phát không được để trống")
    @DecimalMin(value = "-180.0", message = "Kinh độ phải >= -180")
    @DecimalMax(value = "180.0",  message = "Kinh độ phải <= 180")
    private Double longitude;

    // ───── Điểm đến (B) ─────
    @NotBlank(message = "Địa chỉ điểm đến không được để trống")
    private String destination;

    @NotNull(message = "Vĩ độ điểm đến không được để trống")
    private Double destLatitude;

    @NotNull(message = "Kinh độ điểm đến không được để trống")
    private Double destLongitude;

    // ───── Thời gian ─────
    @NotNull(message = "Thời gian xe bắt đầu rỗng không được để trống")
    @Future(message = "Thời gian xe bắt đầu rỗng phải ở trong tương lai")
    private LocalDateTime expectedEmptyTime;

    @NotNull(message = "Thời gian dự kiến đến không được để trống")
    private LocalDateTime expectedArrivalTime;

    // ───── Bán kính tìm kiếm ─────
    /** Bán kính tìm kiếm lô hàng xung quanh điểm xuất phát (km). Mặc định 50 km */
    @Min(value = 5,   message = "Bán kính tìm kiếm tối thiểu là 5 km")
    @Max(value = 500, message = "Bán kính tìm kiếm tối đa là 500 km")
    @Builder.Default
    private Integer searchRadius = 50;
}
