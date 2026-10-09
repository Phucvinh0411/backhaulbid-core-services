package iuh.fit.se.domain.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record UpdateAvatarRequest(
        @NotBlank(message = "Vui lòng chọn ảnh đại diện hợp lệ.")
        @Pattern(
                regexp = "(?i)^https://[a-z0-9.-]+\\.s3\\.[a-z0-9-]+\\.amazonaws\\.com/avatars/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.(png|jpe?g|webp)$",
                message = "Ảnh đại diện phải là tệp PNG, JPEG hoặc WebP đã tải lên.")
        String avatarUrl) {
}
