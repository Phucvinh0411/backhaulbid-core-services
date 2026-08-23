package iuh.fit.se.service;

import iuh.fit.se.domain.dto.request.AddressRequest;
import iuh.fit.se.domain.entity.AddressBookEntry;
import iuh.fit.se.repository.AddressBookRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AddressBookServiceTest {

    @Mock
    private AddressBookRepository addressBookRepository;

    @InjectMocks
    private AddressBookService addressBookService;

    @Test
    void givenValidAddress_whenCreate_thenItIsOwnedByCurrentAccountAndReturned() {
        UUID accountId = UUID.randomUUID();
        AddressRequest request = validRequest();
        when(addressBookRepository.save(any(AddressBookEntry.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var response = addressBookService.create(accountId, request);

        assertThat(response.label()).isEqualTo("Kho Bình Dương");
        assertThat(response.accountId()).isEqualTo(accountId);
        verify(addressBookRepository).save(argThat(entry ->
                accountId.equals(entry.getAccountId())
                        && "0912345678".equals(entry.getContactPhone())));
    }

    @Test
    void givenAccountAddresses_whenList_thenOnlyThatAccountIsReturned() {
        UUID accountId = UUID.randomUUID();
        AddressBookEntry entry = entry(accountId);
        when(addressBookRepository.findAllByAccountIdOrderByUpdatedAtDesc(accountId))
                .thenReturn(List.of(entry));

        var response = addressBookService.list(accountId);

        assertThat(response).hasSize(1);
        assertThat(response.get(0).id()).isEqualTo(entry.getId());
        verify(addressBookRepository).findAllByAccountIdOrderByUpdatedAtDesc(accountId);
    }

    @Test
    void givenAddressBelongsToAnotherAccount_whenUpdate_thenNotFoundIsReturned() {
        UUID accountId = UUID.randomUUID();
        UUID addressId = UUID.randomUUID();
        when(addressBookRepository.findByIdAndAccountId(addressId, accountId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> addressBookService.update(accountId, addressId, validRequest()))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Address not found");
        verify(addressBookRepository, never()).save(any(AddressBookEntry.class));
    }

    @Test
    void givenOwnedAddress_whenDelete_thenRepositoryDeletesIt() {
        UUID accountId = UUID.randomUUID();
        UUID addressId = UUID.randomUUID();
        AddressBookEntry entry = entry(accountId);
        when(addressBookRepository.findByIdAndAccountId(addressId, accountId))
                .thenReturn(Optional.of(entry));

        addressBookService.delete(accountId, addressId);

        verify(addressBookRepository).delete(entry);
    }

    private static AddressRequest validRequest() {
        return new AddressRequest(
                "Kho Bình Dương",
                "Nguyễn Văn A",
                "0912345678",
                "Bình Dương",
                "Đường số 8, KCN VSIP 1");
    }

    private static AddressBookEntry entry(UUID accountId) {
        return AddressBookEntry.builder()
                .id(UUID.randomUUID())
                .accountId(accountId)
                .label("Kho Bình Dương")
                .contactName("Nguyễn Văn A")
                .contactPhone("0912345678")
                .province("Bình Dương")
                .detail("Đường số 8, KCN VSIP 1")
                .build();
    }
}
