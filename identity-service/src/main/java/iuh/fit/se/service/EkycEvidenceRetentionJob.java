package iuh.fit.se.service;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.*;

@Configuration @EnableScheduling @RequiredArgsConstructor
public class EkycEvidenceRetentionJob {
    private final EkycReviewService reviews;
    /** Removes encrypted eKYC image evidence after its configured retention deadline. */
    @Scheduled(fixedDelayString="${app.ekyc.evidence-cleanup-ms:3600000}", initialDelayString="${app.ekyc.evidence-cleanup-ms:3600000}")
    public void cleanup() { reviews.purgeExpiredEvidence(); }
}
