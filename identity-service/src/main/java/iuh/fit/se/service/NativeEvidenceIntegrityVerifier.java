package iuh.fit.se.service;

import org.bouncycastle.asn1.ASN1Primitive;
import org.bouncycastle.asn1.icao.LDSSecurityObject;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cms.*;
import org.bouncycastle.cms.jcajce.JcaSimpleSignerInfoVerifierBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import javax.imageio.ImageIO;
import java.io.*;
import java.nio.ByteBuffer;
import java.security.*;
import java.util.*;

/** Integrity only: an embedded signer is NOT a trusted issuer, and static photos are NOT PAD. */
@Component
@lombok.RequiredArgsConstructor
public class NativeEvidenceIntegrityVerifier {
    private final java.time.Clock clock;
    private static final LinkedHashMap<String, Integer> LIMITS = new LinkedHashMap<>();
    static {
        LIMITS.put("dg1", 16 * 1024); LIMITS.put("dg2", 2 * 1024 * 1024); LIMITS.put("sod", 256 * 1024);
        LIMITS.put("front", 4 * 1024 * 1024); LIMITS.put("back", 4 * 1024 * 1024); LIMITS.put("selfie", 4 * 1024 * 1024);
    }

    /** Validates an upload and returns its digest while clearing temporary evidence buffers. */
    public String verify(Map<String, MultipartFile> parts) {
        try (var evidence = inspect(parts)) { return evidence.digest(); }
    }

    /** Validates upload structure, card signatures, MRZ fields and image bounds. */
    public VerifiedNativeEvidence inspect(Map<String, MultipartFile> parts) {
        Map<String, byte[]> buffers = new LinkedHashMap<>();
        try {
            if (!parts.keySet().equals(LIMITS.keySet())) throw invalid();
            for (var entry : LIMITS.entrySet()) {
                var part = parts.get(entry.getKey());
                if (part == null || part.isEmpty() || part.getSize() > entry.getValue()) throw invalid();
                byte[] bytes;
                try (InputStream stream = part.getInputStream()) { bytes = stream.readNBytes(entry.getValue() + 1); }
                buffers.put(entry.getKey(), bytes);
                if (bytes.length == 0 || bytes.length > entry.getValue()) throw invalid();
            }
            verifyCard(buffers.get("dg1"), buffers.get("dg2"), buffers.get("sod"));
            NfcIdentitySummary summary = verifyTd1Document(buffers.get("dg1"));
            for (String name : List.of("front", "back", "selfie")) verifyJpeg(buffers.get(name));
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (var entry : buffers.entrySet()) {
                digest.update(entry.getKey().getBytes(java.nio.charset.StandardCharsets.US_ASCII));
                digest.update(ByteBuffer.allocate(4).putInt(entry.getValue().length).array()); digest.update(entry.getValue());
            }
            Map<String, byte[]> photos = new LinkedHashMap<>();
            for (String name : List.of("front", "back", "selfie")) photos.put(name, buffers.get(name).clone());
            return new VerifiedNativeEvidence(HexFormat.of().formatHex(digest.digest()), summary, photos);
        } catch (ResponseStatusException safe) { throw safe; }
        catch (Exception unsafe) { throw invalid(); } // Never return parser messages/card contents.
        finally { buffers.values().forEach(bytes -> Arrays.fill(bytes, (byte) 0)); }
    }

