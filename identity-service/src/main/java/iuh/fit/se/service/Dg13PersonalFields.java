package iuh.fit.se.service;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

/**
 * Reads the DG13 citizen ID shown in the owner profile. Call only after the DG13 hash matched the signed SOD.
 * The rules mirror the device parser (BER-TLV, nesting depth 8, strict UTF-8 text, no repeated tag), so the device
 * and the server agree on which fields exist. Any rule break yields no field. DG13 is optional, so a bad one never
 * rejects the rest of the scan.
 */
final class Dg13PersonalFields {
    /** Personal identification number on the card, the 12-digit citizen ID. Not yet confirmed on a physical card. */
    static final int CITIZEN_ID_TAG = 0x5F10;
    private static final int ENVELOPE_TAG = 0x6D;
    private static final int MAX_DEPTH = 8;

    /** The citizen ID; null when absent or invalid. Never printed. */
    record Values(String citizenId) {
        static final Values NONE = new Values(null);
        @Override public String toString() { return "Dg13PersonalFields(<redacted>)"; }
    }

    private Dg13PersonalFields() {}

    /** Returns the citizen ID, or {@link Values#NONE} when DG13 is absent or does not follow the device rules. */
    static Values read(byte[] dg13) {
        if (dg13 == null || dg13.length < 3 || (dg13[0] & 0xff) != ENVELOPE_TAG) return Values.NONE;
        Fields fields = new Fields();
        try {
            int[] at = {1};
            int length = readLength(dg13, at);
            if (at[0] + length != dg13.length) return Values.NONE;
            collect(dg13, at[0], dg13.length, 0, fields);
        } catch (IllegalArgumentException | IndexOutOfBoundsException malformed) {
            return Values.NONE;
        }
        if (fields.count == 0) return Values.NONE;
        return new Values(citizenId(fields.citizenId));
    }

    /** Text found while walking DG13; only the profile field is kept. */
    private static final class Fields {
        final Set<Integer> seen = new HashSet<>();
        String citizenId;
        int count;
    }

    /** Walks one BER-TLV level, descending into constructed fields. */
    private static void collect(byte[] data, int from, int to, int depth, Fields fields) {
        if (depth > MAX_DEPTH) throw new IllegalArgumentException("DG13 nesting is too deep");
        int at = from;
        while (at < to) {
            int first = data[at++] & 0xff;
            int tag = first;
            if ((first & 0x1f) == 0x1f) {
                int octets = 1, next;
                do {
                    if (at >= to || ++octets > 4) throw new IllegalArgumentException("DG13 tag is malformed");
                    next = data[at++] & 0xff;
                    tag = (tag << 8) | next;
                } while ((next & 0x80) != 0);
            }
            int[] cursor = {at};
            int length = readLength(data, cursor);
            at = cursor[0];
            if (length > to - at) throw new IllegalArgumentException("DG13 field overruns its container");
            if ((first & 0x20) != 0) {
                collect(data, at, at + length, depth + 1, fields);
            } else {
                if (!fields.seen.add(tag)) throw new IllegalArgumentException("DG13 repeats a tag");
                String text = text(data, at, at + length);
                fields.count++;
                if (tag == CITIZEN_ID_TAG) fields.citizenId = text;
            }
            at += length;
        }
    }

    /** Reads a definite BER length and advances the cursor past it. */
    private static int readLength(byte[] data, int[] at) {
        if (at[0] >= data.length) throw new IllegalArgumentException("DG13 length is missing");
        int first = data[at[0]++] & 0xff;
        if (first < 0x80) return first;
        int count = first & 0x7f;
        if (count < 1 || count > 3 || at[0] + count > data.length) throw new IllegalArgumentException("DG13 length is invalid");
        int length = 0;
        for (int i = 0; i < count; i++) length = (length << 8) | (data[at[0]++] & 0xff);
        return length;
    }

    /** Decodes one primitive value as printable UTF-8, the same rule the device applies to every field. */
    private static String text(byte[] data, int from, int to) {
        String text;
        try {
            text = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(data, from, to - from))
                    .toString();
        } catch (CharacterCodingException notText) {
            throw new IllegalArgumentException("DG13 field is not UTF-8 text", notText);
        }
        text = stripTrailingPadding(text);
        if (text.isEmpty() || text.chars().anyMatch(c -> Character.isISOControl(c) && c != '\t')) {
            throw new IllegalArgumentException("DG13 field is not printable text");
        }
        return text;
    }

    private static String stripTrailingPadding(String text) {
        int end = text.length();
        while (end > 0 && (text.charAt(end - 1) == '\u0000' || text.charAt(end - 1) == ' ')) end--;
        return text.substring(0, end);
    }

    /** A citizen ID is exactly twelve digits; anything else is not presented as one. */
    private static String citizenId(String value) {
        return value != null && value.matches("\\d{12}") ? value : null;
    }
}
