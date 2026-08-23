package iuh.fit.se.mediaservice.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

@Configuration
public class AwsS3Config {

    @Value("${aws.s3.region}")
    private String region;

    @Value("${aws.s3.access-key}")
    private String accessKey;

    @Value("${aws.s3.secret-key}")
    private String secretKey;

    @Bean
    public S3Client s3Client() {
        // Nếu dùng mock creds, tránh crash thì có thể dùng anonymous hoặc client builder cơ bản
        if ("mock-key".equals(accessKey)) {
            // Dummy client for testing without real credentials (sẽ lỗi khi gọi upload thật nếu S3Client cố gắng dùng)
            // nhưng ta vẫn cần bean để app khởi động
        }
        
        AwsBasicCredentials credentials = AwsBasicCredentials.create(accessKey, secretKey);
        
        return S3Client.builder()
                .region(Region.of(region))
                .credentialsProvider(StaticCredentialsProvider.create(credentials))
                .build();
    }
}
