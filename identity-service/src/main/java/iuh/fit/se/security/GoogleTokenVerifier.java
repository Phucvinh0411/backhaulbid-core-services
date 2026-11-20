package iuh.fit.se.security;

public interface GoogleTokenVerifier {
    GoogleUserInfo verify(String idToken);
}
