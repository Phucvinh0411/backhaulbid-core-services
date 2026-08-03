package iuh.fit.se.mapper;

import iuh.fit.se.domain.entity.Account;
import iuh.fit.se.domain.entity.Company;
import iuh.fit.se.domain.entity.EkycVerification;
import iuh.fit.se.domain.enums.AccountRole;
import iuh.fit.se.domain.enums.VerificationStatus;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class IdentityMapperTest {

    private final AuthMapper authMapper = Mappers.getMapper(AuthMapper.class);
    private final BusinessVerificationMapper businessMapper =
            Mappers.getMapper(BusinessVerificationMapper.class);
    private final RepresentativeVerificationMapper representativeMapper =
            Mappers.getMapper(RepresentativeVerificationMapper.class);

    @Test
    void authMapper_mapsAccountAndTokens() {
        Account account = Account.builder()
                .id(UUID.fromString("8f14e45f-ea43-4a4f-b716-3f8f68f74201"))
                .phone("0901234567")
                .role(AccountRole.CARRIER)
                .build();

        var response = authMapper.toResponse(account, "access", "refresh");

        assertThat(response.getAccountId()).isEqualTo(account.getId());
        assertThat(response.getPhone()).isEqualTo("0901234567");
        assertThat(response.getRole()).isEqualTo("CARRIER");
        assertThat(response.getAccessToken()).isEqualTo("access");
        assertThat(response.getRefreshToken()).isEqualTo("refresh");
    }

    @Test
    void businessMapper_mapsNestedAccountAndStatus() {
        Account account = Account.builder()
                .id(UUID.fromString("8f14e45f-ea43-4a4f-b716-3f8f68f74201"))
                .phone("0901234567")
                .email("carrier@test.local")
                .role(AccountRole.CARRIER)
                .build();
        Company company = Company.builder()
                .id(UUID.fromString("ca978112-ca1b-4dca-bac2-31b39a23dc4d"))
                .account(account)
                .taxCode("0100684378")
                .verificationStatus(VerificationStatus.PENDING)
                .build();

        var response = businessMapper.toResponse(company);

        assertThat(response.getAccountId()).isEqualTo(account.getId());
        assertThat(response.getAccountRole()).isEqualTo("CARRIER");
        assertThat(response.getContactEmail()).isEqualTo("carrier@test.local");
        assertThat(response.getStatus()).isEqualTo("PENDING");
    }

    @Test
    void representativeMapper_mapsStatusAndFailureReason() {
        EkycVerification verification = EkycVerification.builder()
                .id(UUID.fromString("ca978112-ca1b-4dca-bac2-31b39a23dc4d"))
                .status(VerificationStatus.REJECTED)
                .failureReason("Giấy tờ không hợp lệ")
                .build();

        var response = representativeMapper.toResponse(verification);

        assertThat(response.getStatus()).isEqualTo("REJECTED");
        assertThat(response.getFailureReason()).isEqualTo("Giấy tờ không hợp lệ");
    }

    @Test
    void mappers_returnExplicitNotSubmittedResponses() {
        assertThat(businessMapper.notSubmitted().getStatus()).isEqualTo("NOT_SUBMITTED");
        assertThat(representativeMapper.notSubmitted().getStatus()).isEqualTo("NOT_SUBMITTED");
    }
}
