package iuh.fit.se.service;

import iuh.fit.se.dto.BusinessLookupResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpEntity;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;

class BusinessVerificationServiceTest {

    @Test
    void lookup_currentDoanhNghiepResponse_mapsVietnameseCompanyFields() {
        RestTemplate restTemplate = mock(RestTemplate.class);
        BusinessVerificationService service =
                new BusinessVerificationService(restTemplate);
        Map<String, Object> response = Map.of(
                "mst", "0100684378",
                "name_vi", "Tập đoàn Bưu chính Viễn thông Việt Nam",
                "address_full", "57 Huỳnh Thúc Kháng, Hà Nội",
                "legal_rep_name", "Huỳnh Quang Liêm",
                "status", "active"
        );
        when(restTemplate.exchange(
                eq("https://doanhnghiep.vn/api/v1/companies/0100684378"),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                eq(Map.class)))
                .thenReturn(ResponseEntity.ok(response));

        BusinessLookupResponse result = service.lookupByTaxCode("0100684378");

        assertThat(result.isValid()).isTrue();
        assertThat(result.getCompanyName())
                .isEqualTo("Tập đoàn Bưu chính Viễn thông Việt Nam");
        assertThat(result.getAddress()).isEqualTo("57 Huỳnh Thúc Kháng, Hà Nội");
        assertThat(result.getRepresentative()).isEqualTo("Huỳnh Quang Liêm");
    }
}
