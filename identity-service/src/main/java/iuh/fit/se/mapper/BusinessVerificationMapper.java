package iuh.fit.se.mapper;

import iuh.fit.se.domain.entity.Company;
import iuh.fit.se.domain.enums.VerificationStatus;
import iuh.fit.se.dto.BusinessVerificationResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.Named;
import org.mapstruct.ReportingPolicy;

@Mapper(
        componentModel = MappingConstants.ComponentModel.SPRING,
        unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface BusinessVerificationMapper {

    @Mapping(target = "accountId", source = "account.id")
    @Mapping(target = "accountRole", source = "account.role")
    @Mapping(target = "contactEmail", source = "account.email")
    @Mapping(target = "contactPhone", source = "account.phone")
    @Mapping(target = "status", source = "verificationStatus", qualifiedByName = "verificationStatus")
    BusinessVerificationResponse toResponse(Company company);

    default BusinessVerificationResponse notSubmitted() {
        return BusinessVerificationResponse.builder()
                .status("NOT_SUBMITTED")
                .build();
    }

    @Named("verificationStatus")
    default String verificationStatus(VerificationStatus status) {
        return status == null ? "NOT_SUBMITTED" : status.name();
    }
}
