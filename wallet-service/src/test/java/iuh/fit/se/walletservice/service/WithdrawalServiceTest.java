package iuh.fit.se.walletservice.service;

import iuh.fit.se.walletservice.domain.entity.WithdrawalRequest;
import iuh.fit.se.walletservice.domain.enums.WithdrawalStatus;
import iuh.fit.se.walletservice.dto.response.WithdrawalPageResponse;
import iuh.fit.se.walletservice.dto.response.WithdrawalResponse;
import iuh.fit.se.walletservice.mapper.WithdrawalMapper;
import iuh.fit.se.walletservice.repository.WalletRepository;
import iuh.fit.se.walletservice.repository.WithdrawalRequestRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WithdrawalServiceTest {
    @Mock private WithdrawalRequestRepository withdrawalRepository;
    @Mock private WalletRepository walletRepository;
    @Mock private WithdrawalMapper withdrawalMapper;

    @InjectMocks private WithdrawalService withdrawalService;

    private static WithdrawalResponse responseOf(WithdrawalRequest request) {
        return new WithdrawalResponse(
                request.getId(),
                request.getAccountId(),
                request.getAmount(),
                request.getBankName(),
                request.getBankAccountNumber(),
                request.getAccountHolderName(),
                request.getStatus().name(),
                null,
                request.getRejectionReason(),
                Instant.now(),
                null);
    }

    private static WithdrawalRequest requestOf(WithdrawalStatus status) {
        return WithdrawalRequest.builder()
                .id(UUID.randomUUID())
                .accountId(UUID.randomUUID())
                .amount(new BigDecimal("1000000.00"))
                .bankName("Vietcombank")
                .bankAccountNumber("0123456789")
                .accountHolderName("NGUYEN VAN A")
                .status(status)
                .build();
    }

    @Test
    void pendingWithoutStatusOrAllReturnsEveryStatus() {
        WithdrawalRequest pending = requestOf(WithdrawalStatus.PENDING);
        WithdrawalRequest approved = requestOf(WithdrawalStatus.APPROVED);
        when(withdrawalRepository.findAll(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(pending, approved)));
        when(withdrawalMapper.toResponse(pending)).thenReturn(responseOf(pending));
        when(withdrawalMapper.toResponse(approved)).thenReturn(responseOf(approved));

        WithdrawalPageResponse unfiltered = withdrawalService.pending(null, 1, 10);
        WithdrawalPageResponse explicitAll = withdrawalService.pending("ALL", 1, 10);

        assertEquals(2L, unfiltered.totalItems());
        assertEquals(2, unfiltered.data().size());
        assertEquals(2L, explicitAll.totalItems());
        verify(withdrawalRepository, times(2)).findAll(any(Pageable.class));
        verifyNoMoreInteractions(withdrawalRepository);
    }

    @Test
    void pendingWithSpecificStatusFiltersByThatStatus() {
        WithdrawalRequest pending = requestOf(WithdrawalStatus.PENDING);
        when(withdrawalRepository.findByStatus(eq(WithdrawalStatus.PENDING), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(pending)));
        when(withdrawalMapper.toResponse(pending)).thenReturn(responseOf(pending));

        WithdrawalPageResponse page = withdrawalService.pending(" pending ", 1, 10);

        assertEquals(1L, page.totalItems());
        assertEquals("PENDING", page.data().getFirst().status());
        verify(withdrawalRepository).findByStatus(eq(WithdrawalStatus.PENDING), any(Pageable.class));
    }

    @Test
    void pendingWithUnknownStatusRejectsWithBadRequest() {
        ResponseStatusException error = assertThrows(
                ResponseStatusException.class,
                () -> withdrawalService.pending("UNKNOWN", 1, 10));
        assertEquals(HttpStatus.BAD_REQUEST, error.getStatusCode());
    }
}
