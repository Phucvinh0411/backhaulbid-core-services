package iuh.fit.se.contractservice.service;

import iuh.fit.se.contractservice.domain.entity.Contract;
import iuh.fit.se.contractservice.domain.entity.ContractSignature;
import iuh.fit.se.contractservice.domain.enums.AccountRole;
import iuh.fit.se.contractservice.domain.enums.ContractStatus;
import iuh.fit.se.contractservice.repository.ContractRepository;
import iuh.fit.se.contractservice.repository.ContractSignatureRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ContractServiceTest {
    @Mock
    private ContractRepository contractRepository;

    @Mock
    private ContractSignatureRepository signatureRepository;

    private ContractService contractService;
    private Contract contract;
    private UUID shipperId;
    private UUID carrierId;

    @BeforeEach
    void setUp() {
        contractService = new ContractService(contractRepository, signatureRepository);
        shipperId = UUID.randomUUID();
        carrierId = UUID.randomUUID();
        contract = Contract.builder()
                .id(UUID.randomUUID())
                .shipperId(shipperId)
                .carrierId(carrierId)
                .status(ContractStatus.DRAFT)
                .signatures(new ArrayList<>())
                .build();
        when(contractRepository.findById(contract.getId())).thenReturn(Optional.of(contract));
        when(contractRepository.save(any(Contract.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(signatureRepository.save(any(ContractSignature.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void firstSignatureKeepsContractWaitingForTheOtherParty() {
        when(signatureRepository.findByContractIdAndAccountId(contract.getId(), carrierId)).thenReturn(Optional.empty());

        Contract result = contractService.sign(carrierId, AccountRole.CARRIER, contract.getId(), "127.0.0.1");

        assertThat(result.getStatus()).isEqualTo(ContractStatus.WAITING_SIGNATURE);
        assertThat(result.getSignatures()).singleElement().satisfies(signature -> {
            assertThat(signature.getRole()).isEqualTo(AccountRole.CARRIER);
            assertThat(signature.getSignedAt()).isNotNull();
        });
    }

    @Test
    void secondPartySignatureMarksContractSigned() {
        ContractSignature carrierSignature = ContractSignature.builder()
                .contract(contract)
                .accountId(carrierId)
                .role(AccountRole.CARRIER)
                .signedAt(java.time.Instant.now())
                .build();
        contract.addSignature(carrierSignature);
        when(signatureRepository.findByContractIdAndAccountId(contract.getId(), shipperId)).thenReturn(Optional.empty());

        Contract result = contractService.sign(shipperId, AccountRole.SHIPPER, contract.getId(), "127.0.0.1");

        assertThat(result.getStatus()).isEqualTo(ContractStatus.SIGNED);
        assertThat(result.getSignatures()).hasSize(2);
    }
}
