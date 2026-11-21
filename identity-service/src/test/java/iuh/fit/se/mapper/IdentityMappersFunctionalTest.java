package iuh.fit.se.mapper;

import iuh.fit.se.domain.dto.response.AddressResponse;
import iuh.fit.se.domain.dto.response.AdminAccountResponse;
import iuh.fit.se.domain.dto.response.AdminSettingsResponse;
import iuh.fit.se.domain.dto.response.CurrentAccountResponse;
import iuh.fit.se.domain.entity.Account;
import iuh.fit.se.domain.entity.AddressBookEntry;
import iuh.fit.se.domain.entity.AdminSetting;
import iuh.fit.se.domain.entity.Company;
import iuh.fit.se.domain.entity.EkycVerification;
import iuh.fit.se.domain.entity.InsuranceInfo;
import iuh.fit.se.domain.entity.UserProfile;
import iuh.fit.se.domain.enums.AccountRole;
import iuh.fit.se.domain.enums.AccountStatus;
import iuh.fit.se.domain.enums.AuthProvider;
import iuh.fit.se.domain.enums.VerificationStatus;
import iuh.fit.se.dto.CargoLiabilityDtos;
import iuh.fit.se.dto.CarrierPublicProfileResponse;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class IdentityMappersFunctionalTest {

    private final AccountMapper accountMapper = Mappers.getMapper(AccountMapper.class);
    private final AddressMapper addressMapper = Mappers.getMapper(AddressMapper.class);
    private final AdminSettingsMapper adminSettingsMapper = Mappers.getMapper(AdminSettingsMapper.class);
    private final CargoLiabilityMapper cargoLiabilityMapper = Mappers.getMapper(CargoLiabilityMapper.class);

    @Test
    void accountMapperMapsCurrentAdminAndPublicProfilesCleanly() {
        UUID accountId = UUID.randomUUID();
        Instant now = Instant.parse("2026-11-20T10:00:00Z");
        UserProfile profile = UserProfile.builder()
                .fullName("Nguyen Van Chu Hang")
                .avatarUrl("/media/avatar.png")
                .build();
        Company company = Company.builder()
                .companyName("Cong Ty Van Tai A")
                .taxCode("0102030405")
                .address("123 Nguyen Hue, TP.HCM")
                .legalRepresentative("Nguyen Van Dai Dien")
                .verificationStatus(VerificationStatus.VERIFIED)
                .build();
        Account account = Account.builder()
                .id(accountId)
                .phone("0901234567")
                .email("shipper@company.vn")
                .emailVerified(true)
                .authProvider(AuthProvider.GOOGLE)
                .role(AccountRole.CARRIER)
                .status(AccountStatus.ACTIVE)
                .userProfile(profile)
                .company(company)
                .createdAt(now)
                .build();

        CurrentAccountResponse current = accountMapper.toCurrentAccountResponse(account);
        assertThat(current.accountId()).isEqualTo(accountId);
        assertThat(current.fullName()).isEqualTo("Nguyen Van Chu Hang");
        assertThat(current.avatarUrl()).isEqualTo("/media/avatar.png");
        assertThat(current.authProvider()).isEqualTo("GOOGLE");
        assertThat(current.role()).isEqualTo("CARRIER");

        AdminAccountResponse adminView = accountMapper.toAdminAccountResponse(account);
        assertThat(adminView.id()).isEqualTo(accountId);
        assertThat(adminView.displayName()).isEqualTo("Nguyen Van Chu Hang");
        assertThat(adminView.companyName()).isEqualTo("Cong Ty Van Tai A");
        assertThat(adminView.verificationStatus()).isEqualTo(VerificationStatus.VERIFIED);
        assertThat(adminView.registeredAt()).isEqualTo(now);

        // Fallback when userProfile and company are null, using ekycVerification
        Account fallbackAccount = Account.builder()
                .id(UUID.randomUUID())
                .phone("0999888777")
                .authProvider(null)
                .role(AccountRole.SHIPPER)
                .status(AccountStatus.ACTIVE)
                .ekycVerification(EkycVerification.builder().status(VerificationStatus.PENDING).build())
                .build();
        assertThat(accountMapper.toCurrentAccountResponse(fallbackAccount).authProvider()).isEqualTo("LOCAL");
        assertThat(accountMapper.toAdminAccountResponse(fallbackAccount).displayName()).isEqualTo("0999888777");
        assertThat(accountMapper.toAdminAccountResponse(fallbackAccount).verificationStatus())
                .isEqualTo(VerificationStatus.PENDING);

        LocalDate expiry = LocalDate.of(2027, 6, 30);
        CarrierPublicProfileResponse publicProfile =
                accountMapper.toCarrierPublicProfileResponse(account, company, expiry);
        assertThat(publicProfile.accountId()).isEqualTo(accountId);
        assertThat(publicProfile.contactEmail()).isEqualTo("shipper@company.vn");
        assertThat(publicProfile.companyName()).isEqualTo("Cong Ty Van Tai A");
        assertThat(publicProfile.cargoLiabilityVerifiedUntil()).isEqualTo(expiry);
    }

    @Test
    void addressAdminSettingsAndCargoLiabilityMappersMapAllFields() {
        UUID addressId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        AddressBookEntry entry = AddressBookEntry.builder()
                .id(addressId)
                .accountId(accountId)
                .label("Kho Binh Duong")
                .contactName("Anh Minh")
                .contactPhone("0911222333")
                .province("Bình Dương")
                .detail("KCN VSIP 1")
                .latitude(10.95)
                .longitude(106.71)
                .coordinateSource("USER_CONFIRMED")
                .build();
        List<AddressResponse> addresses = addressMapper.toResponses(List.of(entry));
        assertThat(addresses).hasSize(1);
        assertThat(addresses.get(0).id()).isEqualTo(addressId);
        assertThat(addresses.get(0).latitude()).isEqualTo(10.95);

        Instant now = Instant.now();
        AdminSetting setting = AdminSetting.builder()
                .scope("auction")
                .updatedAt(now)
                .build();
        AdminSettingsResponse settingsResponse =
                adminSettingsMapper.toResponse(setting, Map.of("maxAttempts", 4));
        assertThat(settingsResponse.scope()).isEqualTo("auction");
        assertThat(settingsResponse.values()).containsEntry("maxAttempts", 4);

        Account carrierAccount = Account.builder().id(accountId).build();
        Company company = Company.builder()
                .account(carrierAccount)
                .companyName("Van Tai B")
                .taxCode("0304050607")
                .build();
        InsuranceInfo info = InsuranceInfo.builder()
                .id(UUID.randomUUID())
                .company(company)
                .providerName("Bao Viet")
                .policyNumber("BV-2026-01")
                .coverageLimit(new BigDecimal("500000000.00"))
                .expiredDate(LocalDate.of(2027, 12, 31))
                .status(VerificationStatus.VERIFIED)
                .submittedAt(now)
                .build();

        CargoLiabilityDtos.Submission submission = cargoLiabilityMapper.toSubmission(info);
        assertThat(submission.status()).isEqualTo("VERIFIED");
        assertThat(submission.policyNumber()).isEqualTo("BV-2026-01");

        List<CargoLiabilityDtos.ReviewItem> reviewItems = cargoLiabilityMapper.toReviewItems(List.of(info));
        assertThat(reviewItems).hasSize(1);
        assertThat(reviewItems.get(0).accountId()).isEqualTo(accountId);
        assertThat(reviewItems.get(0).companyName()).isEqualTo("Van Tai B");
    }
}
