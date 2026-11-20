package iuh.fit.se.contractservice.service;

import iuh.fit.se.contractservice.config.RabbitMQConfig;
import iuh.fit.se.contractservice.domain.enums.ContractStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.AmqpConnectException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class AwardEventPublisherTest {

    private static final Instant NOW = Instant.parse("2026-11-18T00:00:00Z");

    @Mock
    private RabbitTemplate rabbitTemplate;

    private AwardEventPublisher publisher;

    @BeforeEach
    void setUp() {
        publisher = new AwardEventPublisher(rabbitTemplate, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @AfterEach
    void tearDown() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
        TransactionSynchronizationManager.setActualTransactionActive(false);
    }

    @Test
    @SuppressWarnings("unchecked")
    void publishesAwardUpdatedEnvelopeImmediatelyWhenNoActiveTransaction() {
        UUID contractId = UUID.randomUUID();
        UUID tripId = UUID.randomUUID();

        publisher.publishAfterCommit("attempt-123", contractId, tripId, ContractStatus.SIGNED);

        ArgumentCaptor<Map<String, Object>> envelopeCaptor = ArgumentCaptor.forClass(Map.class);
        verify(rabbitTemplate).convertAndSend(
                eq(RabbitMQConfig.CONTRACT_EXCHANGE),
                eq(RabbitMQConfig.AWARD_UPDATED_ROUTING),
                envelopeCaptor.capture());

        Map<String, Object> envelope = envelopeCaptor.getValue();
        assertThat(envelope.get("pattern")).isEqualTo("contract.award.updated");
        Map<String, Object> data = (Map<String, Object>) envelope.get("data");
        assertThat(data)
                .containsEntry("awardAttemptId", "attempt-123")
                .containsEntry("contractId", contractId.toString())
                .containsEntry("tripId", tripId.toString())
                .containsEntry("status", "SIGNED")
                .containsEntry("occurredAt", NOW.toString());
    }

    @Test
    void defersPublishUntilAfterTransactionCommitAndSkipsBeforeCommit() {
        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);

        UUID contractId = UUID.randomUUID();
        UUID tripId = UUID.randomUUID();

        publisher.publishAfterCommit("attempt-456", contractId, tripId, ContractStatus.WAITING_SIGNATURE);

        verifyNoInteractions(rabbitTemplate);
        assertThat(TransactionSynchronizationManager.getSynchronizations()).hasSize(1);

        for (TransactionSynchronization sync : TransactionSynchronizationManager.getSynchronizations()) {
            sync.afterCommit();
        }

        verify(rabbitTemplate).convertAndSend(
                eq(RabbitMQConfig.CONTRACT_EXCHANGE),
                eq(RabbitMQConfig.AWARD_UPDATED_ROUTING),
                anyMap());
    }

    @Test
    void doesNotPublishWhenTransactionRollsBack() {
        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);

        publisher.publishAfterCommit("attempt-rollback", UUID.randomUUID(), UUID.randomUUID(), ContractStatus.SIGNED);

        for (TransactionSynchronization sync : TransactionSynchronizationManager.getSynchronizations()) {
            sync.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);
        }

        verifyNoInteractions(rabbitTemplate);
    }

    @Test
    void swallowsRabbitMqFailureSoContractSigningIsNeverBroken() {
        doThrow(new AmqpConnectException(new RuntimeException("broker down")))
                .when(rabbitTemplate)
                .convertAndSend(eq(RabbitMQConfig.CONTRACT_EXCHANGE), eq(RabbitMQConfig.AWARD_UPDATED_ROUTING), anyMap());

        assertThatCode(() -> publisher.publishAfterCommit(
                "attempt-broker-down",
                UUID.randomUUID(),
                UUID.randomUUID(),
                ContractStatus.SIGNED)).doesNotThrowAnyException();
    }
}
