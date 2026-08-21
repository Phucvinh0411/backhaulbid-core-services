package iuh.fit.se.walletservice.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/internal")
public class DummyWalletController {

    @PostMapping("/wallets/{accountId}/holds")
    public ResponseEntity<Map<String, Object>> hold(@PathVariable String accountId, @RequestBody Map<String, Object> input) {
        return ResponseEntity.ok(Map.of(
            "status", "SUCCESS",
            "transactionId", UUID.randomUUID().toString(),
            "holdId", UUID.randomUUID().toString(),
            "amount", input.getOrDefault("amount", "0")
        ));
    }

    @PostMapping("/wallets/{accountId}/charges")
    public ResponseEntity<Map<String, Object>> charge(@PathVariable String accountId, @RequestBody Map<String, Object> input) {
        return ResponseEntity.ok(Map.of(
            "status", "SUCCESS",
            "transactionId", UUID.randomUUID().toString(),
            "amount", input.getOrDefault("amount", "0")
        ));
    }

    @PostMapping("/wallet-holds/{holdId}/release")
    public ResponseEntity<Map<String, Object>> release(@PathVariable String holdId, @RequestBody Map<String, Object> input) {
        return ResponseEntity.ok(Map.of(
            "status", "SUCCESS",
            "transactionId", UUID.randomUUID().toString()
        ));
    }

    @PostMapping("/wallet-holds/{holdId}/forfeit")
    public ResponseEntity<Map<String, Object>> forfeit(@PathVariable String holdId, @RequestBody Map<String, Object> input) {
        return ResponseEntity.ok(Map.of(
            "status", "SUCCESS",
            "transactionId", UUID.randomUUID().toString()
        ));
    }
}
