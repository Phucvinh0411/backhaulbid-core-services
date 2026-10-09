package iuh.fit.se.service;

import iuh.fit.se.domain.entity.InsuranceInfo;
import iuh.fit.se.domain.enums.InsuranceCoverageType;
import iuh.fit.se.domain.enums.VerificationStatus;

import java.time.LocalDate;
import java.util.Collection;
import java.util.Comparator;
import java.util.regex.Pattern;

/**
 * When a carrier's cargo-liability certificate satisfies a shipper's requirement. The row must be cargo liability,
 * reviewed by an admin (VERIFIED with reviewer and review time), backed by a stored file, and not expired. Self-entered
 * rows, legacy rows and insurance on the vehicle never qualify.
 */
public final class CargoLiabilityRules {

    /** Private folder that media-service keeps for certificate files. Read only through its token-protected endpoint. */
    public static final Pattern CERTIFICATE_KEY = Pattern.compile(
            "^business-verifications/insurance/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"
                    + "\\.(png|jpe?g|webp|gif|pdf)$",
            Pattern.CASE_INSENSITIVE);

    private CargoLiabilityRules() {
    }

    public static boolean isCertificateKey(String key) {
        return key != null && CERTIFICATE_KEY.matcher(key).matches();
    }

    public static boolean qualifies(InsuranceInfo row, LocalDate today) {
        return row != null
                && row.getCoverageType() == InsuranceCoverageType.CARGO_LIABILITY
                && row.getStatus() == VerificationStatus.VERIFIED
                && row.getVerifiedBy() != null
                && row.getVerifiedAt() != null
                && row.getCertificateKey() != null
                && row.getExpiredDate() != null
                && !row.getExpiredDate().isBefore(today);
    }

    /** The latest expiry among rows that qualify today, or null when none qualifies. */
    public static LocalDate latestQualifyingExpiry(Collection<InsuranceInfo> rows, LocalDate today) {
        return rows.stream()
                .filter(row -> qualifies(row, today))
                .map(InsuranceInfo::getExpiredDate)
                .max(Comparator.naturalOrder())
                .orElse(null);
    }
}
