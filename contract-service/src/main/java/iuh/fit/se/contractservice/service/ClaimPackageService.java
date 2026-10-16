package iuh.fit.se.contractservice.service;

import iuh.fit.se.contractservice.domain.entity.InsuranceClaim;
import iuh.fit.se.contractservice.domain.entity.InsuranceClaimEvidence;
import iuh.fit.se.contractservice.domain.entity.Trip;
import iuh.fit.se.contractservice.domain.entity.TripCargoDocument;
import iuh.fit.se.contractservice.domain.entity.TripHandover;
import iuh.fit.se.contractservice.domain.enums.CargoDocumentKind;
import iuh.fit.se.contractservice.domain.enums.ClaimEvidenceKind;
import iuh.fit.se.contractservice.domain.enums.ClaimIncidentType;
import iuh.fit.se.contractservice.repository.InsuranceClaimEvidenceRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Builds the claim pack a carrier downloads (or the shipper and an admin download): one HTML summary and the files,
 * in a ZIP. Each file is read through the private proxy before anything is written, so a missing file fails the whole
 * download rather than handing over a partial pack. The total size and the file count are capped, which bounds memory.
 * File names are generated from the kind and a position; storage keys and URLs never appear in the pack.
 */
@Service
public class ClaimPackageService {
    static final int MAX_FILES = 40;
    static final long MAX_TOTAL_BYTES = 40L * 1024 * 1024;
    private static final String SUMMARY_NAME = "ho-so-tom-tat.html";
    private static final String HANDOVER_FOLDER = "handover-photos";
    private static final String CLAIM_EVIDENCE_FOLDER = "claim-evidence";
    private static final Pattern PRIVATE_KEY = Pattern.compile(
            "^(goods-value-docs|claim-evidence|handover-photos)/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"
                    + "\\.(pdf|png|jpe?g|webp|gif)$");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
            .withZone(ZoneId.of("Asia/Ho_Chi_Minh"));

    private final InsuranceClaimEvidenceRepository evidence;
    private final TripCargoDocumentService cargoDocuments;
    private final TripHandoverService handovers;
    private final MediaEvidenceClient media;

    public ClaimPackageService(InsuranceClaimEvidenceRepository evidence, TripCargoDocumentService cargoDocuments,
                               TripHandoverService handovers, MediaEvidenceClient media) {
        this.evidence = evidence;
        this.cargoDocuments = cargoDocuments;
        this.handovers = handovers;
        this.media = media;
    }

    /** Who the pack is for. A carrier gets only what was shared; the shipper and admins get the current claim. */
    public enum Audience {
        SHIPPER_OR_ADMIN,
        CARRIER
    }

    public record Packaged(String fileName, byte[] zip) {
    }

    /** One file in the pack: its name inside the ZIP, its private key, and the only folder that key may be in. */
    private record Entry(String path, String objectKey, String folder) {
    }

