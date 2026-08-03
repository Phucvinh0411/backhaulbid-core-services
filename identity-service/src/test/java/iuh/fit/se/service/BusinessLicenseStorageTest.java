package iuh.fit.se.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BusinessLicenseStorageTest {

    @TempDir
    Path uploadDirectory;

    @Test
    void store_validPdf_usesGeneratedFilenameAndPreservesDisplayName() {
        BusinessLicenseStorage storage =
                new BusinessLicenseStorage(uploadDirectory.toString());
        MockMultipartFile file = new MockMultipartFile(
                "businessLicense",
                "giay-phep.pdf",
                "application/pdf",
                "%PDF-1.7 sample".getBytes()
        );

        BusinessLicenseStorage.StoredBusinessLicense stored = storage.store(file);

        assertThat(stored.originalFilename()).isEqualTo("giay-phep.pdf");
        assertThat(stored.storedFilename()).endsWith(".pdf");
        assertThat(Files.exists(uploadDirectory.resolve(stored.storedFilename())))
                .isTrue();
    }

    @Test
    void store_spoofedPdf_rejectsFileUsingMagicBytes() {
        BusinessLicenseStorage storage =
                new BusinessLicenseStorage(uploadDirectory.toString());
        MockMultipartFile file = new MockMultipartFile(
                "businessLicense",
                "giay-phep.pdf",
                "application/pdf",
                "<script>not a pdf</script>".getBytes()
        );

        assertThatThrownBy(() -> storage.store(file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("PDF, PNG hoặc JPG");
    }
}
