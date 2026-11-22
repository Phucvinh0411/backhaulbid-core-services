package iuh.fit.se.fleetservice.repository;

import iuh.fit.se.fleetservice.domain.entity.Vehicle;
import iuh.fit.se.fleetservice.domain.entity.VehicleDocument;
import iuh.fit.se.fleetservice.domain.entity.VehicleVerification;
import iuh.fit.se.fleetservice.domain.enums.VehicleDocumentType;
import iuh.fit.se.fleetservice.domain.enums.VehicleStatus;
import iuh.fit.se.fleetservice.domain.enums.VehicleType;
import iuh.fit.se.fleetservice.domain.enums.VerificationStatus;
import iuh.fit.se.fleetservice.mapper.VehicleMapper;
import iuh.fit.se.fleetservice.mapper.VehicleMapperImpl;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import(VehicleMapperImpl.class)
@TestPropertySource(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.generate_statistics=true",
        "spring.datasource.url=jdbc:h2:mem:fleet_nplus1;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "eureka.client.enabled=false"
})
class VehicleFetchQueryCountTest {

    @Autowired private VehicleRepository vehicleRepository;
    @Autowired private VehicleMapper vehicleMapper;
    @Autowired private EntityManager entityManager;
    @Autowired private EntityManagerFactory entityManagerFactory;

    @Test
    void listCarrierVehiclesFetchesVerificationAndDocumentsInOneQuery() {
        UUID carrierId = UUID.randomUUID();
        for (int i = 0; i < 4; i++) {
            Vehicle vehicle = Vehicle.builder()
                    .carrierId(carrierId)
                    .licensePlate("51C-100.0" + i)
                    .vehicleType(VehicleType.TRUCK_MEDIUM)
                    .payloadCapacity(new BigDecimal("8.00"))
                    .status(VehicleStatus.VERIFIED)
                    .build();
            vehicle.setVerification(VehicleVerification.builder()
                    .vehicle(vehicle)
                    .status(VerificationStatus.VERIFIED)
                    .build());
            vehicle.getDocuments().add(VehicleDocument.builder()
                    .vehicle(vehicle)
                    .documentType(VehicleDocumentType.REGISTRATION)
                    .documentUrl("https://backhaulbid-s3.s3.ap-southeast-2.amazonaws.com/vehicles/registration/" + UUID.randomUUID() + ".png")
                    .status(VerificationStatus.VERIFIED)
                    .build());
            entityManager.persist(vehicle);
        }
        entityManager.flush();
        entityManager.clear();

        Statistics stats = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        stats.clear();

        var responses = vehicleMapper.toResponses(vehicleRepository.findByCarrierIdOrderByLicensePlateAsc(carrierId));

        assertThat(responses).hasSize(4);
        assertThat(responses.get(0).registrationDocumentUrl()).isNotBlank();
        assertThat(responses.get(0).verificationStatus()).isEqualTo(VerificationStatus.VERIFIED);
        assertThat(stats.getPrepareStatementCount())
                .as("Vehicle list + verification + documents must execute in 1 SQL query")
                .isEqualTo(1);
    }
}
