package iuh.fit.se.walletservice.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OpenApiConfigTest {

    @Test
    void walletOpenApi_exposesServiceMetadataAndBearerAuth() {
        var openApi = new OpenApiConfig().walletServiceOpenApi();

        assertThat(openApi.getInfo().getTitle()).isEqualTo("BackHaulBid Wallet API");
        assertThat(openApi.getComponents().getSecuritySchemes()).containsKey(OpenApiConfig.BEARER_AUTH);
        assertThat(openApi.getSecurity()).isNotEmpty();
    }
}
