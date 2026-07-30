package iuh.fit.se.web.controller;

import iuh.fit.se.domain.dto.request.LoginRequest;
import iuh.fit.se.domain.dto.request.RegisterRequest;
import iuh.fit.se.domain.dto.response.AuthResponse;
import iuh.fit.se.service.AuthService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@RequestBody RegisterRequest request) {
        return ResponseEntity.ok(authService.register(request));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@RequestBody LoginRequest request, HttpServletResponse response) {
        AuthResponse authResponse = authService.login(request);

        // Set HttpOnly Cookie cho accessToken (15 phút)
        Cookie accessToken = new Cookie("accessToken", authResponse.getAccessToken());
        accessToken.setHttpOnly(true);
        accessToken.setPath("/");
        accessToken.setMaxAge(15 * 60);
        response.addCookie(accessToken);

        // Set HttpOnly Cookie cho refreshToken (7 ngày)
        Cookie refreshToken = new Cookie("refreshToken", authResponse.getRefreshToken());
        refreshToken.setHttpOnly(true);
        refreshToken.setPath("/");
        refreshToken.setMaxAge(7 * 24 * 60 * 60);
        response.addCookie(refreshToken);

        return ResponseEntity.ok(authResponse);
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(HttpServletRequest request, HttpServletResponse response) {
        // Đọc refreshToken từ Cookie
        String refreshTokenValue = null;
        if (request.getCookies() != null) {
            for (Cookie c : request.getCookies()) {
                if ("refreshToken".equals(c.getName())) {
                    refreshTokenValue = c.getValue();
                    break;
                }
            }
        }
        if (refreshTokenValue == null) {
            return ResponseEntity.status(401).build();
        }

        AuthResponse authResponse = authService.refresh(refreshTokenValue);

        // Cập nhật lại accessToken Cookie mới
        Cookie newAccessToken = new Cookie("accessToken", authResponse.getAccessToken());
        newAccessToken.setHttpOnly(true);
        newAccessToken.setPath("/");
        newAccessToken.setMaxAge(15 * 60);
        response.addCookie(newAccessToken);

        return ResponseEntity.ok(authResponse);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletResponse response) {
        // Xóa cookies bằng cách set maxAge = 0
        Cookie accessToken = new Cookie("accessToken", "");
        accessToken.setHttpOnly(true);
        accessToken.setPath("/");
        accessToken.setMaxAge(0);
        response.addCookie(accessToken);

        Cookie refreshToken = new Cookie("refreshToken", "");
        refreshToken.setHttpOnly(true);
        refreshToken.setPath("/");
        refreshToken.setMaxAge(0);
        response.addCookie(refreshToken);

        return ResponseEntity.ok().build();
    }
}
