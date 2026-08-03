package iuh.fit.se.contractservice.domain;

import iuh.fit.se.contractservice.domain.entity.Contract;
import iuh.fit.se.contractservice.domain.entity.ContractSignature;
import iuh.fit.se.contractservice.domain.entity.Trip;
import iuh.fit.se.contractservice.domain.enums.ContractStatus;

import java.util.List;
import java.util.UUID;

public final class ContractBuilder {
    private final Contract contract = Contract.builder()
            .status(ContractStatus.DRAFT)
            .build();

    public ContractBuilder withTripInfo(Trip trip) {
        contract.setTrip(trip);
        return this;
    }

    public ContractBuilder withParties(UUID shipperId, UUID carrierId) {
        contract.setShipperId(shipperId);
        contract.setCarrierId(carrierId);
        return this;
    }

    public ContractBuilder withContractCode(String contractCode) {
        contract.setContractCode(contractCode);
        return this;
    }

    public ContractBuilder withPdf(String pdfUrl, String pdfHashSha256) {
        contract.setPdfUrl(pdfUrl);
        contract.setPdfHashSha256(pdfHashSha256);
        return this;
    }

    public ContractBuilder withSignatures(List<ContractSignature> signatures) {
        signatures.forEach(contract::addSignature);
        return this;
    }

    public Contract build() {
        return contract;
    }
}
