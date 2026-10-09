package iuh.fit.se.service;

import iuh.fit.se.domain.entity.Account;
import iuh.fit.se.domain.entity.Company;
import iuh.fit.se.domain.enums.AccountRole;
import iuh.fit.se.domain.enums.InsuranceCoverageType;
import iuh.fit.se.dto.CarrierPublicProfileResponse;
import iuh.fit.se.repository.AccountRepository;
import iuh.fit.se.repository.CompanyRepository;
import iuh.fit.se.repository.InsuranceInfoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CarrierPublicProfileService {
    private final AccountRepository accountRepository;
    private final CompanyRepository companyRepository;
    private final InsuranceInfoRepository insuranceInfoRepository;

    @Transactional(readOnly = true)
    public CarrierPublicProfileResponse get(UUID carrierId) {
        Account account = accountRepository.findWithUserProfileById(carrierId)
                .filter(candidate -> candidate.getRole() == AccountRole.CARRIER)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Carrier profile not found"));
        Company company = companyRepository.findByAccount_Id(carrierId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Carrier business profile not found"));
        LocalDate cargoLiabilityVerifiedUntil = CargoLiabilityRules.latestQualifyingExpiry(
                insuranceInfoRepository.findAllByCompany_IdAndCoverageType(
                        company.getId(), InsuranceCoverageType.CARGO_LIABILITY),
                LocalDate.now());

        return new CarrierPublicProfileResponse(
                account.getId(),
                account.getEmail(),
                account.getPhone(),
                company.getCompanyName(),
                company.getAddress(),
                company.getLegalRepresentative(),
                company.getTaxCode(),
                company.getVerificationStatus() == null ? null : company.getVerificationStatus().name(),
                cargoLiabilityVerifiedUntil);
    }
}
