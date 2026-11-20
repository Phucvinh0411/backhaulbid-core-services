package iuh.fit.se.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HttpGoogleTokenVerifierTest {

    private static final Instant NOW = Instant.parse("2026-11-13T06:00:00Z");
    private HttpServer server;
    private String endpointUrl;
    private final AtomicInteger statusCode = new AtomicInteger(200);
    private final AtomicReference<String> responseBody = new AtomicReference<>("{}");

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/tokeninfo", exchange -> {
            byte[] bytes = responseBody.get().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(statusCode.get(), bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });
        server.start();
        endpointUrl = "http://127.0.0.1:" + server.getAddress().getPort() + "/tokeninfo";
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    void verifiesValidTokenAndNormalizesFields() {
        responseBody.set("""
                {
                  "iss": "https://accounts.google.com",
                  "aud": "client-123.apps.googleusercontent.com",
                  "sub": "10987654321",
                  "email": "User.Name@Gmail.com",
                  "email_verified": "true",
                  "name": "Nguyen Van Test",
                  "picture": "http://insecure.example.com/pic.jpg",
                  "exp": "%d"
                }
                """.formatted(NOW.plusSeconds(600).getEpochSecond()));

        HttpGoogleTokenVerifier verifier = new HttpGoogleTokenVerifier(
                HttpClient.newHttpClient(),
                new ObjectMapper(),
                "client-123.apps.googleusercontent.com",
                endpointUrl,
                Clock.fixed(NOW, ZoneOffset.UTC));

        GoogleUserInfo user = verifier.verify("valid-token");

        assertThat(user.sub()).isEqualTo("10987654321");
        assertThat(user.email()).isEqualTo("user.name@gmail.com");
        assertThat(user.fullName()).isEqualTo("Nguyen Van Test");
        assertThat(user.avatarUrl()).isNull();
    }

    @Test
    void rejectsBlankClientIdWrongAudienceUnverifiedEmailAndExpiredToken() {
        HttpGoogleTokenVerifier unconfigured = new HttpGoogleTokenVerifier(
                HttpClient.newHttpClient(),
                new ObjectMapper(),
                "",
                endpointUrl,
                Clock.fixed(NOW, ZoneOffset.UTC));
        assertThatThrownBy(() -> unconfigured.verify("tok"))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);

        HttpGoogleTokenVerifier verifier = new HttpGoogleTokenVerifier(
                HttpClient.newHttpClient(),
                new ObjectMapper(),
                "expected-client-id",
                endpointUrl,
                Clock.fixed(NOW, ZoneOffset.UTC));

        responseBody.set("""
                {"iss":"https://accounts.google.com","aud":"other-client","sub":"1","email":"a@b.com","email_verified":"true","exp":"%d"}
                """.formatted(NOW.plusSeconds(600).getEpochSecond()));
        assertThatThrownBy(() -> verifier.verify("tok"))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);

        responseBody.set("""
                {"iss":"https://accounts.google.com","aud":"expected-client-id","sub":"1","email":"a@b.com","email_verified":"false","exp":"%d"}
                """.formatted(NOW.plusSeconds(600).getEpochSecond()));
        assertThatThrownBy(() -> verifier.verify("tok"))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);

        responseBody.set("""
                {"iss":"https://accounts.google.com","aud":"expected-client-id","sub":"1","email":"a@b.com","email_verified":"true","exp":"%d"}
                """.formatted(NOW.minusSeconds(10).getEpochSecond()));
        assertThatThrownBy(() -> verifier.verify("tok"))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
