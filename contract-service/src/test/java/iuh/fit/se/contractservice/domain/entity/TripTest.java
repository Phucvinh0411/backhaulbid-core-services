package iuh.fit.se.contractservice.domain.entity;

import iuh.fit.se.contractservice.domain.enums.TripStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TripTest {

    @Test
    void completeTrip_deliveredTrip_setsCompletedStatus() {
        // Given
        Trip trip = Trip.builder().status(TripStatus.DELIVERED).build();

        // When
        trip.completeTrip();

        // Then
        assertThat(trip.getStatus()).isEqualTo(TripStatus.COMPLETED);
    }

    @Test
    void completeTrip_tripNotDelivered_throwsIllegalStateException() {
        // Given
        Trip trip = Trip.builder().status(TripStatus.IN_TRANSIT).build();

        // When / Then
        assertThatThrownBy(trip::completeTrip)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Trip must be delivered before completion");
    }
}