    /** Verifies the SOD signature and checks DG1/DG2 hashes against its signed contents. */
    private void verifyCard(byte[] dg1, byte[] dg2, byte[] sod) throws Exception {
        checkEnvelope(dg1, 0x61); checkEnvelope(dg2, 0x75);
        int sodOffset = checkEnvelope(sod, 0x77);
        byte[] cmsBytes = Arrays.copyOfRange(sod, sodOffset, sod.length);
        byte[] content = null;
        try {
            ASN1Primitive.fromByteArray(cmsBytes); // Reject trailing data/indefinite garbage.
            CMSSignedData signed = new CMSSignedData(cmsBytes);
            if (!"2.23.136.1.1.1".equals(signed.getSignedContentTypeOID()) || signed.getSignedContent() == null) throw invalid();
            var signers = signed.getSignerInfos().getSigners();
            if (signers.size() != 1) throw invalid();
            SignerInformation signer = signers.iterator().next();
            Collection<X509CertificateHolder> certificates = signed.getCertificates().getMatches(signer.getSID());
            if (certificates.size() != 1 || !signer.verify(new JcaSimpleSignerInfoVerifierBuilder()
                    .setProvider(new BouncyCastleProvider()).build(certificates.iterator().next()))) throw invalid();
            content = (byte[]) signed.getSignedContent().getContent();
            var lds = LDSSecurityObject.getInstance(ASN1Primitive.fromByteArray(content));
            String algorithm = switch (lds.getDigestAlgorithmIdentifier().getAlgorithm().getId()) {
                case "1.3.14.3.2.26" -> "SHA-1";
                case "2.16.840.1.101.3.4.2.1" -> "SHA-256";
                case "2.16.840.1.101.3.4.2.2" -> "SHA-384";
                case "2.16.840.1.101.3.4.2.3" -> "SHA-512";
                default -> throw invalid();
            };
            Map<Integer, byte[]> hashes = new HashMap<>();
            for (var hash : lds.getDatagroupHash()) {
                int number = hash.getDataGroupNumber();
                if (number < 1 || number > 16 || hashes.put(number, hash.getDataGroupHashValue().getOctets()) != null) throw invalid();
            }
            if (!MessageDigest.isEqual(MessageDigest.getInstance(algorithm).digest(dg1), hashes.getOrDefault(1, new byte[0])) ||
                !MessageDigest.isEqual(MessageDigest.getInstance(algorithm).digest(dg2), hashes.getOrDefault(2, new byte[0]))) throw invalid();
        } finally {
            Arrays.fill(cmsBytes, (byte) 0);
            if (content != null) Arrays.fill(content, (byte) 0);
        }
    }
    /** Validates one bounded BER-TLV envelope and returns its content offset. */
    private int checkEnvelope(byte[] data, int tag) {
        if (data.length < 3 || (data[0] & 0xff) != tag) throw invalid();
        int position = 2, length = data[1] & 0xff;
        if ((length & 0x80) != 0) {
            int count = length & 0x7f;
            if (count < 1 || count > 3 || 2 + count >= data.length) throw invalid();
            length = 0;
            for (int i = 0; i < count; i++) length = (length << 8) | (data[position++] & 0xff);
        }
        if (length != data.length - position || length == 0) throw invalid();
        return position;
    }
    /** Parses signed TD1 MRZ fields and validates document and date check digits. */
    private NfcIdentitySummary verifyTd1Document(byte[] data) {
        int offset = checkEnvelope(data, 0x61);
        byte[] mrz = null;
        try {
            while (offset < data.length) {
                int tag = data[offset++] & 0xff;
                if ((tag & 0x1f) == 0x1f) {
                    if (offset >= data.length || (data[offset] & 0x80) != 0) throw invalid();
                    tag = (tag << 8) | (data[offset++] & 0xff);
                }
                if (offset >= data.length) throw invalid();
                int length = data[offset++] & 0xff;
                if ((length & 0x80) != 0) {
                    int count = length & 0x7f;
                    if (count < 1 || count > 3 || offset + count > data.length) throw invalid();
                    length = 0;
                    for (int i = 0; i < count; i++) length = (length << 8) | (data[offset++] & 0xff);
                }
                if (length > data.length - offset) throw invalid();
                if (tag == 0x5f1f) {
                    if (mrz != null || length != 90) throw invalid();
                    mrz = Arrays.copyOfRange(data, offset, offset + length);
                }
                offset += length;
            }
            if (mrz == null) throw invalid();
            for (byte value : mrz) if (!(value >= 'A' && value <= 'Z' || value >= '0' && value <= '9' || value == '<')) throw invalid();
            if (mrz[2] != 'V' || mrz[3] != 'N' || mrz[4] != 'M') throw invalid();
            if (checkDigit(mrz, 5, 9) != mrz[14] || checkDigit(mrz, 30, 6) != mrz[36] || checkDigit(mrz, 38, 6) != mrz[44]) throw invalid();
            int year = 2000 + numeric(mrz, 38, 2), month = numeric(mrz, 40, 2), day = numeric(mrz, 42, 2);
            var expires = java.time.LocalDate.of(year, month, day);
            if (expires.isBefore(java.time.LocalDate.now(clock))) throw invalid();
            int birthYear = (java.time.LocalDate.now(clock).getYear() / 100) * 100 + numeric(mrz, 30, 2);
            if (birthYear > java.time.LocalDate.now(clock).getYear()) birthYear -= 100;
            var birth = java.time.LocalDate.of(birthYear, numeric(mrz, 32, 2), numeric(mrz, 34, 2));
            return new NfcIdentitySummary(text(mrz, 60, 30), text(mrz, 5, 9), birth.toString(),
                    expires.toString(), text(mrz, 37, 1), text(mrz, 45, 3), text(mrz, 2, 3), "SIGNED_DG1");
        } finally { if (mrz != null) Arrays.fill(mrz, (byte) 0); }
    }
    /** Converts a fixed-width MRZ field into normalized printable text. */
    private String text(byte[] bytes, int from, int length) {
        return new String(bytes, from, length, java.nio.charset.StandardCharsets.US_ASCII)
                .replace('<', ' ').trim().replaceAll("\\s+", " ");
    }
    /** Parses a fixed-width decimal MRZ field and rejects non-digit bytes. */
    private int numeric(byte[] bytes, int from, int length) {
        int result = 0;
        for (int i = from; i < from + length; i++) {
            if (bytes[i] < '0' || bytes[i] > '9') throw invalid();
            result = result * 10 + bytes[i] - '0';
        }
        return result;
    }
    /** Calculates the ICAO 7-3-1 check digit for a fixed-width MRZ field. */
    private byte checkDigit(byte[] data, int from, int length) {
        int sum = 0;
        for (int i = 0; i < length; i++) {
            int c = data[from + i];
            int value = c == '<' ? 0 : c >= 'A' && c <= 'Z' ? c - 'A' + 10 : c - '0';
            sum += value * new int[]{7,3,1}[i % 3];
        }
        return (byte) ('0' + sum % 10);
    }
    /** Checks JPEG decoding and dimension limits without writing a cache file. */
    private void verifyJpeg(byte[] bytes) throws Exception {
        if (bytes.length < 4 || (bytes[0] & 0xff) != 0xff || (bytes[1] & 0xff) != 0xd8) throw invalid();
        // Memory input: ImageIO must not create an unencrypted cache file.
        try (var input = new javax.imageio.stream.MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))) {
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw invalid();
            var reader = readers.next();
            try {
                reader.setInput(input, true, true);
                int width = reader.getWidth(0), height = reader.getHeight(0);
                if (width < 32 || height < 32 || width > 1600 || height > 1600 || (long) width * height > 3_000_000) throw invalid();
                // A decodable image is still not proof of a living holder or document authenticity.
                var image = reader.read(0);
                if (image == null) throw invalid();
                image.flush();
            } finally { reader.dispose(); }
        }
    }
    /** Creates a generic evidence error without exposing parser or card details. */
    private static ResponseStatusException invalid() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid or incomplete native evidence");
    }
}
