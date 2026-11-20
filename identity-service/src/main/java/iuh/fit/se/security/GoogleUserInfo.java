package iuh.fit.se.security;

public record GoogleUserInfo(
        String sub,
        String email,
        String fullName,
        String avatarUrl) {
}
