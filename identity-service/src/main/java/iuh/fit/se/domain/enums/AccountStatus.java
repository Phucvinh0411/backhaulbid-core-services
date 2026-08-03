package iuh.fit.se.domain.enums;

public enum AccountStatus {
    INACTIVE,         // Account created, waiting for eKYC/Admin approval
    ACTIVE,           // Step 2: Approved, can transact
    BLOCKED
}
