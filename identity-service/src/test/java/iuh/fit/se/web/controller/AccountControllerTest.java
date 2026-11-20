package iuh.fit.se.web.controller;

import iuh.fit.se.domain.dto.request.UpdateEmailRequest;
import iuh.fit.se.domain.dto.response.CurrentAccountResponse;
import iuh.fit.se.domain.entity.Account;
import iuh.fit.se.domain.enums.AccountRole;
import iuh.fit.se.domain.enums.AuthProvider;
import iuh.fit.se.mapper.AccountMapper;
import iuh.fit.se.repository.AccountRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.web.server.ResponseStatusException;

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

    @Spy
    private AccountMapper accountMapper = Mappers.getMapper(AccountMapper.class);

    @InjectMocks
    private AccountController controller;

    @Test
    void updateEmailNormalizesAndSavesForLocalAccount() {
        UUID accountId = UUID.randomUUID();
        Account account = Account.builder()
                .id(accountId)
                .phone("0912345678")
                .email(null)
                .emailVerified(true)
                .authProvider(AuthProvider.LOCAL)
                .role(AccountRole.SHIPPER)
                .build();
        when(accountRepository.findWithUserProfileById(accountId)).thenReturn(Optional.of(account));
        when(accountRepository.existsByEmailIgnoreCaseAndIdNot("new.mail@company.vn", accountId)).thenReturn(false);

        CurrentAccountResponse response = controller.updateEmail(
                new TestingAuthenticationToken(accountId.toString(), null),
                new UpdateEmailRequest("  New.Mail@Company.VN "));

        assertThat(response.email()).isEqualTo("new.mail@company.vn");
        assertThat(response.emailVerified()).isFalse();
        assertThat(account.getEmail()).isEqualTo("new.mail@company.vn");
        verify(accountRepository).save(account);
    }

    @Test
    void updateEmailRejectsDuplicateOrGoogleLinkedAccount() {
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
                new UpdateEmailRequest("taken@company.vn")))
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
                new UpdateEmailRequest("other@gmail.com")))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }
}
