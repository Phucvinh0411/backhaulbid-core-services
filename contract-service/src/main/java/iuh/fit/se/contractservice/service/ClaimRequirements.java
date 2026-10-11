package iuh.fit.se.contractservice.service;

import iuh.fit.se.contractservice.domain.enums.CargoDocumentKind;
import iuh.fit.se.contractservice.domain.enums.ClaimEvidenceKind;
import iuh.fit.se.contractservice.domain.enums.ClaimIncidentType;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * The evidence a claim draft must hold before it is marked ready to share. These are the platform's own preparation
 * rules; they are not a statement of what any insurer will pay, and the provider may still ask for more. Nothing here
 * sends a claim or decides one.
 *
 * <p>Value proof comes from either the trip's goods-value documents (typed INVOICE, RECEIPT, CONTRACT or PAYMENT_PROOF)
 * or a claim evidence file of one of those kinds. The trip's documents are reused, so the shipper does not upload them
 * again. Handover photos are context from before transport and never count as damage evidence.
 */
public final class ClaimRequirements {
    /** Any one of these proves the value of the goods. Keep in step with bidding-service's VALUE_PROOF_KINDS. */
    static final Set<ClaimEvidenceKind> VALUE_PROOF = EnumSet.of(
            ClaimEvidenceKind.INVOICE, ClaimEvidenceKind.RECEIPT, ClaimEvidenceKind.CONTRACT, ClaimEvidenceKind.PAYMENT_PROOF);

    private ClaimRequirements() {
    }

    /**
     * Missing items in Vietnamese, in checklist order. Empty when the draft may be shared. Waybill or trip reference is
     * the trip itself and is always present, so it is not listed.
     */
    public static List<String> missingItems(ClaimIncidentType type, BigDecimal claimedAmount,
                                            Set<ClaimEvidenceKind> evidenceKinds, Set<CargoDocumentKind> cargoKinds) {
        List<String> missing = new ArrayList<>();
        if (claimedAmount == null || claimedAmount.signum() <= 0) {
            missing.add("Số tiền yêu cầu");
        }
        boolean cargoProof = cargoKinds.stream().anyMatch(CargoDocumentKind::isValueProof);
        boolean evidenceProof = evidenceKinds.stream().anyMatch(VALUE_PROOF::contains);
        if (!cargoProof && !evidenceProof) {
            missing.add("Chứng từ chứng minh giá trị hàng (hóa đơn, biên nhận, hợp đồng hoặc chứng từ thanh toán)");
        }
        switch (type) {
            case DAMAGE -> {
                if (!evidenceKinds.contains(ClaimEvidenceKind.PHOTO)) {
                    missing.add("Ảnh hàng hư hỏng");
                }
                if (!evidenceKinds.contains(ClaimEvidenceKind.PACKAGING) && !evidenceKinds.contains(ClaimEvidenceKind.VIDEO)) {
                    missing.add("Ảnh hoặc video bao bì và phần hư hỏng");
                }
            }
            case LOSS -> {
                if (!evidenceKinds.contains(ClaimEvidenceKind.CARRIER_CONFIRMATION)) {
                    missing.add("Xác nhận hoặc biên bản tra soát của nhà vận chuyển");
                }
            }
            case THEFT -> {
                if (!evidenceKinds.contains(ClaimEvidenceKind.POLICE_REPORT)) {
                    missing.add("Báo cáo hoặc giấy tiếp nhận của cơ quan công an");
                }
            }
            default -> throw new IllegalArgumentException("Unknown incident type");
        }
        return missing;
    }
}
