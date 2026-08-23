package iuh.fit.se.controller;

import iuh.fit.se.domain.dto.request.AddressRequest;
import iuh.fit.se.domain.dto.response.AddressResponse;
import iuh.fit.se.service.AddressBookService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/addresses")
@RequiredArgsConstructor
public class AddressBookController {

    private final AddressBookService addressBookService;

    @GetMapping
    @PreAuthorize("hasAnyRole('SHIPPER', 'CARRIER')")
    public List<AddressResponse> list(Authentication authentication) {
        return addressBookService.list(currentAccountId(authentication));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('SHIPPER', 'CARRIER')")
    public ResponseEntity<AddressResponse> create(
            Authentication authentication,
            @Valid @RequestBody AddressRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(addressBookService.create(currentAccountId(authentication), request));
    }

    @PatchMapping("/{addressId}")
    @PreAuthorize("hasAnyRole('SHIPPER', 'CARRIER')")
    public AddressResponse update(
            Authentication authentication,
            @PathVariable UUID addressId,
            @Valid @RequestBody AddressRequest request) {
        return addressBookService.update(currentAccountId(authentication), addressId, request);
    }

    @DeleteMapping("/{addressId}")
    @PreAuthorize("hasAnyRole('SHIPPER', 'CARRIER')")
    public ResponseEntity<Void> delete(
            Authentication authentication,
            @PathVariable UUID addressId) {
        addressBookService.delete(currentAccountId(authentication), addressId);
        return ResponseEntity.noContent().build();
    }

    private UUID currentAccountId(Authentication authentication) {
        return UUID.fromString(authentication.getName());
    }
}
