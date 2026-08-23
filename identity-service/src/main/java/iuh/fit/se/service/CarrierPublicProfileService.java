package iuh.fit.se.service;

import iuh.fit.se.domain.entity.Account;
import iuh.fit.se.domain.entity.Company;
import iuh.fit.se.domain.enums.AccountRole;
import iuh.fit.se.dto.CarrierPublicProfileResponse;
import iuh.fit.se.repository.AccountRepository;
import iuh.fit.se.repository.CompanyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CarrierPublicProfileService {
    private final AccountRepository accountRepository;
    private final CompanyRepository companyRepository;

    @Transactional(readOnly = true)
    public CarrierPublicProfileResponse get(UUID carrierId) {
        Account account = accountRepository.findWithUserProfileById(carrierId)
                .filter(candidate -> candidate.getRole() == AccountRole.CARRIER)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Carrier profile not found"));
        Company company = companyRepository.findByAccount_Id(carrierId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Carrier business profile not found"));

        return new CarrierPublicProfileResponse(
                account.getId(),
                account.getEmail(),
                account.getPhone(),
                company.getCompanyName(),
                company.getAddress(),
                company.getLegalRepresentative(),
                company.getTaxCode(),
                company.getVerificationStatus() == null ? null : company.getVerificationStatus().name());
    }
}
