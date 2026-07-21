package iuh.fit.se.domain.enums;

public enum AccountStatus {
    IN_ACTIVE,         // Step 1: Account created, waiting for eKYC/Admin approval
    ACTIVE,           // Step 2: Approved, can transact
    BLOCKED
}
