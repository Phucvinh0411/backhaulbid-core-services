package iuh.fit.se.mapper;

import iuh.fit.se.domain.entity.EkycVerification;
import iuh.fit.se.domain.enums.VerificationStatus;
import iuh.fit.se.dto.RepresentativeVerificationResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.Named;
import org.mapstruct.ReportingPolicy;

@Mapper(
        componentModel = MappingConstants.ComponentModel.SPRING,
        unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface RepresentativeVerificationMapper {

    @Mapping(target = "status", source = "status", qualifiedByName = "verificationStatus")
    RepresentativeVerificationResponse toResponse(EkycVerification verification);

    default RepresentativeVerificationResponse notSubmitted() {
        return RepresentativeVerificationResponse.builder()
                .status("NOT_SUBMITTED")
                .build();
    }

    @Named("verificationStatus")
    default String verificationStatus(VerificationStatus status) {
        return status == null ? "PENDING" : status.name();
    }
}
