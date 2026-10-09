package iuh.fit.se.mediaservice.service;

import java.nio.charset.StandardCharsets;
import java.util.Set;

/**
 * Which folders accept uploads and which real file types each one takes. The decision reads the file's leading
 * bytes; the client's file name and content type are never trusted. Photo folders take images only. Document
 * folders also take PDF.
 */
public final class MediaUploadPolicy {

    public static final long MAX_BYTES = 15L * 1024 * 1024;

    /** Every folder the apps and the web portal upload to. Any other folder is refused. */
    public static final Set<String> UPLOAD_FOLDERS = Set.of(
            "delivery-proofs", "handover-photos", "auction-goods", "complaints", "avatars", "claim-evidence",
            "goods-value-docs", "uploads", "business-verifications/licenses", "business-verifications/authorization",
            "business-verifications/insurance", "vehicles/registration", "vehicles/inspection", "drivers/license");

    private static final Set<String> DOCUMENT_FOLDERS = Set.of(
            "complaints", "claim-evidence", "goods-value-docs", "uploads", "business-verifications/licenses",
            "business-verifications/authorization", "business-verifications/insurance", "vehicles/registration",
            "vehicles/inspection", "drivers/license");

    public enum FileKind {
        PNG("png", "image/png"),
        JPEG("jpg", "image/jpeg"),
        GIF("gif", "image/gif"),
        WEBP("webp", "image/webp"),
        PDF("pdf", "application/pdf");

        private final String extension;
        private final String mimeType;

        FileKind(String extension, String mimeType) {
            this.extension = extension;
            this.mimeType = mimeType;
        }

        public String extension() {
            return extension;
        }

        public String mimeType() {
            return mimeType;
        }
    }

    private MediaUploadPolicy() {
    }

    public static boolean isUploadFolder(String folder) {
        return folder != null && UPLOAD_FOLDERS.contains(folder);
    }

    /** The real type from the leading bytes, or null when the bytes match no accepted type. */
    public static FileKind detect(byte[] head) {
        if (head == null) return null;
        if (startsWith(head, 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)) return FileKind.PNG;
        if (startsWith(head, 0xFF, 0xD8, 0xFF)) return FileKind.JPEG;
        if (startsWithAscii(head, "GIF87a") || startsWithAscii(head, "GIF89a")) return FileKind.GIF;
        if (startsWithAscii(head, "RIFF") && head.length >= 12 && ascii(head, 8, 12).equals("WEBP")) return FileKind.WEBP;
        if (startsWithAscii(head, "%PDF-")) return FileKind.PDF;
        return null;
    }

    /** Whether a detected type may be stored in the folder. PDF needs a document folder. */
    public static boolean accepts(String folder, FileKind kind) {
        if (!isUploadFolder(folder) || kind == null) return false;
        return kind != FileKind.PDF || DOCUMENT_FOLDERS.contains(folder);
    }

    private static boolean startsWith(byte[] head, int... expected) {
        if (head.length < expected.length) return false;
        for (int i = 0; i < expected.length; i++) {
            if ((head[i] & 0xFF) != expected[i]) return false;
        }
        return true;
    }

    private static boolean startsWithAscii(byte[] head, String prefix) {
        return head.length >= prefix.length() && ascii(head, 0, prefix.length()).equals(prefix);
    }

    private static String ascii(byte[] head, int from, int to) {
        return new String(head, from, to - from, StandardCharsets.US_ASCII);
    }
}
