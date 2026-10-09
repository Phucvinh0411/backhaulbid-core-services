package iuh.fit.se.contractservice.service;

import iuh.fit.se.contractservice.dto.CreateAuctionAwardRequest;
import iuh.fit.se.contractservice.dto.TripRoutePoint;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

/** Immutable award fingerprint survives later owner pin confirmation. */
final class RouteSnapshotFingerprint {
    private RouteSnapshotFingerprint() {}

    static String of(CreateAuctionAwardRequest request) {
        try {
            var bytes = new ByteArrayOutputStream();
            var out = new DataOutputStream(bytes);
            text(out, request.pickupLocation()); text(out, request.deliveryLocation());
            point(out, request.pickupPoint()); point(out, request.deliveryPoint());
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes.toByteArray()));
        } catch (java.io.IOException | java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("Unable to fingerprint award route", impossible);
        }
    }

    private static void point(DataOutputStream out, TripRoutePoint point) throws java.io.IOException {
        out.writeBoolean(point != null);
        if (point == null) return;
        out.writeBoolean(point.latitude() != null);
        if (point.latitude() != null) { out.writeDouble(point.latitude()); out.writeDouble(point.longitude()); }
        text(out, point.label()); text(out, point.address());
        text(out, point.latitude() == null ? null : "USER_CONFIRMED");
    }

    private static void text(DataOutputStream out, String value) throws java.io.IOException {
        if (value == null) { out.writeInt(-1); return; }
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8); out.writeInt(bytes.length); out.write(bytes);
    }
}
