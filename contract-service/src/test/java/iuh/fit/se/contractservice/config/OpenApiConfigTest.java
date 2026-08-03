package iuh.fit.se.contractservice.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OpenApiConfigTest {

    @Test
    void contractOpenApi_exposesServiceMetadataAndBearerAuth() {
        var openApi = new OpenApiConfig().contractServiceOpenApi();

        assertThat(openApi.getInfo().getTitle()).isEqualTo("BackHaulBid Contract API");
        assertThat(openApi.getComponents().getSecuritySchemes()).containsKey(OpenApiConfig.BEARER_AUTH);
        assertThat(openApi.getSecurity()).isNotEmpty();
    }
}