    public Packaged build(InsuranceClaim claim, Trip trip, Audience audience) {
        boolean carrier = audience == Audience.CARRIER;
        Instant sharedAt = claim.getSharedAt();
        List<TripCargoDocument> cargo = cargoDocuments.documentsOf(claim.getTripId()).stream()
                .filter(row -> !carrier || !row.getUploadedAt().isAfter(sharedAt))
                .toList();
        List<InsuranceClaimEvidence> files = evidence.findByClaimIdOrderByUploadedAtAsc(claim.getId()).stream()
                .filter(file -> !carrier || file.getRevision() <= claim.getSharedRevision())
                .toList();
        Optional<TripHandover> handover = handovers.handoverOf(claim.getTripId());

        List<Entry> entries = new ArrayList<>();
        for (int index = 0; index < cargo.size(); index++) {
            TripCargoDocument row = cargo.get(index);
            String name = numbered(index + 1, row.getKind().name(), row.getObjectKey());
            entries.add(new Entry("chung-tu-gia-tri-hang/" + name, row.getObjectKey(), CargoObjectKeys.FOLDER));
        }
        if (handover.isPresent()) {
            List<String> photoKeys = handover.get().getPhotoKeys();
            for (int index = 0; index < photoKeys.size(); index++) {
                String key = photoKeys.get(index);
                entries.add(new Entry("bien-ban-ban-giao/" + numbered(index + 1, "anh", key), key, HANDOVER_FOLDER));
            }
        }
        for (int index = 0; index < files.size(); index++) {
            InsuranceClaimEvidence file = files.get(index);
            String name = numbered(index + 1, file.getKind().name(), file.getObjectKey());
            entries.add(new Entry("chung-cu-su-co/" + name, file.getObjectKey(), CLAIM_EVIDENCE_FOLDER));
        }
        if (entries.size() > MAX_FILES) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "Hồ sơ có quá nhiều tệp để tải gói một lần");
        }

        List<byte[]> contents = new ArrayList<>();
        long total = 0;
        for (Entry entry : entries) {
            byte[] bytes = fetch(entry.objectKey(), entry.folder());
            total += bytes.length;
            if (total > MAX_TOTAL_BYTES) {
                throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "Hồ sơ vượt quá giới hạn dung lượng tải gói");
            }
            contents.add(bytes);
        }

        byte[] summary = summaryHtml(claim, trip, cargo, files, handover.orElse(null), entries).getBytes(StandardCharsets.UTF_8);
        return new Packaged("ho-so-boi-thuong-" + claim.getId().toString().substring(0, 8) + ".zip",
                zip(summary, entries, contents));
    }

    /** Reads one file. The key must sit in the folder its entry belongs to, so a document never comes from another folder. */
    private byte[] fetch(String objectKey, String folder) {
        if (!objectKey.startsWith(folder + "/") || !PRIVATE_KEY.matcher(objectKey).matches()) {
            throw unavailable();
        }
        return media.fetch(objectKey).map(MediaEvidenceClient.Fetched::bytes).orElseThrow(this::unavailable);
    }

    private ResponseStatusException unavailable() {
        return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "Chưa tải đủ tệp hồ sơ. Vui lòng thử lại sau.");
    }

    private static byte[] zip(byte[] summary, List<Entry> entries, List<byte[]> contents) {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(buffer, StandardCharsets.UTF_8)) {
            put(zip, SUMMARY_NAME, summary);
            for (int index = 0; index < entries.size(); index++) {
                put(zip, entries.get(index).path(), contents.get(index));
            }
        } catch (IOException impossible) {
            throw new IllegalStateException("Unable to write the claim package", impossible);
        }
        return buffer.toByteArray();
    }

    private static void put(ZipOutputStream zip, String path, byte[] bytes) throws IOException {
        zip.putNextEntry(new ZipEntry(path));
        zip.write(bytes);
        zip.closeEntry();
    }

    /** A name from a position, a kind and the stored extension. Only the extension of a validated key is used. */
    private static String numbered(int position, String kind, String objectKey) {
        String extension = objectKey.substring(objectKey.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
        return String.format(Locale.ROOT, "%02d-%s.%s", position, kind.toLowerCase(Locale.ROOT), extension);
    }

    private String summaryHtml(InsuranceClaim claim, Trip trip, List<TripCargoDocument> cargo,
                               List<InsuranceClaimEvidence> files, TripHandover handover, List<Entry> entries) {
        StringBuilder html = new StringBuilder();
        html.append("<!doctype html><html lang=\"vi\"><head><meta charset=\"utf-8\">")
                .append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">")
                .append("<title>Hồ sơ hỗ trợ bồi thường</title></head><body>");
        html.append("<h1>Hồ sơ hỗ trợ bồi thường</h1>");
        html.append("<p>Nền tảng BackHaulBid chuẩn bị và chuyển hồ sơ này cho nhà xe. Nền tảng không bảo đảm nhà bảo hiểm "
                + "chấp thuận hồ sơ hay chi trả khoản nào; quyết định thuộc về đơn vị bảo hiểm.</p>");
        html.append("<table>");
        row(html, "Mã chuyến", claim.getTripId().toString());
        row(html, "Mã hồ sơ", claim.getId().toString());
        row(html, "Loại sự cố", incidentLabel(claim.getIncidentType()));
        row(html, "Thời điểm sự cố", TIME.format(claim.getOccurredAt()));
        row(html, "Mô tả", claim.getDescription());
        row(html, "Số tiền yêu cầu", money(claim.getClaimedAmount()));
        row(html, "Giá trị hàng khai báo", money(trip.getDeclaredGoodsValue()));
        if (claim.getSharedAt() != null) {
            row(html, "Chia sẻ lúc", TIME.format(claim.getSharedAt()) + " (phiên bản " + claim.getSharedRevision() + ")");
        }
        html.append("</table>");

        html.append("<h2>Chứng từ đơn hàng (chứng từ giá trị hàng)</h2>");
        listOrNone(html, cargo.stream().map(row -> label(row.getKind()) + " — " + TIME.format(row.getUploadedAt())
                + " — " + fileNameFor(entries, row.getObjectKey())).toList());

        html.append("<h2>Biên bản bàn giao lúc nhận hàng (bối cảnh, không phải chứng cứ hư hỏng phát sinh sau đó)</h2>");
        if (handover == null) {
            html.append("<p>Chưa có biên bản bàn giao.</p>");
        } else {
            html.append("<table>");
            row(html, "Loại hàng", handover.getCargoCategory());
            row(html, "Số kiện", handover.getPackageCount() == null ? "" : String.valueOf(handover.getPackageCount()));
            row(html, "Khối lượng (kg)", handover.getGrossWeightKg() == null ? "" : handover.getGrossWeightKg().toPlainString());
            row(html, "Tình trạng lúc nhận", handover.getConditionStatus()
                    + (handover.getConditionNote() == null ? "" : " — " + handover.getConditionNote()));
            row(html, "Số niêm phong", handover.getSealNumber());
            row(html, "Địa điểm", handover.getPlaceNote());
            row(html, "Ghi lúc", TIME.format(handover.getRecordedAt()));
            html.append("</table>");
            listOrNone(html, entries.stream().filter(entry -> entry.path().startsWith("bien-ban-ban-giao/"))
                    .map(Entry::path).toList());
        }

        html.append("<h2>Chứng cứ sự cố</h2>");
        listOrNone(html, files.stream().map(file -> label(file.getKind()) + " — " + TIME.format(file.getUploadedAt())
                + " — " + fileNameFor(entries, file.getObjectKey())).toList());
        html.append("</body></html>");
        return html.toString();
    }

    private static String fileNameFor(List<Entry> entries, String objectKey) {
        return entries.stream().filter(entry -> entry.objectKey().equals(objectKey)).map(Entry::path).findFirst()
                .orElse("");
    }

    private static void row(StringBuilder html, String label, String value) {
        html.append("<tr><th>").append(escape(label)).append("</th><td>").append(escape(value == null ? "" : value))
                .append("</td></tr>");
    }

    private static void listOrNone(StringBuilder html, List<String> items) {
        if (items.isEmpty()) {
            html.append("<p>Không có.</p>");
            return;
        }
        html.append("<ol>");
        for (String item : items) {
            html.append("<li>").append(escape(item)).append("</li>");
        }
        html.append("</ol>");
    }

    /**
     * Escapes the characters that can open markup. Vietnamese text is kept as it is, so the summary stays readable as
     * plain text; the file is served as a download, and its content is text inside an HTML body only.
     */
    private static String escape(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }

    private static String money(BigDecimal value) {
        return value == null ? "Chưa khai báo" : value.toPlainString() + " VND";
    }

    private static String incidentLabel(ClaimIncidentType type) {
        return switch (type) {
            case DAMAGE -> "Hư hỏng hàng hóa";
            case LOSS -> "Mất hàng";
            case THEFT -> "Mất trộm";
        };
    }

    private static String label(CargoDocumentKind kind) {
        return CARGO_LABELS.getOrDefault(kind, kind.name());
    }

    private static String label(ClaimEvidenceKind kind) {
        return EVIDENCE_LABELS.getOrDefault(kind, kind.name());
    }

    private static final Map<CargoDocumentKind, String> CARGO_LABELS = Map.of(
            CargoDocumentKind.INVOICE, "Hóa đơn",
            CargoDocumentKind.RECEIPT, "Biên nhận",
            CargoDocumentKind.CONTRACT, "Hợp đồng mua bán",
            CargoDocumentKind.PAYMENT_PROOF, "Chứng từ thanh toán",
            CargoDocumentKind.OTHER, "Chứng từ khác",
            CargoDocumentKind.UNCLASSIFIED, "Chưa phân loại");

    private static final Map<ClaimEvidenceKind, String> EVIDENCE_LABELS = Map.of(
            ClaimEvidenceKind.INVOICE, "Hóa đơn",
            ClaimEvidenceKind.RECEIPT, "Biên nhận",
            ClaimEvidenceKind.CONTRACT, "Hợp đồng",
            ClaimEvidenceKind.PAYMENT_PROOF, "Chứng từ thanh toán",
            ClaimEvidenceKind.PHOTO, "Ảnh hàng hư hỏng",
            ClaimEvidenceKind.VIDEO, "Video",
            ClaimEvidenceKind.PACKAGING, "Bao bì và phần hư hỏng",
            ClaimEvidenceKind.CARRIER_CONFIRMATION, "Xác nhận của nhà vận chuyển",
            ClaimEvidenceKind.POLICE_REPORT, "Báo cáo công an",
            ClaimEvidenceKind.OTHER, "Khác");
}
