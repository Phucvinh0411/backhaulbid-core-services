package iuh.fit.se.domain.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record GoogleAuthResponse(
        boolean needsOnboarding,
        String onboardingToken,
        String email,
        String fullName,
        String avatarUrl,
        AuthResponse auth) {

    public static GoogleAuthResponse authenticated(AuthResponse auth) {
        return new GoogleAuthResponse(false, null, null, null, null, auth);
    }

    public static GoogleAuthResponse onboarding(
            String onboardingToken,
            String email,
            String fullName,
            String avatarUrl) {
        return new GoogleAuthResponse(true, onboardingToken, email, fullName, avatarUrl, null);
    }
}
