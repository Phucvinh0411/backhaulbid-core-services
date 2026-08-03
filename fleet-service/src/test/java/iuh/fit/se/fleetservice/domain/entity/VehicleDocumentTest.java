package iuh.fit.se.fleetservice.domain.entity;

import iuh.fit.se.fleetservice.domain.enums.VerificationStatus;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class VehicleDocumentTest {

    @Test
    void isValidOn_verifiedUnexpiredDocument_returnsTrue() {
        // Given
        VehicleDocument document = VehicleDocument.builder()
                .status(VerificationStatus.VERIFIED)
                .expiredDate(LocalDate.parse("2027-01-01"))
                .build();

        // When
        boolean actualValid = document.isValidOn(LocalDate.parse("2026-07-31"));

        // Then
        assertThat(actualValid).isTrue();
    }

    @Test
    void isValidOn_expiredDocument_returnsFalse() {
        // Given
        VehicleDocument document = VehicleDocument.builder()
                .status(VerificationStatus.VERIFIED)
                .expiredDate(LocalDate.parse("2026-07-30"))
                .build();

        // When
        boolean actualValid = document.isValidOn(LocalDate.parse("2026-07-31"));

        // Then
        assertThat(actualValid).isFalse();
    }
}
