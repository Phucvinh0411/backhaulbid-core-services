package iuh.fit.se.contractservice.service;

import iuh.fit.se.contractservice.config.RabbitMQConfig;
import iuh.fit.se.contractservice.domain.enums.ContractStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Publishes auction-award contract state transitions to RabbitMQ strictly after
 * PostgreSQL transaction commit so bidding-service reconciles awards in real time
 * without seeing uncommitted state.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AwardEventPublisher {

    private final RabbitTemplate rabbitTemplate;
    private final Clock clock;

    public void publishAfterCommit(String awardAttemptId, UUID contractId, UUID tripId, ContractStatus status) {
        if (awardAttemptId == null || awardAttemptId.isBlank() || status == null) {
            return;
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("awardAttemptId", awardAttemptId);
        data.put("contractId", contractId != null ? contractId.toString() : null);
        data.put("tripId", tripId != null ? tripId.toString() : null);
        data.put("status", status.name());
        data.put("occurredAt", clock.instant().toString());

        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("pattern", RabbitMQConfig.AWARD_UPDATED_ROUTING);
        envelope.put("data", data);

        // ponytail: Spring afterCommit hook avoids a full Outbox table because bidding-service
        // keeps its 30s reconciliation sweep as the guaranteed fallback if RabbitMQ is unreachable.
        if (TransactionSynchronizationManager.isSynchronizationActive()
                && TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    send(awardAttemptId, envelope);
                }
            });
        } else {
            send(awardAttemptId, envelope);
        }
    }

    private void send(String awardAttemptId, Map<String, Object> envelope) {
        try {
            rabbitTemplate.convertAndSend(
                    RabbitMQConfig.CONTRACT_EXCHANGE,
                    RabbitMQConfig.AWARD_UPDATED_ROUTING,
                    envelope);
        } catch (Exception ex) {
            log.warn("[AwardSaga] Failed to publish contract.award.updated for attempt {}: {}",
                    awardAttemptId, ex.getMessage());
        }
    }
}
