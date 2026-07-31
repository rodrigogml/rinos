package br.com.rinos.app.backend.module.identity.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import br.com.rinos.app.backend.module.identity.enums.PasswordPolicyViolationEnum;
import br.com.rinos.app.backend.module.identity.vo.PasswordPreparationResultVO;
import br.com.rinos.app.backend.module.identity.vo.PasswordValidationResultVO;

@DisplayName("Preparação protegida da senha")
class PasswordPreparationServiceTest {

  @Test
  void prepare_shouldClearInputAndHideHash_whenPasswordIsAccepted() {
    PasswordPolicyService policy = mock(PasswordPolicyService.class);
    PasswordEncoder encoder = mock(PasswordEncoder.class);
    char[] password = "Unique-Password-7!".toCharArray();
    when(policy.validate(password)).thenReturn(new PasswordValidationResultVO(List.of()));
    when(encoder.encode(org.mockito.ArgumentMatchers.any()))
        .thenReturn("{argon2id}$argon2id$secret-hash");
    PasswordPreparationService service = new PasswordPreparationService(policy, encoder);

    PasswordPreparationResultVO result = service.prepare(password);

    assertThat(password).containsOnly('\0');
    assertThat(result.getEncodedHash()).startsWith("{argon2id}");
    assertThat(result.toString()).doesNotContain("secret-hash", "$argon2id$");
  }

  @Test
  void prepare_shouldClearInput_whenPasswordIsRejected() {
    PasswordPolicyService policy = mock(PasswordPolicyService.class);
    char[] password = "short".toCharArray();
    when(policy.validate(password)).thenReturn(new PasswordValidationResultVO(
        List.of(PasswordPolicyViolationEnum.MINIMUM_LENGTH_REQUIRED)));
    PasswordPreparationService service =
        new PasswordPreparationService(policy, mock(PasswordEncoder.class));

    PasswordPreparationResultVO result = service.prepare(password);

    assertThat(password).containsOnly('\0');
    assertThat(result.getValidation().accepted()).isFalse();
  }
}
