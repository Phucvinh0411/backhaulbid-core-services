package iuh.fit.se.service;

import iuh.fit.se.domain.entity.Account;
import iuh.fit.se.domain.entity.EkycVerification;
import iuh.fit.se.domain.enums.VerificationStatus;
import iuh.fit.se.dto.EkycSubmitRequest;
import iuh.fit.se.repository.AccountRepository;
import iuh.fit.se.repository.EkycVerificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class EkycService {

    private final AccountRepository accountRepository;
    private final EkycVerificationRepository ekycVerificationRepository;

    @Transactional
    public EkycVerification processEkyc(String accountId, EkycSubmitRequest request) {
        UUID parsedAccountId = UUID.fromString(accountId);
        Account account = accountRepository.findById(parsedAccountId)
                .orElseThrow(() -> new RuntimeException("Account not found"));

        EkycVerification verification = ekycVerificationRepository
                .findByAccount_Id(parsedAccountId)
                .orElseGet(() -> EkycVerification.builder().account(account).build());

        verification.setIdentityNumber(request.getIdentityNumber());
        verification.setFullName(limitLength(normalizeBlank(request.getFullName()), 255));
        verification.setFrontImageUrl(request.getFrontImageUrl());
        verification.setBackImageUrl(request.getBackImageUrl());
        verification.setSelfieImageUrl(request.getSelfieImageUrl());
        verification.setOcrPassed(request.getOcrPassed());
        verification.setDocumentLivenessPassed(request.getDocumentLivenessPassed());
        verification.setDocumentAuthenticityPassed(request.getDocumentAuthenticityPassed());
        verification.setLivenessPassed(request.getLivenessPassed());
        verification.setFaceMatched(request.getFaceMatched());
        verification.setFaceMatchScore(request.getFaceMatchScore());
        String failureReason = determineFailureReason(request);
        verification.setFailureReason(failureReason);
        verification.setStatus(failureReason == null
                ? VerificationStatus.VERIFIED
                : VerificationStatus.REJECTED);

        return ekycVerificationRepository.save(verification);
    }

    @Transactional(readOnly = true)
    public Optional<EkycVerification> findByAccountId(String accountId) {
        return ekycVerificationRepository.findByAccount_Id(UUID.fromString(accountId));
    }

    private String determineFailureReason(EkycSubmitRequest request) {
        if (!Boolean.TRUE.equals(request.getOcrPassed())) {
            return "Không nhận diện được CCCD hợp lệ. Vui lòng chụp đúng CCCD bản gốc và bảo đảm thông tin rõ nét.";
        }
        if (!Boolean.TRUE.equals(request.getDocumentAuthenticityPassed())) {
            return "Giấy tờ có dấu hiệu bị chụp lại, in lại hoặc thay ảnh. Vui lòng sử dụng CCCD bản gốc.";
        }
        if (!Boolean.TRUE.equals(request.getDocumentLivenessPassed())) {
            return "Kiểm tra giấy tờ thật không đạt. Vui lòng chụp trực tiếp CCCD bản gốc.";
        }
        if (!Boolean.TRUE.equals(request.getLivenessPassed())) {
            return "Kiểm tra người thật không đạt. Vui lòng thực hiện lại trong môi trường đủ sáng.";
        }
        if (!Boolean.TRUE.equals(request.getFaceMatched())) {
            return "Khuôn mặt không khớp với ảnh trên CCCD. Vui lòng thực hiện lại.";
        }
        if (request.getIdentityNumber() == null || request.getIdentityNumber().isBlank()
                || request.getFullName() == null || request.getFullName().isBlank()) {
            return "OCR không trả về đủ số CCCD và họ tên người đại diện. Vui lòng thực hiện lại.";
        }
        return null;
    }

    private String normalizeBlank(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String limitLength(String value, int maximumLength) {
        if (value == null || value.length() <= maximumLength) {
            return value;
        }
        return value.substring(0, maximumLength);
    }
}
