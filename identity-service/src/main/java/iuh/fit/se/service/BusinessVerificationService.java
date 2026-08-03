package iuh.fit.se.service;

import iuh.fit.se.dto.BusinessLookupResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
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
import java.util.List;

@Service
@Slf4j
public class BusinessVerificationService {

    private static final String DOANHNGHIEP_API = "https://doanhnghiep.vn/api/v1/companies/";

    private final RestTemplate restTemplate;

    @Autowired
    public BusinessVerificationService(RestTemplateBuilder restTemplateBuilder) {
        this(restTemplateBuilder
                .setConnectTimeout(Duration.ofSeconds(5))
                .setReadTimeout(Duration.ofSeconds(10))
                .build());
    }

    BusinessVerificationService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    /**
     * Tra cứu thông tin doanh nghiệp qua Mã số thuế bằng API doanhnghiep.vn
     * API này trả về được cả người đại diện pháp luật!
     */
    @SuppressWarnings("unchecked")
    public BusinessLookupResponse lookupByTaxCode(String taxCode) {
        try {
            String url = DOANHNGHIEP_API + taxCode.trim();
            log.info("Tra cứu MST: {} via doanhnghiep.vn API", taxCode);

            HttpHeaders headers = new HttpHeaders();
            headers.set("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
            headers.set("Accept", "application/json");
            HttpEntity<String> entity = new HttpEntity<>(headers);

            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, entity, Map.class);
            Map<String, Object> body = response.getBody();

            if (body == null) {
                return buildNotFound(taxCode);
            }

            // Có thể trả về object trực tiếp hoặc bọc trong "data"
            Object dataObj = body.containsKey("data") ? body.get("data") : body;
            Map<String, Object> record = null;

            if (dataObj instanceof List) {
                List<Map<String, Object>> list = (List<Map<String, Object>>) dataObj;
                if (!list.isEmpty()) {
                    record = list.get(0);
                }
            } else if (dataObj instanceof Map) {
                record = (Map<String, Object>) dataObj;
            }

            if (record == null) {
                return buildNotFound(taxCode);
            }

            String name = getStringValue(
                    record, "name_vi", "name", "tenDN", "company_name", "title", "Title");
            String address = getStringValue(
                    record, "address_full", "address", "diaChi", "DiaChiDN");
            String status = getStringValue(record, "status", "trangThai", "TrangThai", "tinh_trang");
            String representative = getStringValue(
                    record, "legal_rep_name", "representative", "nguoiDaiDien",
                    "NguoiDaiDien", "dai_dien", "director", "Director");

            // Kiểm tra trạng thái hoạt động (Nếu không có, mặc định coi là Đang hoạt động)
            if (status == null || status.isEmpty()) {
                status = "Đang hoạt động";
            }


            boolean active = "active".equalsIgnoreCase(status)
                    || status.toLowerCase().contains("đang hoạt động");
            boolean valid = name != null && !name.isEmpty() && active;

            return BusinessLookupResponse.builder()
                    .taxCode(taxCode)
                    .companyName(name != null ? name : "Không tìm thấy")
                    .address(address != null ? address : "")
                    .representative(representative != null ? representative : "")
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

    /**
     * Lấy giá trị string từ Map, thử nhiều key khác nhau (vì API có thể đổi format)
     */
    private String getStringValue(Map<String, Object> map, String... keys) {
        for (String key : keys) {
            Object val = map.get(key);
            if (val != null && !val.toString().isEmpty()) {
                return val.toString();
            }
        }
        return null;
    }
}
