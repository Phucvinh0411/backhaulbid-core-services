package iuh.fit.se.contractservice.service;

import iuh.fit.se.contractservice.domain.entity.Complaint;
import iuh.fit.se.contractservice.domain.entity.ComplaintMessage;
import iuh.fit.se.contractservice.domain.entity.Contract;
import iuh.fit.se.contractservice.domain.entity.TrackingLog;
import iuh.fit.se.contractservice.domain.entity.Trip;
import iuh.fit.se.contractservice.domain.entity.TripHandover;
import iuh.fit.se.contractservice.domain.enums.AccountRole;
import iuh.fit.se.contractservice.domain.enums.ComplaintCategory;
import iuh.fit.se.contractservice.domain.enums.ComplaintStatus;
import iuh.fit.se.contractservice.domain.enums.ContractStatus;
import iuh.fit.se.contractservice.domain.enums.TripStatus;
import iuh.fit.se.contractservice.dto.ContractResponse;
import iuh.fit.se.contractservice.dto.TripHandoverResponse;
import iuh.fit.se.contractservice.dto.TripResponse;
import jakarta.persistence.EntityManager;
import iuh.fit.se.contractservice.mapper.ComplaintMapperImpl;
import iuh.fit.se.contractservice.mapper.ContractMapper;
import iuh.fit.se.contractservice.mapper.ContractMapperImpl;
import iuh.fit.se.contractservice.mapper.TripMapper;
import iuh.fit.se.contractservice.mapper.TripMapperImpl;
import iuh.fit.se.contractservice.repository.ComplaintRepository;
import iuh.fit.se.contractservice.repository.ContractRepository;
import iuh.fit.se.contractservice.repository.TripHandoverRepository;
import iuh.fit.se.contractservice.repository.TripRepository;
import iuh.fit.se.contractservice.service.driveraccess.DriverAccessPolicy;
import iuh.fit.se.contractservice.service.driveraccess.DriverGrantService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies lazy associations mapped after service transactions close do not throw
 * {@code LazyInitializationException} when {@code spring.jpa.open-in-view=false}.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@TestPropertySource(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.datasource.url=jdbc:h2:mem:osiv_lazy_regression;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.open-in-view=false"
})
@Import({
        ContractService.class,
        TripHandoverService.class,
        ComplaintService.class,
        DriverAccessPolicy.class,
        TripMapperImpl.class,
        ContractMapperImpl.class,
        ComplaintMapperImpl.class,
        OpenInViewDisabledLazyFetchRegressionTest.TestBeans.class
})
class OpenInViewDisabledLazyFetchRegressionTest {

    @Autowired private TransactionTemplate tx;
    @Autowired private EntityManager entityManager;
    @Autowired private TripRepository tripRepository;
    @Autowired private ContractRepository contractRepository;
    @Autowired private TripHandoverRepository tripHandoverRepository;
    @Autowired private ComplaintRepository complaintRepository;
    @Autowired private ContractService contractService;
    @Autowired private TripHandoverService tripHandoverService;
    @Autowired private ComplaintService complaintService;
    @Autowired private ContractMapper contractMapper;
    @Autowired private TripMapper tripMapper;

