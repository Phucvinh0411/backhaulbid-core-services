package iuh.fit.se.contractservice.domain.entity;

import iuh.fit.se.contractservice.domain.enums.AccountRole;
import iuh.fit.se.contractservice.domain.enums.ContractStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ContractTest {

    @Test
    void markSigned_shipperAndCarrierSigned_setsSignedStatus() {
        // Given
        Contract contract = Contract.builder()
                .status(ContractStatus.WAITING_SIGNATURE)
                .signatures(List.of(
                        signedBy(AccountRole.SHIPPER),
                        signedBy(AccountRole.CARRIER)))
                .build();

        // When
        contract.markSigned();

        // Then
        assertThat(contract.getStatus()).isEqualTo(ContractStatus.SIGNED);
    }

    @Test
    void markSigned_missingCarrierSignature_throwsIllegalStateException() {
        // Given
        Contract contract = Contract.builder()
                .status(ContractStatus.WAITING_SIGNATURE)
                .signatures(List.of(signedBy(AccountRole.SHIPPER)))
                .build();

        // When / Then
        assertThatThrownBy(contract::markSigned)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Both shipper and carrier must sign the contract");
    }

    @Test
    void verifyIntegrity_matchingHash_returnsTrue() {
        // Given
        Contract contract = Contract.builder().pdfHashSha256("abc123").build();

        // When
        boolean actualValid = contract.verifyIntegrity("abc123");

        // Then
        assertThat(actualValid).isTrue();
    }

    private ContractSignature signedBy(AccountRole role) {
        return ContractSignature.builder()
                .role(role)
                .signedAt(Instant.parse("2026-07-31T08:00:00Z"))
                .build();
    }
}
