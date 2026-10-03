package iuh.fit.se.contractservice.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.client.SimpleClientHttpRequestFactory;

import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class LateDeliveryNotificationClient {
    @Value("${NOTIFICATION_SERVICE_URL:http://notification-service:3002/api/v1/notifications}")
    private String notificationServiceUrl;

    @Value("${INTERNAL_SERVICE_TOKEN:}")
    private String token;

    private final RestTemplate restTemplate = createRestTemplate();

    private static RestTemplate createRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(3000);
        factory.setReadTimeout(5000);
        return new RestTemplate(factory);
    }

    public void notify(UUID userId, UUID tripId, int tier, long lateMinutes, String role) {
        String title = tier == 1 ? "Đơn hàng có nguy cơ giao trễ"
                : tier == 2 ? "Đơn hàng đã giao trễ hơn 1 giờ" : "Đơn hàng đã giao trễ hơn 3 giờ";
        String message = "Chuyến " + tripId + " trễ " + lateMinutes
                + " phút. Mức bồi thường hiện tại là " + (tier == 1 ? "5%" : tier == 2 ? "50%" : "100%")
                + " tiền đặt trước. " + ("SHIPPER".equals(role)
                ? "Vui lòng bố trí người nhận hàng tại kho; chủ hàng có thể hủy chuyến khi trễ hơn 1 giờ."
                : "Điểm uy tín đã được cập nhật theo mức giao trễ.");
        send(userId, tripId, "late:" + tripId + ":tier:" + tier + ":user:" + userId, title, message);
    }

    public void notifyCancelled(UUID userId, UUID tripId) {
        send(userId, tripId, "late-cancel:" + tripId + ":user:" + userId,
                "Chuyến hàng đã bị hủy do giao trễ", "Chủ hàng đã hủy chuyến " + tripId + " vì xe trễ hơn 1 giờ.");
    }

    private void send(UUID userId, UUID tripId, String dedupeKey, String title, String message) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Internal-Token", token);
        Map<String, Object> body = Map.of("userId", userId.toString(), "title", title, "message", message,
                "referenceId", tripId.toString(), "type", "LATE_DELIVERY", "dedupeKey", dedupeKey);
        restTemplate.exchange(notificationServiceUrl.replaceAll("/$", "") + "/internal/create",
                HttpMethod.POST, new HttpEntity<>(body, headers), Map.class);
    }
}
