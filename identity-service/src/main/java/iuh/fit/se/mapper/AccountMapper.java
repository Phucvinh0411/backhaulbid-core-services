package iuh.fit.se.mapper;

import iuh.fit.se.domain.dto.response.AdminAccountResponse;
import iuh.fit.se.domain.dto.response.CurrentAccountResponse;
import iuh.fit.se.domain.entity.Account;
import iuh.fit.se.domain.entity.Company;
import iuh.fit.se.domain.enums.AuthProvider;
import iuh.fit.se.domain.enums.VerificationStatus;
import iuh.fit.se.dto.CarrierPublicProfileResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.Named;
import org.mapstruct.ReportingPolicy;

import java.time.LocalDate;

@Mapper(
        componentModel = MappingConstants.ComponentModel.SPRING,
        unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface AccountMapper {

    @Mapping(target = "accountId", source = "id")
    @Mapping(target = "fullName", source = "userProfile.fullName")
    @Mapping(target = "avatarUrl", source = "userProfile.avatarUrl")
    @Mapping(target = "authProvider", source = "authProvider", qualifiedByName = "resolveAuthProvider")
    CurrentAccountResponse toCurrentAccountResponse(Account account);

    @Mapping(target = "displayName", expression = "java(resolveDisplayName(account))")
    @Mapping(target = "verificationStatus", expression = "java(resolveVerificationStatus(account))")
    @Mapping(target = "companyName", source = "company.companyName")
    @Mapping(target = "registeredAt", source = "createdAt")
    AdminAccountResponse toAdminAccountResponse(Account account);

    @Mapping(target = "accountId", source = "account.id")
    @Mapping(target = "contactEmail", source = "account.email")
    @Mapping(target = "contactPhone", source = "account.phone")
    @Mapping(target = "companyName", source = "company.companyName")
    @Mapping(target = "address", source = "company.address")
    @Mapping(target = "legalRepresentative", source = "company.legalRepresentative")
    @Mapping(target = "taxCode", source = "company.taxCode")
    @Mapping(target = "verificationStatus", source = "company.verificationStatus")
    @Mapping(target = "cargoLiabilityVerifiedUntil", source = "cargoLiabilityVerifiedUntil")
    CarrierPublicProfileResponse toCarrierPublicProfileResponse(
            Account account,
            Company company,
            LocalDate cargoLiabilityVerifiedUntil);

    @Named("resolveAuthProvider")
    default String resolveAuthProvider(AuthProvider provider) {
        return provider != null ? provider.name() : "LOCAL";
    }

    default String resolveDisplayName(Account account) {
        String profileName = account.getUserProfile() == null ? null : account.getUserProfile().getFullName();
        String companyName = account.getCompany() == null ? null : account.getCompany().getCompanyName();
        return firstNonBlank(profileName, companyName, account.getEmail(), account.getPhone());
    }

    default VerificationStatus resolveVerificationStatus(Account account) {
        if (account.getCompany() != null) {
            return account.getCompany().getVerificationStatus();
        }
        return account.getEkycVerification() == null ? null : account.getEkycVerification().getStatus();
    }

    default String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "Chưa có thông tin";
    }
}
