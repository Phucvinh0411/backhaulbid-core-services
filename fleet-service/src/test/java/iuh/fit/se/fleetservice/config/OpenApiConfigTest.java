package iuh.fit.se.fleetservice.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OpenApiConfigTest {

    @Test
    void fleetOpenApi_exposesServiceMetadataAndBearerAuth() {
        var openApi = new OpenApiConfig().fleetServiceOpenApi();

        assertThat(openApi.getInfo().getTitle()).isEqualTo("BackHaulBid Fleet API");
        assertThat(openApi.getComponents().getSecuritySchemes()).containsKey(OpenApiConfig.BEARER_AUTH);
        assertThat(openApi.getSecurity()).isNotEmpty();
    }
}
