package iuh.fit.se.contractservice.domain.enums;

import java.util.EnumSet;
import java.util.Set;

/**
 * Stable business classification of a trip complaint. Codes are persisted as-is;
 * display labels live in the clients. Each category declares which reporter roles
 * may select it so the server can reject mismatched classifications.
 */
public enum ComplaintCategory {
    CARGO_DAMAGE_LOSS(EnumSet.of(AccountRole.SHIPPER)),
    CARGO_INFO_MISMATCH(EnumSet.of(AccountRole.CARRIER)),
    SCHEDULE_DELAY(EnumSet.of(AccountRole.SHIPPER, AccountRole.CARRIER)),
    VEHICLE_DRIVER_ISSUE(EnumSet.of(AccountRole.SHIPPER)),
    DELIVERY_CONFIRMATION(EnumSet.of(AccountRole.SHIPPER, AccountRole.CARRIER)),
    PAYMENT_DEPOSIT(EnumSet.of(AccountRole.SHIPPER, AccountRole.CARRIER)),
    CANCELLATION(EnumSet.of(AccountRole.SHIPPER, AccountRole.CARRIER)),
    CONDUCT(EnumSet.of(AccountRole.SHIPPER, AccountRole.CARRIER)),
    OTHER(EnumSet.of(AccountRole.SHIPPER, AccountRole.CARRIER));

    /** Query value used to list complaints created before categories existed. */
    public static final String UNCATEGORIZED = "UNCATEGORIZED";

    private final Set<AccountRole> reporterRoles;

    ComplaintCategory(Set<AccountRole> reporterRoles) {
        this.reporterRoles = reporterRoles;
    }

    public boolean allowsReporter(AccountRole role) {
        return reporterRoles.contains(role);
    }

    public Set<AccountRole> reporterRoles() {
        return Set.copyOf(reporterRoles);
    }
}
