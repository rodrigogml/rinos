package br.com.rinos.app.backend.module.identity.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import br.com.rinos.app.backend.module.identity.enums.PasswordPolicyViolationEnum;
import br.com.rinos.app.backend.module.identity.enums.PwnedPasswordStatusEnum;
import br.com.rinos.app.backend.module.identity.vo.PasswordValidationResultVO;

@DisplayName("Política pública de senha")
class PasswordPolicyServiceTest {

  @Test
  void validate_shouldReturnEveryMissingRule_withoutCallingExternalService() {
    PwnedPasswordsService pwned = mock(PwnedPasswordsService.class);
    PasswordPolicyService service = new PasswordPolicyService(
        new CommonPasswordService(),
        pwned);

    PasswordValidationResultVO result = service.validate("abc".toCharArray());

    assertThat(result.accepted()).isFalse();
    assertThat(result.violations()).containsExactly(
        PasswordPolicyViolationEnum.MINIMUM_LENGTH_REQUIRED,
        PasswordPolicyViolationEnum.UPPERCASE_REQUIRED,
        PasswordPolicyViolationEnum.NUMBER_REQUIRED,
        PasswordPolicyViolationEnum.SPECIAL_CHARACTER_REQUIRED);
    verifyNoInteractions(pwned);
  }

  @Test
  void validate_shouldRejectCommonPasswordLocally() {
    PwnedPasswordsService pwned = mock(PwnedPasswordsService.class);
    PasswordPolicyService service = new PasswordPolicyService(
        new CommonPasswordService(),
        pwned);

    assertThat(service.validate("Password1!".toCharArray()).violations())
        .containsExactly(PasswordPolicyViolationEnum.COMMON_PASSWORD);
    verifyNoInteractions(pwned);
  }

  @Test
  void validate_shouldMapCompromisedUnavailableAndSafeResults() {
    PwnedPasswordsService pwned = mock(PwnedPasswordsService.class);
    PasswordPolicyService service = new PasswordPolicyService(
        new CommonPasswordService(),
        pwned);
    char[] password = "Unique-Password-7!".toCharArray();

    when(pwned.check(password)).thenReturn(PwnedPasswordStatusEnum.COMPROMISED);
    assertThat(service.validate(password).violations())
        .containsExactly(PasswordPolicyViolationEnum.COMPROMISED_PASSWORD);
    when(pwned.check(password)).thenReturn(PwnedPasswordStatusEnum.UNAVAILABLE);
    assertThat(service.validate(password).violations())
        .containsExactly(PasswordPolicyViolationEnum.COMPROMISE_CHECK_UNAVAILABLE);
    when(pwned.check(password)).thenReturn(PwnedPasswordStatusEnum.SAFE);
    assertThat(service.validate(password).accepted()).isTrue();
  }

  @Test
  void validate_shouldCountUnicodeCodePoints_andEnforceMaximum() {
    PwnedPasswordsService pwned = mock(PwnedPasswordsService.class);
    PasswordPolicyService service = new PasswordPolicyService(
        new CommonPasswordService(),
        pwned);

    assertThat(service.validate(("A1!" + "a".repeat(126)).toCharArray()).violations())
        .containsExactly(PasswordPolicyViolationEnum.MAXIMUM_LENGTH_EXCEEDED);
    verifyNoInteractions(pwned);
  }
}
