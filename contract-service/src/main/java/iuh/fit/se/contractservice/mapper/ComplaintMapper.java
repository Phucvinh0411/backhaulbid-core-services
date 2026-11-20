package iuh.fit.se.contractservice.mapper;

import iuh.fit.se.contractservice.domain.entity.Complaint;
import iuh.fit.se.contractservice.domain.entity.ComplaintMessage;
import iuh.fit.se.contractservice.dto.ComplaintResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.ReportingPolicy;

import java.util.List;

@Mapper(
        componentModel = MappingConstants.ComponentModel.SPRING,
        unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface ComplaintMapper {

    @Mapping(target = "messages", expression = "java(toMessageResponses(complaint.getMessages()))")
    ComplaintResponse toResponse(Complaint complaint);

    List<ComplaintResponse> toResponses(List<Complaint> complaints);

    ComplaintResponse.MessageResponse toMessageResponse(ComplaintMessage message);

    default List<ComplaintResponse.MessageResponse> toMessageResponses(List<ComplaintMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return List.of();
        }
        return messages.stream().map(this::toMessageResponse).toList();
    }
}
