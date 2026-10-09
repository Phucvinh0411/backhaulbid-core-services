package iuh.fit.se.mediaservice.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;

@Service
public class MediaService {

    /** The stored key (folder/uuid.ext) is what private readers use; the URL is kept for older clients. */
    public record UploadedObject(String key, String url) {
    }

    private final S3Client s3Client;

    @Value("${aws.s3.bucket-name}")
    private String bucketName;

    @Value("${aws.s3.region}")
    private String region;

    @Value("${aws.s3.access-key}")
    private String accessKey;

    public MediaService(S3Client s3Client) {
        this.s3Client = s3Client;
    }

    /**
     * Stores one upload after checking the folder and the real file type from its first bytes. The extension comes
     * from the detected type, not from the client's file name.
     */
    public UploadedObject uploadFile(MultipartFile file, String folder) throws IOException {
        if (!MediaUploadPolicy.isUploadFolder(folder)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Thư mục tải lên không hợp lệ");
        }
        if (file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tệp tải lên đang rỗng");
        }
        if (file.getSize() > MediaUploadPolicy.MAX_BYTES) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "Tệp vượt quá 15 MB");
        }
        MediaUploadPolicy.FileKind kind = MediaUploadPolicy.detect(readHead(file, 16));
        if (!MediaUploadPolicy.accepts(folder, kind)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tệp không đúng định dạng cho mục tải lên này");
        }

        String key = folder + "/" + UUID.randomUUID() + "." + kind.extension();
        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .contentType(kind.mimeType())
                // .acl(ObjectCannedACL.PUBLIC_READ) // Requires ACL enabled on Bucket, skipping for standard setups (Bucket policy is preferred)
                .build();

        try (InputStream content = file.getInputStream()) {
            s3Client.putObject(putObjectRequest, RequestBody.fromInputStream(content, file.getSize()));
        }
        return new UploadedObject(key, "https://" + bucketName + ".s3." + region + ".amazonaws.com/" + key);
    }

    /** Opens an object for streaming; the caller has already validated the key with MediaReadPolicy. */
    public ResponseInputStream<GetObjectResponse> open(String key) {
        try {
            return s3Client.getObject(GetObjectRequest.builder().bucket(bucketName).key(key).build());
        } catch (NoSuchKeyException exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "File not found");
        }
    }

    private static byte[] readHead(MultipartFile file, int length) throws IOException {
        try (InputStream content = file.getInputStream()) {
            return content.readNBytes(length);
        }
    }
}
