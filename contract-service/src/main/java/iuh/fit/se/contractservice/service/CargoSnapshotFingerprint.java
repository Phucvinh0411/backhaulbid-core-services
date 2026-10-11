package iuh.fit.se.contractservice.service;

import iuh.fit.se.contractservice.dto.CargoValueDocumentRef;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;

/**
 * Fingerprint of the cargo snapshot an award attempt carries: the declared goods value and every value document, in a
 * fixed order. Stored on the trip when it is created, so a retry of the same attempt must carry the same snapshot.
 * It is kept apart from the route fingerprint, which trips created earlier already hold and which must not change.
 */
final class CargoSnapshotFingerprint {
    private CargoSnapshotFingerprint() {
    }

    static String of(BigDecimal declaredGoodsValue, List<CargoValueDocumentRef> documents) {
        StringBuilder canonical = new StringBuilder("cargo-snapshot-v1\n");
        canonical.append(declaredGoodsValue == null ? "-" : declaredGoodsValue.setScale(2).toPlainString()).append('\n');
        List<CargoValueDocumentRef> ordered = documents == null ? List.of()
                : documents.stream().sorted(Comparator.comparing(CargoValueDocumentRef::documentId)).toList();
        canonical.append(ordered.size()).append('\n');
        for (CargoValueDocumentRef document : ordered) {
            canonical.append(document.documentId()).append('|')
                    .append(document.kind().name()).append('|')
                    .append(document.objectKey()).append('|')
                    .append(document.uploadedBy()).append('|')
                    .append(document.uploadedAt().toEpochMilli()).append('\n');
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(canonical.toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is not available", impossible);
        }
    }
}
