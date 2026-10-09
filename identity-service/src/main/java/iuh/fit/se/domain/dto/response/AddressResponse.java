package iuh.fit.se.domain.dto.response;

import iuh.fit.se.domain.entity.AddressBookEntry;

import java.time.Instant;
import java.util.UUID;

public record AddressResponse(
        UUID id,
        UUID accountId,
        String label,
        String contactName,
        String contactPhone,
        String province,
        String detail,
        Instant createdAt,
        Instant updatedAt,
        Double latitude,
        Double longitude,
        String coordinateSource,
        Instant coordinateConfirmedAt
) {
    public AddressResponse(UUID id, UUID accountId, String label, String contactName, String contactPhone,
                           String province, String detail, Instant createdAt, Instant updatedAt) {
        this(id, accountId, label, contactName, contactPhone, province, detail, createdAt, updatedAt,
                null, null, null, null);
    }
    public static AddressResponse from(AddressBookEntry entry) {
        return new AddressResponse(
                entry.getId(),
                entry.getAccountId(),
                entry.getLabel(),
                entry.getContactName(),
                entry.getContactPhone(),
                entry.getProvince(),
                entry.getDetail(),
                entry.getCreatedAt(),
                entry.getUpdatedAt(), entry.getLatitude(), entry.getLongitude(),
                entry.getCoordinateSource(), entry.getCoordinateConfirmedAt());
    }
}
