package iuh.fit.se.service;

import iuh.fit.se.dto.BusinessLookupResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.web.client.HttpClientErrorException;

import java.time.Duration;
import java.util.Map;

/**
 * Tra cứu doanh nghiệp theo mã số thuế qua xinvoice.vn (GET /gdt-api/tax-payer/{taxCode}).
 * The provider returns no legal representative, so the representative is never matched here: every
 * submission must then carry a signed authorization letter.
 */
@Service
@Slf4j
public class BusinessVerificationService {

    private static final String XINVOICE_API = "https://api.xinvoice.vn/gdt-api/tax-payer/";

    private final RestTemplate restTemplate;
    /** Both values are sent as headers only; they are never put in the URL or logged. */
    private final String clientId;
    private final String apiKey;

    @Autowired
    public BusinessVerificationService(RestTemplateBuilder restTemplateBuilder,
            @Value("${app.business-lookup.client-id:}") String clientId,
            @Value("${app.business-lookup.api-key:}") String apiKey) {
        this(restTemplateBuilder
                .setConnectTimeout(Duration.ofSeconds(5))
                .setReadTimeout(Duration.ofSeconds(10))
                .build(), clientId, apiKey);
    }

    BusinessVerificationService(RestTemplate restTemplate, String clientId, String apiKey) {
        this.restTemplate = restTemplate;
        this.clientId = clientId == null ? "" : clientId.trim();
        this.apiKey = apiKey == null ? "" : apiKey.trim();
    }

    /** Looks up a company by tax code. A provider failure is reported as unavailable, never as a verdict. */
    public BusinessLookupResponse lookupByTaxCode(String taxCode) {
        try {
            String url = XINVOICE_API + taxCode.trim();
            log.info("Tra cứu MST: {} via xinvoice API", taxCode);

            HttpHeaders headers = new HttpHeaders();
            headers.set("Accept", "application/json");
            // xinvoice answers without credentials today; the optional headers are sent only when configured.
            if (!clientId.isEmpty()) headers.set("client-id", clientId);
            if (!apiKey.isEmpty()) headers.set("api-key", apiKey);
            HttpEntity<String> entity = new HttpEntity<>(headers);

            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, entity, Map.class);
            Map<String, Object> record = response.getBody();
            if (record == null || record.isEmpty()) {
                return buildNotFound(taxCode);
            }

            String name = getStringValue(record, "name");
            String address = getStringValue(record, "address");
            String status = getStringValue(record, "status");

            // A missing status is not treated as active: the company must show an active status to pass.
            boolean active = status != null && (
                    "active".equalsIgnoreCase(status) || status.toLowerCase().contains("đang hoạt động"));
            boolean valid = name != null && !name.isEmpty() && active;

            return BusinessLookupResponse.builder()
                    .taxCode(taxCode)
                    .companyName(name != null ? name : "Không tìm thấy")
                    .address(address != null ? address : "")
                    .representative("")
                    .status(status != null ? status : "Không xác định")
                    .valid(valid)
                    .message(valid
                            ? "Doanh nghiệp hợp lệ"
                            : name == null
                            ? "Không tìm thấy doanh nghiệp với MST này"
                            : "Doanh nghiệp không ở trạng thái hoạt động")
                    .build();

        } catch (HttpClientErrorException.NotFound e) {
            log.info("Không tìm thấy MST {} (404 Not Found)", taxCode);
            return buildNotFound(taxCode);
        } catch (Exception e) {
            log.warn("Không thể tra cứu MST {} qua dịch vụ doanh nghiệp: {}", taxCode, e.getMessage());
            return BusinessLookupResponse.builder()
                    .taxCode(taxCode)
                    .valid(false)
                    .message("Dịch vụ tra cứu doanh nghiệp tạm thời không khả dụng")
                    .build();
        }
    }

    private BusinessLookupResponse buildNotFound(String taxCode) {
        return BusinessLookupResponse.builder()
                .taxCode(taxCode)
                .valid(false)
                .message("Không tìm thấy doanh nghiệp nào với mã số thuế: " + taxCode)
                .build();
    }

    private String getStringValue(Map<String, Object> map, String key) {
        Object val = map.get(key);
        return val != null && !val.toString().isEmpty() ? val.toString() : null;
    }
}
