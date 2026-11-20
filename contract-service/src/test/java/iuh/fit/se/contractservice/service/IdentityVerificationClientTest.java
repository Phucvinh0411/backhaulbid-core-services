package iuh.fit.se.contractservice.service;

import org.junit.jupiter.api.Test;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class IdentityVerificationClientTest {

    @Test
    void checksVerificationAndSigningContactOverInternalToken() {
        IdentityVerificationClient client = new IdentityVerificationClient(
                new RestTemplateBuilder(),
                "http://identity-service:8080",
                "internal-secret");
        RestTemplate restTemplate = (RestTemplate) ReflectionTestUtils.getField(client, "client");
        MockRestServiceServer server = MockRestServiceServer.createServer(restTemplate);

        UUID accountId = UUID.randomUUID();
        server.expect(requestTo("http://identity-service:8080/internal/accounts/" + accountId + "/verification-status"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("X-Internal-Token", "internal-secret"))
                .andRespond(withSuccess("{\"fullyVerified\":true}", MediaType.APPLICATION_JSON));

        server.expect(requestTo("http://identity-service:8080/internal/accounts/" + accountId + "/verification-status"))
                .andRespond(withSuccess("{\"fullyVerified\":false}", MediaType.APPLICATION_JSON));

        server.expect(requestTo("http://identity-service:8080/internal/accounts/" + accountId + "/signing-contact"))
                .andExpect(header("X-Internal-Token", "internal-secret"))
                .andRespond(withSuccess("{\"email\":\"owner@carrier.vn\",\"emailVerified\":true,\"fullName\":\"Tran Van E\"}", MediaType.APPLICATION_JSON));

        assertThatCode(() -> client.assertVerified(accountId)).doesNotThrowAnyException();
        assertThatThrownBy(() -> client.assertVerified(accountId))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);

        var contact = client.fetchSigningContact(accountId);
        assertThat(contact.email()).isEqualTo("owner@carrier.vn");
        assertThat(contact.emailVerified()).isTrue();
        assertThat(contact.fullName()).isEqualTo("Tran Van E");

        server.verify();
    }
}
