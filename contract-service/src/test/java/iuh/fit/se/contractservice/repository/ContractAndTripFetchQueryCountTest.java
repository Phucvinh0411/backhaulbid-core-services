package iuh.fit.se.contractservice.repository;

import iuh.fit.se.contractservice.domain.entity.Contract;
import iuh.fit.se.contractservice.domain.entity.ContractSignature;
import iuh.fit.se.contractservice.domain.entity.TrackingLog;
import iuh.fit.se.contractservice.domain.entity.Trip;
import iuh.fit.se.contractservice.domain.enums.AccountRole;
import iuh.fit.se.contractservice.domain.enums.ContractStatus;
import iuh.fit.se.contractservice.domain.enums.TripStatus;
import iuh.fit.se.contractservice.mapper.ContractMapper;
import iuh.fit.se.contractservice.mapper.ContractMapperImpl;
import iuh.fit.se.contractservice.mapper.TripMapper;
import iuh.fit.se.contractservice.mapper.TripMapperImpl;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import({ContractMapperImpl.class, TripMapperImpl.class})
@TestPropertySource(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.generate_statistics=true",
        "spring.datasource.url=jdbc:h2:mem:contract_nplus1;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "eureka.client.enabled=false",
        "app.mail.enabled=false"
})
class ContractAndTripFetchQueryCountTest {

    @Autowired private ContractRepository contractRepository;
    @Autowired private TripRepository tripRepository;
    @Autowired private ContractMapper contractMapper;
    @Autowired private TripMapper tripMapper;
    @Autowired private EntityManager entityManager;
    @Autowired private EntityManagerFactory entityManagerFactory;

    private UUID seedSample(int count) {
        UUID shipperId = UUID.randomUUID();
        UUID carrierId = UUID.randomUUID();
        for (int i = 0; i < count; i++) {
            Trip trip = Trip.builder()
                    .shipperId(shipperId)
                    .carrierId(carrierId)
                    .vehicleId(UUID.randomUUID())
                    .pickupLocation("Kho A " + i)
                    .deliveryLocation("Kho B " + i)
                    .agreedPrice(new BigDecimal("5000000.00"))
                    .status(TripStatus.WAITING_PICKUP)
                    .build();
            TrackingLog log = TrackingLog.builder()
                    .trip(trip)
                    .latitude(10.77 + i * 0.01)
                    .longitude(106.69)
                    .status(TripStatus.WAITING_PICKUP)
                    .recordedAt(Instant.now())
                    .build();
            trip.getTrackingLogs().add(log);
            entityManager.persist(trip);

            Contract contract = Contract.builder()
                    .trip(trip)
                    .shipperId(shipperId)
                    .carrierId(carrierId)
                    .contractCode("CTR-TEST-" + i + "-" + UUID.randomUUID().toString().substring(0, 6))
                    .status(ContractStatus.WAITING_SIGNATURE)
                    .build();
            contract.addSignature(ContractSignature.builder()
                    .accountId(carrierId)
                    .role(AccountRole.CARRIER)
                    .signedAt(Instant.now())
                    .build());
            entityManager.persist(contract);
        }
        entityManager.flush();
        entityManager.clear();
        return shipperId;
    }

    @Test
    void listContractsAvoidsNPlusOneQueries() {
        UUID shipperId = seedSample(4);
        Statistics stats = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        stats.clear();

        var contracts = contractMapper.toResponses(contractRepository.findByShipperIdOrderByCreatedAtDesc(shipperId));

        assertThat(contracts).hasSize(4);
        assertThat(contracts.get(0).trip()).isNotNull();
        assertThat(contracts.get(0).signatures()).hasSize(1);
        assertThat(contracts.get(0).trip().latestTracking()).isNotNull();
        assertThat(stats.getPrepareStatementCount())
                .as("Contract list + trip + signatures + latestTracking must execute in at most 2 SQL statements")
                .isLessThanOrEqualTo(2);
    }

    @Test
    void listTripsAvoidsNPlusOneQueries() {
        UUID shipperId = seedSample(4);
        Statistics stats = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        stats.clear();

        var trips = tripMapper.toResponses(tripRepository.findByShipperIdOrderByCreatedAtDesc(shipperId));

        assertThat(trips).hasSize(4);
        assertThat(trips.get(0).latestTracking()).isNotNull();
        assertThat(stats.getPrepareStatementCount())
                .as("Trip list + inverse contract + latestTracking must execute in 1 SQL statement")
                .isEqualTo(1);
    }
}
