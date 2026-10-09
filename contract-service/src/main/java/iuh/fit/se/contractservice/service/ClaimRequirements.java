package iuh.fit.se.contractservice.service;

import iuh.fit.se.contractservice.domain.enums.ClaimEvidenceKind;
import iuh.fit.se.contractservice.domain.enums.ClaimIncidentType;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * The evidence a claim draft must hold before it is marked ready for the provider. These are internal
 * preparation rules; they are not a statement of what any insurer will pay, and the provider may still ask for
 * more. Nothing here sends a claim or decides one.
 */
public final class ClaimRequirements {
    /** Any one of these proves the value of the goods. */
    static final Set<ClaimEvidenceKind> VALUE_PROOF = EnumSet.of(
            ClaimEvidenceKind.INVOICE, ClaimEvidenceKind.RECEIPT, ClaimEvidenceKind.CONTRACT, ClaimEvidenceKind.PAYMENT_PROOF);

    private ClaimRequirements() {
    }

    /**
     * Missing items in Vietnamese, in checklist order. Empty when the draft is ready to be marked for the provider.
     * Waybill or trip reference is the trip itself and is always present, so it is not listed.
     */
    public static List<String> missingItems(ClaimIncidentType type, BigDecimal claimedAmount, Set<ClaimEvidenceKind> kinds) {
        List<String> missing = new ArrayList<>();
        if (claimedAmount == null || claimedAmount.signum() <= 0) {
            missing.add("Số tiền yêu cầu");
        }
        if (kinds.stream().noneMatch(VALUE_PROOF::contains)) {
            missing.add("Chứng từ chứng minh giá trị hàng (hóa đơn, biên nhận, hợp đồng hoặc chứng từ thanh toán)");
        }
        switch (type) {
            case DAMAGE -> {
                if (!kinds.contains(ClaimEvidenceKind.PHOTO)) {
                    missing.add("Ảnh hàng hư hỏng");
                }
                if (!kinds.contains(ClaimEvidenceKind.PACKAGING) && !kinds.contains(ClaimEvidenceKind.VIDEO)) {
                    missing.add("Ảnh hoặc video bao bì và phần hư hỏng");
                }
            }
            case LOSS -> {
                if (!kinds.contains(ClaimEvidenceKind.CARRIER_CONFIRMATION)) {
                    missing.add("Xác nhận hoặc biên bản tra soát của nhà vận chuyển");
                }
            }
            case THEFT -> {
                if (!kinds.contains(ClaimEvidenceKind.POLICE_REPORT)) {
                    missing.add("Báo cáo hoặc giấy tiếp nhận của cơ quan công an");
                }
            }
            default -> throw new IllegalArgumentException("Unknown incident type");
        }
        return missing;
    }
}
