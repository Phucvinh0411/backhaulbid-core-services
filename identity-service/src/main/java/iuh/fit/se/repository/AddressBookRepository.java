package iuh.fit.se.repository;

import iuh.fit.se.domain.entity.AddressBookEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AddressBookRepository extends JpaRepository<AddressBookEntry, UUID> {
    List<AddressBookEntry> findAllByAccountIdOrderByUpdatedAtDesc(UUID accountId);

    Optional<AddressBookEntry> findByIdAndAccountId(UUID id, UUID accountId);
}
