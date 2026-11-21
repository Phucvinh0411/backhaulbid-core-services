package iuh.fit.se.contractservice.config;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Queue;

import static org.assertj.core.api.Assertions.assertThat;

class RabbitMQConfigTest {

    @Test
    void complaintSseQueueIsEphemeralAndOmitsDeprecatedMasterLocatorArgument() {
        RabbitMQConfig config = new RabbitMQConfig();

        Queue first = config.complaintSseQueue();
        Queue second = config.complaintSseQueue();

        assertThat(first.getName()).isNotBlank().isNotEqualTo(second.getName());
        assertThat(first.isDurable()).isFalse();
        assertThat(first.isExclusive()).isTrue();
        assertThat(first.isAutoDelete()).isTrue();
        assertThat(first.getArguments()).doesNotContainKey("x-queue-master-locator");
    }
}