    @Test
    void contractsHandoversAndComplaintSubscribeMapCleanlyOutsideTransaction() {
        UUID shipperId = UUID.randomUUID();
        UUID carrierId = UUID.randomUUID();
        UUID vehicleId = UUID.randomUUID();
        Instant now = Instant.now();

        UUID[] ids = new UUID[4]; // [tripId, contractId, complaintId, waitingTripId]
        tx.executeWithoutResult(status -> {
            Trip trip = Trip.builder()
                    .shipperId(shipperId)
                    .carrierId(carrierId)
                    .vehicleId(vehicleId)
                    .pickupLocation("Kho A")
                    .deliveryLocation("Kho B")
                    .agreedPrice(new BigDecimal("15000000.00"))
                    .status(TripStatus.IN_TRANSIT)
                    .routeVersion(1L)
                    .assignmentPinAttempts(0)
                    .build();
            TrackingLog log = TrackingLog.builder()
                    .trip(trip)
                    .latitude(10.77)
                    .longitude(106.69)
                    .status(TripStatus.IN_TRANSIT)
                    .recordedAt(now)
                    .build();
            trip.getTrackingLogs().add(log);
            Trip savedTrip = tripRepository.saveAndFlush(trip);

            Contract savedContract = contractRepository.saveAndFlush(Contract.builder()
                    .trip(savedTrip)
                    .shipperId(shipperId)
                    .carrierId(carrierId)
                    .contractCode("HD-OSIV-" + savedTrip.getId().toString().substring(0, 8))
                    .status(ContractStatus.SIGNED)
                    .build());

            tripHandoverRepository.saveAndFlush(TripHandover.builder()
                    .id(UUID.randomUUID())
                    .tripId(savedTrip.getId())
                    .cargoCategory("ELECTRONICS")
                    .grossWeightKg(new BigDecimal("1200.00"))
                    .conditionStatus("INTACT")
                    .recordedBy(carrierId)
                    .recordedByType("CARRIER")
                    .recordedAt(now)
                    .updatedAt(now)
                    .photoKeys(List.of("handovers/photo-1.jpg", "handovers/photo-2.jpg"))
                    .build());

            Complaint complaint = Complaint.builder()
                    .tripId(savedTrip.getId())
                    .reporterId(shipperId)
                    .respondentId(carrierId)
                    .category(ComplaintCategory.OTHER)
                    .title("Test lazy messages")
                    .description("Ensure Complaint.messages loads cleanly on subscribe")
                    .status(ComplaintStatus.PENDING)
                    .build();
            complaint.getMessages().add(ComplaintMessage.builder()
                    .complaint(complaint)
                    .senderId(shipperId)
                    .senderRole(AccountRole.SHIPPER)
                    .message("First message")
                    .build());
            Complaint savedComplaint = complaintRepository.saveAndFlush(complaint);

            ids[0] = savedTrip.getId();
            ids[1] = savedContract.getId();
            ids[2] = savedComplaint.getId();

            Trip waitingTrip = tripRepository.saveAndFlush(Trip.builder()
                    .shipperId(shipperId)
                    .carrierId(carrierId)
                    .vehicleId(vehicleId)
                    .pickupLocation("Kho C")
                    .deliveryLocation("Kho D")
                    .agreedPrice(new BigDecimal("12000000.00"))
                    .status(TripStatus.WAITING_PICKUP)
                    .routeVersion(1L)
                    .assignmentPinAttempts(0)
                    .build());
            contractRepository.saveAndFlush(Contract.builder()
                    .trip(waitingTrip)
                    .shipperId(shipperId)
                    .carrierId(carrierId)
                    .contractCode("HD-WAIT-" + waitingTrip.getId().toString().substring(0, 8))
                    .status(ContractStatus.WAITING_SIGNATURE)
                    .build());
            ids[3] = waitingTrip.getId();
        });

        List<ContractResponse> contracts = contractMapper.toResponses(
                contractService.list(shipperId, AccountRole.SHIPPER, null));
        assertThat(contracts).hasSize(2);
        ContractResponse inTransitContract = contracts.stream()
                .filter(c -> c.id().equals(ids[1]))
                .findFirst()
                .orElseThrow();
        assertThat(inTransitContract.trip().latestTracking()).isNotNull();
        assertThat(inTransitContract.trip().latestTracking().status()).isEqualTo(TripStatus.IN_TRANSIT);
        assertThat(inTransitContract.trip().contractStatus()).isEqualTo(ContractStatus.SIGNED);

        ContractResponse singleContract = contractMapper.toResponse(
                contractService.get(shipperId, AccountRole.SHIPPER, ids[1]));
        assertThat(singleContract.trip().latestTracking()).isNotNull();

        List<TripHandoverResponse> handovers = tripMapper.toHandoverResponses(
                tripHandoverService.view(shipperId, AccountRole.SHIPPER, ids[0]));
        assertThat(handovers).hasSize(1);
        assertThat(handovers.get(0).photoPaths()).hasSize(2);

        Trip detachedWaitingTrip = tx.execute(status -> {
            Trip t = entityManager.find(Trip.class, ids[3]);
            t.getTrackingLogs().size();
            if (t.getContract() != null) t.getContract().getStatus();
            return t;
        });
        TripResponse routeUpdatedTrip = tripMapper.toResponse(detachedWaitingTrip);
        assertThat(routeUpdatedTrip.contractStatus()).isEqualTo(ContractStatus.WAITING_SIGNATURE);

        SseEmitter emitter = complaintService.subscribe(shipperId, AccountRole.SHIPPER, ids[2]);
        assertThat(emitter).isNotNull();
        emitter.complete();
    }

    @TestConfiguration
    static class TestBeans {
        @Bean Clock clock() { return Clock.systemUTC(); }
        @Bean IdentityVerificationClient identityVerificationClient() { return Mockito.mock(IdentityVerificationClient.class); }
        @Bean ContractSigningOtpService contractSigningOtpService() { return Mockito.mock(ContractSigningOtpService.class); }
        @Bean ContractOtpMailSender contractOtpMailSender() { return Mockito.mock(ContractOtpMailSender.class); }
        @Bean AwardEventPublisher awardEventPublisher() { return Mockito.mock(AwardEventPublisher.class); }
        @Bean DriverGrantService driverGrantService() { return Mockito.mock(DriverGrantService.class); }
        @Bean RabbitTemplate rabbitTemplate() { return Mockito.mock(RabbitTemplate.class); }
    }
}
