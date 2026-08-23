package iuh.fit.se.walletservice.dto.response;

import java.util.Map;

public record ApiErrorResponse(ErrorBody error) {
    public record ErrorBody(
            String code,
            String message,
            Map<String, Object> details
    ) {
    }
}
