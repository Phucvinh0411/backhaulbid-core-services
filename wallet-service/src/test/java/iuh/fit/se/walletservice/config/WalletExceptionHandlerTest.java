package iuh.fit.se.walletservice.config;

import iuh.fit.se.walletservice.dto.response.ApiErrorResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;

class WalletExceptionHandlerTest {
    private final WalletExceptionHandler handler = new WalletExceptionHandler();

    @Test
    void responseStatusException_isReturnedAsStableErrorEnvelope() {
        var response = handler.handleResponseStatus(
                new ResponseStatusException(HttpStatus.CONFLICT, "Payment order has expired"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        ApiErrorResponse body = response.getBody();
        assertThat(body.error().code()).isEqualTo("WALLET_409");
        assertThat(body.error().message()).isEqualTo("Payment order has expired");
        assertThat(body.error().details()).containsEntry("status", 409);
    }
}
