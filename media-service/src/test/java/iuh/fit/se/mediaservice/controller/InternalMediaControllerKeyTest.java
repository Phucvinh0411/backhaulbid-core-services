package iuh.fit.se.mediaservice.controller;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class InternalMediaControllerKeyTest {

    @Test
    void resolvePrivateObjectKeyAllowsBusinessVerificationKeysAndUrlsAndRejectsTraversal() {
        String licenseKey = "business-verifications/licenses/" + UUID.randomUUID() + ".pdf";
        String authKey = "business-verifications/authorization/" + UUID.randomUUID() + ".jpg";

        assertThat(InternalMediaController.resolvePrivateObjectKey(licenseKey)).isEqualTo(licenseKey);
        assertThat(InternalMediaController.resolvePrivateObjectKey(
                "https://backhaulbid-media.s3.ap-southeast-1.amazonaws.com/" + authKey)).isEqualTo(authKey);
        assertThat(InternalMediaController.resolvePrivateObjectKey("business-verifications/licenses/../secret.pdf")).isNull();
        assertThat(InternalMediaController.resolvePrivateObjectKey("avatars/" + UUID.randomUUID() + ".png")).isNull();
    }

    @Test
    void resolvePrivateObjectKeyAllowsFleetVehicleAndDriverDocumentKeysAndUrls() {
        String regKey = "vehicles/registration/" + UUID.randomUUID() + ".png";
        String inspKey = "vehicles/inspection/" + UUID.randomUUID() + ".pdf";
        String drvKey = "drivers/license/" + UUID.randomUUID() + ".jpg";

        assertThat(InternalMediaController.resolvePrivateObjectKey(regKey)).isEqualTo(regKey);
        assertThat(InternalMediaController.resolvePrivateObjectKey(
                "https://backhaulbid-s3.s3.ap-southeast-2.amazonaws.com/" + inspKey)).isEqualTo(inspKey);
        assertThat(InternalMediaController.resolvePrivateObjectKey(
                "https://backhaulbid-s3.s3.ap-southeast-2.amazonaws.com/" + drvKey)).isEqualTo(drvKey);
    }
}
