package iuh.fit.se.fleetservice.domain.entity;

import iuh.fit.se.fleetservice.domain.enums.VehicleStatus;
import iuh.fit.se.fleetservice.domain.enums.VehicleType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class VehicleTest {

    @Test
    void isEligible_verifiedMatchingVehicleWithEnoughCapacity_returnsTrue() {
        // Given
        Vehicle vehicle = Vehicle.builder()
                .vehicleType(VehicleType.TRUCK_HEAVY)
                .payloadCapacity(new BigDecimal("15.00"))
                .status(VehicleStatus.VERIFIED)
                .build();

        // When
        boolean actualEligible = vehicle.isEligible(
                new BigDecimal("12.00"), VehicleType.TRUCK_HEAVY);

        // Then
        assertThat(actualEligible).isTrue();
    }

    @Test
    void isEligible_payloadBelowRequirement_returnsFalse() {
        // Given
        Vehicle vehicle = Vehicle.builder()
                .vehicleType(VehicleType.TRUCK_MEDIUM)
                .payloadCapacity(new BigDecimal("5.00"))
                .status(VehicleStatus.VERIFIED)
                .build();

        // When
        boolean actualEligible = vehicle.isEligible(
                new BigDecimal("8.00"), VehicleType.TRUCK_MEDIUM);

        // Then
        assertThat(actualEligible).isFalse();
    }

    @Test
    void isEligible_unverifiedVehicle_returnsFalse() {
        // Given
        Vehicle vehicle = Vehicle.builder()
                .vehicleType(VehicleType.CONTAINER_TRACTOR)
                .payloadCapacity(new BigDecimal("30.00"))
                .status(VehicleStatus.PENDING)
                .build();

        // When
        boolean actualEligible = vehicle.isEligible(
                new BigDecimal("20.00"), VehicleType.CONTAINER_TRACTOR);

        // Then
        assertThat(actualEligible).isFalse();
    }
}
