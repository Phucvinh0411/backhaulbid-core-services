package iuh.fit.se.mapper;

import iuh.fit.se.domain.entity.InsuranceInfo;
import iuh.fit.se.dto.CargoLiabilityDtos;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.ReportingPolicy;

import java.util.List;

@Mapper(
        componentModel = MappingConstants.ComponentModel.SPRING,
        unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface CargoLiabilityMapper {

    CargoLiabilityDtos.Submission toSubmission(InsuranceInfo row);

    @Mapping(target = "accountId", source = "company.account.id")
    @Mapping(target = "companyName", source = "company.companyName")
    @Mapping(target = "taxCode", source = "company.taxCode")
    CargoLiabilityDtos.ReviewItem toReviewItem(InsuranceInfo row);

    List<CargoLiabilityDtos.ReviewItem> toReviewItems(List<InsuranceInfo> rows);
}
