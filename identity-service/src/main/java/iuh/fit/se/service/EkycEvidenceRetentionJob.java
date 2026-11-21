package iuh.fit.se.service;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.*;

@Configuration @EnableScheduling @RequiredArgsConstructor
public class EkycEvidenceRetentionJob {
    private static final long RETENTION_LOCK_KEY = 4_100_001L;

    private final EkycReviewService reviews;
    private final PostgresAdvisoryLock advisoryLock;

    /** Removes encrypted eKYC image evidence after its configured retention deadline. */
    @Scheduled(fixedDelayString="${app.ekyc.evidence-cleanup-ms:3600000}", initialDelayString="${app.ekyc.evidence-cleanup-ms:3600000}")
    public void cleanup() { advisoryLock.runInTransactionWithLock(RETENTION_LOCK_KEY, reviews::purgeExpiredEvidence); }
}
