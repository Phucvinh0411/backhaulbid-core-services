package iuh.fit.se.contractservice.service;

import iuh.fit.se.contractservice.domain.entity.Complaint;
import iuh.fit.se.contractservice.domain.entity.Trip;
import iuh.fit.se.contractservice.domain.enums.AccountRole;
import iuh.fit.se.contractservice.domain.enums.ComplaintDecision;
import iuh.fit.se.contractservice.domain.enums.ComplaintStatus;
import iuh.fit.se.contractservice.dto.AddComplaintMessageRequest;
import iuh.fit.se.contractservice.dto.CreateComplaintRequest;
import iuh.fit.se.contractservice.dto.ResolveComplaintRequest;
import iuh.fit.se.contractservice.repository.ComplaintRepository;
import iuh.fit.se.contractservice.repository.TripRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ComplaintServiceTest {
    @Mock
    private ComplaintRepository complaintRepository;

    @Mock
    private TripRepository tripRepository;

    @Test
    void partyCanCreateComplaintForTheirTrip() {
        UUID shipperId = UUID.randomUUID();
        UUID carrierId = UUID.randomUUID();
        UUID tripId = UUID.randomUUID();
        Trip trip = Trip.builder().id(tripId).shipperId(shipperId).carrierId(carrierId).build();
        when(tripRepository.findById(tripId)).thenReturn(Optional.of(trip));
        when(complaintRepository.save(any(Complaint.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var result = new ComplaintService(complaintRepository, tripRepository).create(
                shipperId,
                AccountRole.SHIPPER,
                new CreateComplaintRequest(
                        tripId,
                        "Damaged goods",
                        "The shipment arrived damaged.",
                        "https://media.example.test/complaints/damaged-goods.jpg"));

        assertThat(result.status()).isEqualTo(ComplaintStatus.PENDING);
        assertThat(result.reporterId()).isEqualTo(shipperId);
        assertThat(result.respondentId()).isEqualTo(carrierId);
        assertThat(result.evidenceUrl()).isEqualTo("https://media.example.test/complaints/damaged-goods.jpg");
    }

    @Test
    void adminDecisionResolvesComplaintAndAddsAuditMessage() {
        UUID complaintId = UUID.randomUUID();
        Complaint complaint = Complaint.builder()
                .id(complaintId)
                .reporterId(UUID.randomUUID())
                .respondentId(UUID.randomUUID())
                .title("Complaint")
                .description("Details")
                .status(ComplaintStatus.PROCESSING)
                .build();
        when(complaintRepository.findById(complaintId)).thenReturn(Optional.of(complaint));
        when(complaintRepository.save(any(Complaint.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var result = new ComplaintService(complaintRepository, tripRepository).resolve(
                UUID.randomUUID(),
                AccountRole.ADMIN,
                complaintId,
                new ResolveComplaintRequest(ComplaintDecision.CARRIER_WIN, "Evidence supports the carrier."));

        assertThat(result.status()).isEqualTo(ComplaintStatus.RESOLVED);
        assertThat(result.decision()).isEqualTo(ComplaintDecision.CARRIER_WIN);
        assertThat(result.messages()).hasSize(1);
    }

    @Test
    void create_adminRole_returns403() {
        assertThatThrownBy(() -> new ComplaintService(complaintRepository, tripRepository).create(
                UUID.randomUUID(),
                AccountRole.ADMIN,
                new CreateComplaintRequest(UUID.randomUUID(), "Title", "Description")))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
                .hasMessageContaining("Shipper or carrier role required");
    }

    @Test
    void create_unrelatedAccount_returns403() {
        UUID tripId = UUID.randomUUID();
        Trip trip = Trip.builder()
                .id(tripId)
                .shipperId(UUID.randomUUID())
                .carrierId(UUID.randomUUID())
                .build();
        when(tripRepository.findById(tripId)).thenReturn(Optional.of(trip));

        assertThatThrownBy(() -> new ComplaintService(complaintRepository, tripRepository).create(
                UUID.randomUUID(),
                AccountRole.CARRIER,
                new CreateComplaintRequest(tripId, "Title", "Description")))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
                .hasMessageContaining("You do not have access to this trip");
    }

    @Test
    void addMessage_accessibleParty_trimsMessageAndMovesComplaintToProcessing() {
        UUID reporterId = UUID.randomUUID();
        UUID complaintId = UUID.randomUUID();
        Complaint complaint = Complaint.builder()
                .id(complaintId)
                .reporterId(reporterId)
                .respondentId(UUID.randomUUID())
                .title("Complaint")
                .description("Details")
                .status(ComplaintStatus.PENDING)
                .build();
        when(complaintRepository.findById(complaintId)).thenReturn(Optional.of(complaint));
        when(complaintRepository.save(any(Complaint.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var actual = new ComplaintService(complaintRepository, tripRepository).addMessage(
                reporterId,
                AccountRole.SHIPPER,
                complaintId,
                new AddComplaintMessageRequest("  Additional evidence  "));

        assertThat(actual.status()).isEqualTo(ComplaintStatus.PROCESSING);
        assertThat(complaint.getMessages()).hasSize(1);
        assertThat(complaint.getMessages().get(0).getMessage()).isEqualTo("Additional evidence");
    }

    @Test
    void addMessage_closedComplaint_returns409() {
        UUID complaintId = UUID.randomUUID();
        Complaint complaint = Complaint.builder()
                .id(complaintId)
                .reporterId(UUID.randomUUID())
                .respondentId(UUID.randomUUID())
                .title("Closed complaint")
                .description("Details")
                .status(ComplaintStatus.RESOLVED)
                .build();
        when(complaintRepository.findById(complaintId)).thenReturn(Optional.of(complaint));

        assertThatThrownBy(() -> new ComplaintService(complaintRepository, tripRepository).addMessage(
                complaint.getReporterId(),
                AccountRole.SHIPPER,
                complaintId,
                new AddComplaintMessageRequest("Should not be appended")))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
                .hasMessageContaining("Complaint is already closed");
        assertThat(complaint.getMessages()).isEmpty();
    }

    @Test
    void get_unrelatedAccount_returns403() {
        UUID complaintId = UUID.randomUUID();
        Complaint complaint = Complaint.builder()
                .id(complaintId)
                .reporterId(UUID.randomUUID())
                .respondentId(UUID.randomUUID())
                .title("Complaint")
                .description("Details")
                .status(ComplaintStatus.PENDING)
                .build();
        when(complaintRepository.findById(complaintId)).thenReturn(Optional.of(complaint));

        assertThatThrownBy(() -> new ComplaintService(complaintRepository, tripRepository).get(
                UUID.randomUUID(), AccountRole.CARRIER, complaintId))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
                .hasMessageContaining("You do not have access to this complaint");
    }

    @Test
    void resolve_nonAdminRole_returns403() {
        assertThatThrownBy(() -> new ComplaintService(complaintRepository, tripRepository).resolve(
                UUID.randomUUID(),
                AccountRole.CARRIER,
                UUID.randomUUID(),
                new ResolveComplaintRequest(ComplaintDecision.CARRIER_WIN, "Resolution")))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
                .hasMessageContaining("Admin role required");
    }

    @Test
    void resolve_closedComplaint_returns409() {
        UUID complaintId = UUID.randomUUID();
        Complaint complaint = Complaint.builder()
                .id(complaintId)
                .reporterId(UUID.randomUUID())
                .respondentId(UUID.randomUUID())
                .title("Already rejected")
                .description("Details")
                .status(ComplaintStatus.REJECTED)
                .build();
        when(complaintRepository.findById(complaintId)).thenReturn(Optional.of(complaint));

        assertThatThrownBy(() -> new ComplaintService(complaintRepository, tripRepository).resolve(
                UUID.randomUUID(),
                AccountRole.ADMIN,
                complaintId,
                new ResolveComplaintRequest(ComplaintDecision.CARRIER_WIN, "Should not overwrite")))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
                .hasMessageContaining("Complaint is already closed");
        assertThat(complaint.getMessages()).isEmpty();
    }
}
