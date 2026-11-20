package iuh.fit.se.service;

import iuh.fit.se.domain.entity.Company;
import iuh.fit.se.mapper.BusinessVerificationMapper;
import iuh.fit.se.repository.AccountRepository;
import iuh.fit.se.repository.CompanyRepository;
import iuh.fit.se.repository.EkycVerificationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CompanyVerificationServiceDocumentTest {

    @Mock private AccountRepository accountRepository;
    @Mock private CompanyRepository companyRepository;
    @Mock private BusinessVerificationService businessVerificationService;
    @Mock private BusinessVerificationMapper businessVerificationMapper;
    @Mock private EkycVerificationRepository ekycVerificationRepository;
    @Mock private EkycReviewService ekycReviews;
    @Mock private MediaCertificateClient mediaCertificateClient;

    @InjectMocks
    private CompanyVerificationService service;

    @Test
    void documentStreamsFromBareKeyOrFullS3Url() {
        UUID companyId = UUID.randomUUID();
        String licenseKey = "business-verifications/licenses/" + UUID.randomUUID() + ".pdf";
        String authKey = "business-verifications/authorization/" + UUID.randomUUID() + ".png";
        Company company = Company.builder()
                .id(companyId)
                .businessLicenseUrl(licenseKey)
                .authorizationLetterUrl("https://my-bucket.s3.ap-southeast-1.amazonaws.com/" + authKey)
                .build();

        when(companyRepository.findById(companyId)).thenReturn(Optional.of(company));
        when(mediaCertificateClient.fetch(licenseKey))
                .thenReturn(new MediaCertificateClient.Certificate(new byte[]{1, 2}, "application/pdf"));
        when(mediaCertificateClient.fetch(authKey))
                .thenReturn(new MediaCertificateClient.Certificate(new byte[]{3, 4}, "image/png"));

        var licenseDoc = service.document(companyId, "businessLicense");
        assertThat(licenseDoc.contentType()).isEqualTo("application/pdf");
        assertThat(licenseDoc.fileName()).isEqualTo("giay-phep-doanh-nghiep.pdf");

        var authDoc = service.document(companyId, "authorizationLetter");
        assertThat(authDoc.contentType()).isEqualTo("image/png");
        assertThat(authDoc.fileName()).isEqualTo("giay-uy-quyen.png");
    }
}
