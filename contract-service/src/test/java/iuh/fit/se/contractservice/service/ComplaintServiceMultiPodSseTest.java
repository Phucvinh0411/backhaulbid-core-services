package iuh.fit.se.contractservice.service;

import iuh.fit.se.contractservice.config.RabbitMQConfig;
import iuh.fit.se.contractservice.domain.entity.Complaint;
import iuh.fit.se.contractservice.domain.enums.AccountRole;
import iuh.fit.se.contractservice.domain.enums.ComplaintCategory;
import iuh.fit.se.contractservice.domain.enums.ComplaintStatus;
import iuh.fit.se.contractservice.dto.AddComplaintMessageRequest;
import iuh.fit.se.contractservice.dto.ComplaintResponse;
import iuh.fit.se.contractservice.mapper.ComplaintMapper;
import iuh.fit.se.contractservice.repository.ComplaintRepository;
import iuh.fit.se.contractservice.repository.TripRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ComplaintServiceMultiPodSseTest {

    @Mock private ComplaintRepository complaintRepository;
    @Mock private TripRepository tripRepository;
    @Mock private ComplaintMapper complaintMapper;
    @Mock private RabbitTemplate rabbitTemplate;

    @Test
    void broadcastsComplaintUpdatesOverRabbitFanoutAndDeliversRemoteEventsToLocalSubscribers() {
        UUID complaintId = UUID.randomUUID();
        UUID shipperId = UUID.randomUUID();
        UUID carrierId = UUID.randomUUID();
        Complaint complaint = Complaint.builder()
                .id(complaintId)
                .tripId(UUID.randomUUID())
                .reporterId(shipperId)
                .respondentId(carrierId)
                .category(ComplaintCategory.OTHER)
                .title("Test")
                .description("Desc")
                .status(ComplaintStatus.PENDING)
                .build();
        ComplaintResponse response = new ComplaintResponse(
                complaintId, complaint.getTripId(), shipperId, carrierId,
                ComplaintCategory.OTHER, "Test", "Desc", null,
                ComplaintStatus.PROCESSING, null, null, Instant.now(), Instant.now(), List.of());

        when(complaintRepository.findById(complaintId)).thenReturn(Optional.of(complaint));
        when(complaintRepository.save(any())).thenReturn(complaint);
        when(complaintMapper.toResponse(any())).thenReturn(response);

        ComplaintService podWriter = new ComplaintService(
                complaintRepository, tripRepository, complaintMapper, rabbitTemplate);
        ComplaintService podReader = new ComplaintService(
                complaintRepository, tripRepository, complaintMapper, rabbitTemplate);

        SseEmitter emitter = podReader.subscribe(shipperId, AccountRole.SHIPPER, complaintId);
        assertThat(emitter).isNotNull();

        podWriter.addMessage(carrierId, AccountRole.CARRIER, complaintId, new AddComplaintMessageRequest("Hello from pod 2"));

        ArgumentCaptor<ComplaintService.ComplaintSseEvent> captor =
                ArgumentCaptor.forClass(ComplaintService.ComplaintSseEvent.class);
        verify(rabbitTemplate).convertAndSend(eq(RabbitMQConfig.COMPLAINT_SSE_EXCHANGE), eq(""), captor.capture());
        assertThat(captor.getValue().complaintId()).isEqualTo(complaintId);

        // Ensure Jackson2JsonMessageConverter round-trips Instant fields cleanly over RabbitMQ
        Jackson2JsonMessageConverter converter = new Jackson2JsonMessageConverter();
        Message amqpMessage = converter.toMessage(captor.getValue(), new org.springframework.amqp.core.MessageProperties());
        Object decoded = converter.fromMessage(amqpMessage);
        assertThat(decoded).isInstanceOf(ComplaintService.ComplaintSseEvent.class);

        // Simulate Pod 1 receiving the fanout event and delivering to its local SSE subscriber
        podReader.onRemoteComplaintUpdate((ComplaintService.ComplaintSseEvent) decoded);
    }
}
