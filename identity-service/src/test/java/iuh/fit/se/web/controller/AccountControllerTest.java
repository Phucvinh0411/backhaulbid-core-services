package iuh.fit.se.web.controller;

import iuh.fit.se.domain.dto.request.SendEmailOtpRequest;
import iuh.fit.se.domain.dto.request.UpdateEmailRequest;
import iuh.fit.se.domain.dto.request.UpdatePasswordRequest;
import iuh.fit.se.domain.dto.response.CurrentAccountResponse;
import iuh.fit.se.domain.dto.response.SendEmailOtpResponse;
import iuh.fit.se.domain.entity.Account;
import iuh.fit.se.domain.enums.AccountRole;
import iuh.fit.se.domain.enums.AuthProvider;
import iuh.fit.se.mapper.AccountMapper;
import iuh.fit.se.repository.AccountRepository;
import iuh.fit.se.service.EmailChangeOtpService;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountControllerTest {

    @Mock
    private AccountRepository accountRepository;
    @Mock
    private EmailChangeOtpService emailChangeOtpService;
    @Mock
    private PasswordEncoder passwordEncoder;

    @Spy
    private AccountMapper accountMapper = Mappers.getMapper(AccountMapper.class);

    @InjectMocks
    private AccountController controller;

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void requestEmailOtpAndUpdateEmailVerifyOtpAndMarkVerified() {
        UUID accountId = UUID.randomUUID();
        Account account = Account.builder()
                .id(accountId)
                .phone("0912345678")
                .email(null)
                .emailVerified(false)
                .authProvider(AuthProvider.LOCAL)
                .role(AccountRole.SHIPPER)
                .build();
        when(accountRepository.findWithUserProfileById(accountId)).thenReturn(Optional.of(account));
        when(accountRepository.existsByEmailIgnoreCaseAndIdNot("new.mail@company.vn", accountId)).thenReturn(false);
        when(emailChangeOtpService.sendOtp(accountId, "new.mail@company.vn", null))
                .thenReturn(new SendEmailOtpResponse("ne***@company.vn", 60, 10));

        SendEmailOtpResponse otpResponse = controller.requestEmailChangeOtp(
                new TestingAuthenticationToken(accountId.toString(), null),
                new SendEmailOtpRequest("  New.Mail@Company.VN "));
        assertThat(otpResponse.maskedEmail()).isEqualTo("ne***@company.vn");

        CurrentAccountResponse response = controller.updateEmail(
                new TestingAuthenticationToken(accountId.toString(), null),
                new UpdateEmailRequest("  New.Mail@Company.VN ", "123456"));

        verify(emailChangeOtpService).verifyAndConsumeOtp(accountId, "new.mail@company.vn", "123456");
        assertThat(response.email()).isEqualTo("new.mail@company.vn");
        assertThat(response.emailVerified()).isTrue();
        assertThat(account.getEmail()).isEqualTo("new.mail@company.vn");
        verify(accountRepository).save(account);
    }

    @Test
    void updateEmailRejectsDuplicateOrGoogleLinkedAccountAndInvalidTldFormat() {
        assertThat(validator.validate(new UpdateEmailRequest("thuha.nguyen@saigonxanhfood.vnkhonghople", "123456")))
                .isNotEmpty();
        assertThat(validator.validate(new UpdateEmailRequest("valid.user@company.vn", "123456")))
                .isEmpty();

        UUID accountId = UUID.randomUUID();
        Account local = Account.builder()
                .id(accountId)
                .phone("0912345678")
                .authProvider(AuthProvider.LOCAL)
                .role(AccountRole.CARRIER)
                .build();
        when(accountRepository.findWithUserProfileById(accountId)).thenReturn(Optional.of(local));
        when(accountRepository.existsByEmailIgnoreCaseAndIdNot("taken@company.vn", accountId)).thenReturn(true);

        assertThatThrownBy(() -> controller.updateEmail(
                new TestingAuthenticationToken(accountId.toString(), null),
                new UpdateEmailRequest("taken@company.vn", "123456")))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);

        Account googleAccount = Account.builder()
                .id(accountId)
                .phone("0912345678")
                .email("google@gmail.com")
                .googleSub("sub-123")
                .authProvider(AuthProvider.GOOGLE)
                .role(AccountRole.SHIPPER)
                .build();
        when(accountRepository.findWithUserProfileById(accountId)).thenReturn(Optional.of(googleAccount));

        assertThatThrownBy(() -> controller.updateEmail(
                new TestingAuthenticationToken(accountId.toString(), null),
                new UpdateEmailRequest("other@gmail.com", "123456")))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void updatePasswordVerifiesCurrentAndEncodesNewPassword() {
        UUID accountId = UUID.randomUUID();
        Account account = Account.builder()
                .id(accountId)
                .phone("0912345678")
                .passwordHash("encoded-old")
                .authProvider(AuthProvider.LOCAL)
                .role(AccountRole.SHIPPER)
                .build();
        when(accountRepository.findWithUserProfileById(accountId)).thenReturn(Optional.of(account));
        when(passwordEncoder.matches("WrongPass1!", "encoded-old")).thenReturn(false);

        assertThatThrownBy(() -> controller.updatePassword(
                new TestingAuthenticationToken(accountId.toString(), null),
                new UpdatePasswordRequest("WrongPass1!", "NewPass123!")))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);

        when(passwordEncoder.matches("OldPass123!", "encoded-old")).thenReturn(true);
        when(passwordEncoder.matches("NewPass123!", "encoded-old")).thenReturn(false);
        when(passwordEncoder.encode("NewPass123!")).thenReturn("encoded-new");

        Map<String, String> result = controller.updatePassword(
                new TestingAuthenticationToken(accountId.toString(), null),
                new UpdatePasswordRequest("OldPass123!", "NewPass123!"));

        assertThat(result).containsKey("message");
        assertThat(account.getPasswordHash()).isEqualTo("encoded-new");
        verify(accountRepository).save(account);
    }
}
