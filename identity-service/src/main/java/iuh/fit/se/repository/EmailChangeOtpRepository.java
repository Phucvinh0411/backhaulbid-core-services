package iuh.fit.se.repository;

import iuh.fit.se.domain.entity.EmailChangeOtp;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface EmailChangeOtpRepository extends JpaRepository<EmailChangeOtp, UUID> {
}
