package iuh.fit.se.service;

import iuh.fit.se.domain.dto.request.AddressRequest;
import iuh.fit.se.domain.dto.response.AddressResponse;
import iuh.fit.se.domain.entity.AddressBookEntry;
import iuh.fit.se.repository.AddressBookRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;
import java.time.Instant;

@Service
@RequiredArgsConstructor
public class AddressBookService {

    private final AddressBookRepository addressBookRepository;

    @Transactional(readOnly = true)
    public List<AddressResponse> list(UUID accountId) {
        return addressBookRepository.findAllByAccountIdOrderByUpdatedAtDesc(accountId)
                .stream()
                .map(AddressResponse::from)
                .toList();
    }

    @Transactional
    public AddressResponse create(UUID accountId, AddressRequest request) {
        AddressBookEntry entry = AddressBookEntry.builder()
                .accountId(accountId)
                .label(request.label().trim())
                .contactName(request.contactName().trim())
                .contactPhone(request.contactPhone().trim())
                .province(request.province().trim())
                .detail(request.detail().trim())
                .build();
        applyCoordinates(entry, request);

        return AddressResponse.from(addressBookRepository.save(entry));
    }

    @Transactional
    public AddressResponse update(UUID accountId, UUID addressId, AddressRequest request) {
        AddressBookEntry entry = findOwned(accountId, addressId);
        entry.setLabel(request.label().trim());
        entry.setContactName(request.contactName().trim());
        entry.setContactPhone(request.contactPhone().trim());
        entry.setProvince(request.province().trim());
        entry.setDetail(request.detail().trim());
        applyCoordinates(entry, request);
        return AddressResponse.from(addressBookRepository.save(entry));
    }

    @Transactional
    public void delete(UUID accountId, UUID addressId) {
        addressBookRepository.delete(findOwned(accountId, addressId));
    }

    private AddressBookEntry findOwned(UUID accountId, UUID addressId) {
        return addressBookRepository.findByIdAndAccountId(addressId, accountId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Address not found"));
    }

    private void applyCoordinates(AddressBookEntry entry, AddressRequest request) {
        if (!request.isCoordinatePairValid()
                || (request.coordinateSource() != null && !request.coordinateSource().equals("USER_CONFIRMED")))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Coordinates must be a confirmed, valid pair");
        entry.setLatitude(request.latitude());
        entry.setLongitude(request.longitude());
        entry.setCoordinateSource(request.latitude() == null ? null : "USER_CONFIRMED");
        // Confirmation is a server observation, never a client supplied timestamp.
        entry.setCoordinateConfirmedAt(request.latitude() == null ? null : Instant.now());
    }
}
