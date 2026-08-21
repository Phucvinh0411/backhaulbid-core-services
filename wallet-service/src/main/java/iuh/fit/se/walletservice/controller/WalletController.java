package iuh.fit.se.walletservice.controller;

import iuh.fit.se.walletservice.domain.entity.Wallet;
import iuh.fit.se.walletservice.repository.WalletRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import lombok.RequiredArgsConstructor;
import java.util.UUID;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/v1/wallets")
@RequiredArgsConstructor
public class WalletController {

    private final WalletRepository walletRepository;

    @GetMapping("/me")
    public ResponseEntity<?> getMyWallet(@RequestHeader("x-user-id") String userId) {
        try {
            Optional<Wallet> walletOpt = walletRepository.findByAccountId(UUID.fromString(userId));
            if (walletOpt.isEmpty()) {
                return ResponseEntity.status(404).body(Map.of("message", "Wallet not found"));
            }
            Wallet w = walletOpt.get();
            return ResponseEntity.ok(Map.of(
                "id", w.getId(),
                "accountId", w.getAccountId(),
                "balance", w.getBalance(),
                "availableBalance", w.getBalance(),
                "frozenBalance", w.getFrozenBalance(),
                "createdAt", w.getCreatedAt(),
                "updatedAt", w.getUpdatedAt()
            ));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("message", e.getMessage()));
        }
    }

    @GetMapping("/me/transactions")
    public ResponseEntity<?> getMyTransactions(@RequestHeader("x-user-id") String userId) {
        try {
            Optional<Wallet> walletOpt = walletRepository.findByAccountId(UUID.fromString(userId));
            if (walletOpt.isEmpty()) {
                return ResponseEntity.status(404).body(Map.of("message", "Wallet not found"));
            }
            
            Wallet wallet = walletOpt.get();
            var txs = wallet.getTransactions().stream().map(t -> Map.of(
                "id", t.getId(),
                "amount", t.getAmount(),
                "type", t.getType(),
                "status", t.getStatus(),
                "paymentMethod", t.getPaymentMethod() != null ? t.getPaymentMethod() : "SYSTEM",
                "description", t.getDescription() != null ? t.getDescription() : "",
                "createdAt", t.getCreatedAt()
            )).toList();

            return ResponseEntity.ok(Map.of(
                "data", txs,
                "total", txs.size()
            ));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("message", e.getMessage()));
        }
    }

    @GetMapping("/me/withdrawals")
    public ResponseEntity<?> getMyWithdrawals(@RequestHeader("x-user-id") String userId) {
        try {
            Optional<Wallet> walletOpt = walletRepository.findByAccountId(UUID.fromString(userId));
            if (walletOpt.isEmpty()) {
                return ResponseEntity.status(404).body(Map.of("message", "Wallet not found"));
            }
            Wallet wallet = walletOpt.get();
            var withdrawals = wallet.getTransactions().stream()
                .filter(t -> t.getType().name().equals("WITHDRAW"))
                .map(t -> Map.of(
                    "id", t.getId(),
                    "amount", t.getAmount(),
                    "status", t.getStatus(),
                    "createdAt", t.getCreatedAt(),
                    "bankName", "MOCK_BANK",
                    "accountNumber", "MOCK_ACCOUNT"
                )).toList();
            return ResponseEntity.ok(Map.of(
                "data", withdrawals,
                "total", withdrawals.size(),
                "page", 1,
                "pageSize", 20
            ));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("message", e.getMessage()));
        }
    }

    @PostMapping("/me/withdrawals")
    public ResponseEntity<?> createWithdrawal(@RequestHeader("x-user-id") String userId, @RequestBody Map<String, Object> payload) {
        try {
            // Mock withdrawal response to prevent frontend errors
            return ResponseEntity.ok(Map.of(
                "message", "Yêu cầu rút tiền đang được xử lý",
                "status", "PENDING"
            ));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("message", e.getMessage()));
        }
    }
}
