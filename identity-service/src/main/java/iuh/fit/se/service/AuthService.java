package iuh.fit.se.service;

import iuh.fit.se.domain.dto.request.LoginRequest;
import iuh.fit.se.domain.dto.request.RegisterRequest;
import iuh.fit.se.domain.dto.response.AuthResponse;
import iuh.fit.se.domain.entity.Account;
import iuh.fit.se.domain.entity.RefreshToken;
import iuh.fit.se.domain.enums.AccountStatus;
import iuh.fit.se.mapper.AuthMapper;
import iuh.fit.se.repository.AccountRepository;
import iuh.fit.se.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import iuh.fit.se.domain.dto.response.UserMeResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final AccountRepository accountRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final AuthMapper authMapper;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (accountRepository.existsByPhone(request.getPhone())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Phone number already exists");
        }

        Account account = Account.builder()
                .phone(request.getPhone())
                .email(request.getEmail())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .role(request.getRole())
                .status(AccountStatus.ACTIVE)
                .build();

        account = accountRepository.save(account);

        String accessToken = jwtTokenProvider.generateAccessToken(account);
        RefreshToken refreshToken = jwtTokenProvider.generateRefreshToken(account);

        return authMapper.toResponse(account, accessToken, refreshToken.getToken());
    }

    public AuthResponse login(LoginRequest request) {
        Account account = accountRepository.findByPhone(request.getPhone())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));

        if (!passwordEncoder.matches(request.getPassword(), account.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid password");
        }

        if (account.getStatus() != AccountStatus.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Account is not active");
        }

        String accessToken = jwtTokenProvider.generateAccessToken(account);
        RefreshToken refreshToken = jwtTokenProvider.generateRefreshToken(account);

        return authMapper.toResponse(account, accessToken, refreshToken.getToken());
    }

    public AuthResponse refresh(String token) {
        RefreshToken refreshToken = jwtTokenProvider.validateRefreshToken(token);
        Account account = refreshToken.getAccount();
        
        String newAccessToken = jwtTokenProvider.generateAccessToken(account);
        return authMapper.toResponse(account, newAccessToken, refreshToken.getToken());
    }

    @Transactional(readOnly = true)
    public UserMeResponse getMe(String identifier) {
        Account account;
        try {
            UUID accountId = UUID.fromString(identifier);
            account = accountRepository.findById(accountId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Account not found: " + identifier));
        } catch (IllegalArgumentException e) {
            account = accountRepository.findByPhone(identifier)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Account not found: " + identifier));
        }

        String fullName = account.getUserProfile() != null ? account.getUserProfile().getFullName() : null;
        String avatarUrl = account.getUserProfile() != null ? account.getUserProfile().getAvatarUrl() : null;
        String address = account.getUserProfile() != null ? account.getUserProfile().getAddress() : null;
        String companyName = account.getCompany() != null ? account.getCompany().getCompanyName() : null;

        return UserMeResponse.builder()
                .accountId(account.getId())
                .phone(account.getPhone())
                .email(account.getEmail())
                .role(account.getRole() != null ? account.getRole().name() : null)
                .fullName(fullName)
                .avatarUrl(avatarUrl)
                .address(address)
                .companyName(companyName)
                .build();
    }
}
