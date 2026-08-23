package iuh.fit.se.service;

import iuh.fit.se.domain.entity.Account;
import iuh.fit.se.domain.entity.Company;
import iuh.fit.se.domain.enums.AccountRole;
import iuh.fit.se.dto.CarrierPublicProfileResponse;
import iuh.fit.se.repository.AccountRepository;
import iuh.fit.se.repository.CompanyRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CarrierPublicProfileServiceTest {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private CompanyRepository companyRepository;

    @InjectMocks
    private CarrierPublicProfileService service;

    @Test
    void givenCarrierWithVerifiedCompany_whenGet_thenReturnsSafePublicProfile() {
        UUID carrierId = UUID.randomUUID();
        Account account = Account.builder().id(carrierId).role(AccountRole.CARRIER).email("carrier@test.local")
                .phone("0900000000").build();
        Company company = Company.builder().account(account).companyName("Nhà xe thật")
                .address("Bình Dương").legalRepresentative("Nguyễn Văn A").taxCode("0123456789").build();
        when(accountRepository.findWithUserProfileById(carrierId)).thenReturn(Optional.of(account));
        when(companyRepository.findByAccount_Id(carrierId)).thenReturn(Optional.of(company));

        CarrierPublicProfileResponse result = service.get(carrierId);

        assertEquals("Nhà xe thật", result.companyName());
        assertEquals("0123456789", result.taxCode());
    }

    @Test
    void givenShipperId_whenGet_thenProfileIsNotFound() {
        UUID shipperId = UUID.randomUUID();
        Account account = Account.builder().id(shipperId).role(AccountRole.SHIPPER).build();
        when(accountRepository.findWithUserProfileById(shipperId)).thenReturn(Optional.of(account));

        assertThrows(ResponseStatusException.class, () -> service.get(shipperId));
    }
}
