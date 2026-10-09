package iuh.fit.se.mediaservice.service;

import java.util.Set;
import java.util.regex.Pattern;

/**
 * Decides which stored objects may be streamed back through the authenticated gateway.
 * Operational images and profile avatars are readable to signed-in users. Handover photos and claim evidence are
 * never streamed from here: contract-service checks the trip or claim owner and proxies them. Identity and business
 * documents need an owner/admin check that this service cannot make, so they stay unreadable here.
 */
public final class MediaReadPolicy {

    public static final Set<String> READABLE_FOLDERS = Set.of("delivery-proofs", "auction-goods", "complaints", "avatars");

    private static final Pattern FILE_NAME = Pattern.compile(
            "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.(png|jpe?g|webp|gif)$",
            Pattern.CASE_INSENSITIVE);

    private MediaReadPolicy() {
    }

    /** Returns the S3 key when folder and file name are an allowed upload, otherwise null. */
    public static String readableKey(String folder, String fileName) {
        if (folder == null || fileName == null) return null;
        if (!READABLE_FOLDERS.contains(folder)) return null;
        if (!FILE_NAME.matcher(fileName).matches()) return null;
        return folder + "/" + fileName;
    }

    public static String contentType(String fileName) {
        String lower = fileName.toLowerCase();
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".webp")) return "image/webp";
        if (lower.endsWith(".gif")) return "image/gif";
        return "image/jpeg";
    }
}
