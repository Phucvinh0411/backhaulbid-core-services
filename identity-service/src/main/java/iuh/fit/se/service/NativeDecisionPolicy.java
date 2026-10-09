package iuh.fit.se.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import java.util.*;

/** Local business acceptance is explicit; SDK observations never grant production identity assurance. */
@Component
public class NativeDecisionPolicy {
    private final String mode;

    /** Validates that automatic NFC acceptance is restricted to an isolated local profile. */
    public NativeDecisionPolicy(@Value("${app.ekyc.local-automatic:DISABLED}") String mode, Environment environment) {
        if (!Set.of("DISABLED","COMPLETED_ONLY","CAPTURE_COMPLETE").contains(mode))
            throw new IllegalStateException("Invalid NFC local acceptance policy");
        var profiles=Set.of(environment.getActiveProfiles());
        if (!"DISABLED".equals(mode) && (!profiles.contains("local") || profiles.stream().anyMatch(p -> Set.of("prod","production","staging").contains(p))))
            throw new IllegalStateException("NFC development acceptance requires an isolated local profile");
        this.mode=mode;
    }
    /** Applies the configured local acceptance rule without claiming production assurance. */
    public boolean accepts(NativeCaptureObservation report) {
        if ("DISABLED".equals(mode) || !report.capturedChecksComplete() || !report.derivedConclusion().equals(report.conclusion())) return false;
        return "CAPTURE_COMPLETE".equals(mode) || "COMPLETED".equals(report.conclusion());
    }
    /** Returns the configured local acceptance mode for capability and audit views. */
    public String mode() { return mode; }
}
