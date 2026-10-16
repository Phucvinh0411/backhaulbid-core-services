package iuh.fit.se.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.util.*;

/**
 * Automatic acceptance follows the device conclusion. A capture that does not pass is rejected at once, so the owner
 * scans again; there is no review queue. Development-model biometric results count as passes only when the local
 * development profile sets development-pass-accepted; production never does.
 */
@Component
public class NativeDecisionPolicy {
    private final String mode;
    private final boolean developmentPassAccepted;

    /** Accepts DISABLED or COMPLETED_ONLY. DISABLED accepts nothing, so every submission is rejected for a new scan. */
    public NativeDecisionPolicy(@Value("${app.ekyc.auto-accept:DISABLED}") String mode,
                                @Value("${app.ekyc.development-pass-accepted:false}") boolean developmentPassAccepted) {
        if (!Set.of("DISABLED","COMPLETED_ONLY").contains(mode))
            throw new IllegalStateException("Invalid eKYC auto-accept policy");
        this.mode=mode;
        this.developmentPassAccepted=developmentPassAccepted;
    }
    /** A capture is accepted only when every eKYC step passed and the server agrees with the device conclusion. */
    public boolean accepts(NativeCaptureObservation report) {
        if (!"COMPLETED_ONLY".equals(mode) || !report.capturedChecksComplete()) return false;
        // The device conclusion was already checked against the standard rule when the report was parsed.
        if (developmentPassAccepted) return "COMPLETED".equals(report.derivedConclusion(true));
        return report.derivedConclusion().equals(report.conclusion()) && "COMPLETED".equals(report.conclusion());
    }
    /** Returns the configured auto-accept mode for capability and audit views. */
    public String mode() { return mode; }
}
