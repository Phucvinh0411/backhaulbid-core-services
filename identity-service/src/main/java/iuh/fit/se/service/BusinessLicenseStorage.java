package iuh.fit.se.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

@Component
public class BusinessLicenseStorage {

    private static final long MAX_FILE_SIZE = 5L * 1024 * 1024;
    private final Path uploadDirectory;

    public BusinessLicenseStorage(
            @Value("${app.business-verification.upload-dir:./data/business-verifications}")
            String uploadDirectory) {
        this.uploadDirectory = Path.of(uploadDirectory).toAbsolutePath().normalize();
    }

    public StoredBusinessLicense store(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Vui lòng chọn giấy phép đăng ký kinh doanh");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException("Tài liệu vượt quá dung lượng tối đa 5MB");
        }

        try {
            byte[] content = file.getBytes();
            String extension = detectExtension(content);
            String storedFilename = UUID.randomUUID() + extension;
            Files.createDirectories(uploadDirectory);
            Files.write(uploadDirectory.resolve(storedFilename), content);

            String originalFilename = file.getOriginalFilename() == null
                    ? "giay-phep-dang-ky-kinh-doanh" + extension
                    : Path.of(file.getOriginalFilename()).getFileName().toString();
            if (originalFilename.length() > 255) {
                originalFilename = originalFilename.substring(0, 255);
            }

            return new StoredBusinessLicense(storedFilename, originalFilename);
        } catch (IOException exception) {
            throw new IllegalStateException("Không thể lưu tài liệu doanh nghiệp", exception);
        }
    }

    public Resource load(String storedFilename) {
        try {
            Path document = uploadDirectory.resolve(storedFilename).normalize();
            if (!document.startsWith(uploadDirectory) || !Files.isRegularFile(document)) {
                throw new IllegalArgumentException("Không tìm thấy tài liệu doanh nghiệp");
            }
            return new UrlResource(document.toUri());
        } catch (IOException exception) {
            throw new IllegalStateException("Không thể đọc tài liệu doanh nghiệp", exception);
        }
    }

    private String detectExtension(byte[] content) {
        if (startsWith(content, new byte[]{0x25, 0x50, 0x44, 0x46, 0x2D})) {
            return ".pdf";
        }
        if (startsWith(content, new byte[]{
                (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A})) {
            return ".png";
        }
        if (startsWith(content, new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF})) {
            return ".jpg";
        }
        throw new IllegalArgumentException(
                "Tài liệu phải là tệp PDF, PNG hoặc JPG hợp lệ");
    }

    private boolean startsWith(byte[] content, byte[] signature) {
        if (content.length < signature.length) {
            return false;
        }
        for (int index = 0; index < signature.length; index++) {
            if (content[index] != signature[index]) {
                return false;
            }
        }
        return true;
    }

    public record StoredBusinessLicense(
            String storedFilename,
            String originalFilename) {
    }
}
