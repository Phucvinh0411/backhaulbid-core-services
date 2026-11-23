package iuh.fit.se.controller;

import iuh.fit.se.domain.entity.Account;
import iuh.fit.se.domain.entity.Company;
import iuh.fit.se.domain.entity.EkycVerification;
import iuh.fit.se.domain.entity.UserProfile;
import iuh.fit.se.domain.enums.VerificationStatus;
import iuh.fit.se.repository.AccountRepository;
import iuh.fit.se.repository.CompanyRepository;
import iuh.fit.se.repository.EkycVerificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InternalAccountVerificationControllerTest {

    @Mock
    private EkycVerificationRepository ekycRepository;
    @Mock
    private CompanyRepository companyRepository;
    @Mock
    private AccountRepository accountRepository;

    private InternalAccountVerificationController controller;

    @BeforeEach
    void setUp() {
        controller = new InternalAccountVerificationController(
                ekycRepository, companyRepository, accountRepository, "secret-token");
    }

    @Test
    void rejectsMissingOrInvalidInternalToken() {
        UUID accountId = UUID.randomUUID();

        assertThatThrownBy(() -> controller.verificationStatus(accountId, null))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);

        assertThatThrownBy(() -> controller.signingContact(accountId, "wrong"))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void returnsVerificationFlagsAndSigningContactWhenAuthorized() {
        UUID accountId = UUID.randomUUID();
        EkycVerification ekyc = EkycVerification.builder().status(VerificationStatus.VERIFIED).build();
        Company company = Company.builder()
                .companyName("Cong ty ABC")
                .verificationStatus(VerificationStatus.VERIFIED)
                .build();
        Account account = Account.builder()
                .id(accountId)
                .email("  sign@company.vn  ")
                .emailVerified(true)
                .build();
        account.setUserProfile(UserProfile.builder().fullName("Le Van D").account(account).build());

        when(ekycRepository.findByAccount_Id(accountId)).thenReturn(Optional.of(ekyc));
        when(companyRepository.findByAccount_Id(accountId)).thenReturn(Optional.of(company));
        when(accountRepository.findWithUserProfileById(accountId)).thenReturn(Optional.of(account));

        var eligibility = controller.verificationStatus(accountId, "secret-token");
        assertThat(eligibility.ekycVerified()).isTrue();
        assertThat(eligibility.businessVerified()).isTrue();
        assertThat(eligibility.fullyVerified()).isTrue();

        var contact = controller.signingContact(accountId, "secret-token");
        assertThat(contact.email()).isEqualTo("sign@company.vn");
        assertThat(contact.emailVerified()).isTrue();
        assertThat(contact.fullName()).isEqualTo("Le Van D");
        assertThat(contact.companyName()).isEqualTo("Cong ty ABC");
    }
}
