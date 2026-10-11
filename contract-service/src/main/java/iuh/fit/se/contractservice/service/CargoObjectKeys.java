package iuh.fit.se.contractservice.service;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Object keys of the shipper's goods-value documents: goods-value-docs/uuid.ext, lowercase, as media-service writes
 * them. A key that does not match this exact shape is refused before it is stored or read.
 */
final class CargoObjectKeys {
    static final String FOLDER = "goods-value-docs";
    private static final String UUID = "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}";
    private static final Pattern KEY = Pattern.compile("^" + FOLDER + "/(" + UUID + ")\\.(pdf|png|jpe?g|webp|gif)$");

    private CargoObjectKeys() {
    }

    static boolean isValid(String key) {
        return key != null && KEY.matcher(key).matches();
    }

    /** The file's UUID, which is the document id. Null when the key is not a value-document key. */
    static String documentIdOf(String key) {
        Matcher matcher = key == null ? null : KEY.matcher(key);
        return matcher != null && matcher.matches() ? matcher.group(1) : null;
    }

    /** The file extension, lowercase, for naming the file inside a download package. */
    static String extensionOf(String key) {
        return key.substring(key.lastIndexOf('.') + 1);
    }
}
