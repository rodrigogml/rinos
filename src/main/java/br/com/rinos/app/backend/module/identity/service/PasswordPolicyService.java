package br.com.rinos.app.backend.module.identity.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.springframework.stereotype.Service;

import br.com.rinos.app.backend.module.identity.enums.PasswordPolicyViolationEnum;
import br.com.rinos.app.backend.module.identity.enums.PwnedPasswordStatusEnum;
import br.com.rinos.app.backend.module.identity.vo.PasswordValidationResultVO;

/**
 * Aplica a política funcional e a verificação obrigatória de comprometimento.
 *
 * @author Rodrigo Leitão
 * @since 2026-07-29
 */
@Service
public class PasswordPolicyService {

  private static final int MINIMUM_LENGTH = 10;
  private static final int MAXIMUM_LENGTH = 128;

  private final CommonPasswordService commonPasswordService;
  private final PwnedPasswordsService pwnedPasswordsService;

  /**
   * Cria a política composta.
   *
   * @param commonPasswordService lista local
   * @param pwnedPasswordsService consulta k-anônima
   */
  public PasswordPolicyService(
      CommonPasswordService commonPasswordService,
      PwnedPasswordsService pwnedPasswordsService) {
    this.commonPasswordService = commonPasswordService;
    this.pwnedPasswordsService = pwnedPasswordsService;
  }

  /**
   * Avalia uma senha efêmera sem alterar o array pertencente ao chamador.
   *
   * @param password senha a avaliar; o chamador deve apagá-la depois do hashing ou rejeição
   * @return violações públicas em ordem estável
   */
  public PasswordValidationResultVO validate(char[] password) {
    Objects.requireNonNull(password, "password must not be null");
    List<PasswordPolicyViolationEnum> violations = validateLocalRules(password);
    if (!violations.isEmpty()) {
      return new PasswordValidationResultVO(violations);
    }
    if (commonPasswordService.isCommon(password)) {
      return new PasswordValidationResultVO(
          List.of(PasswordPolicyViolationEnum.COMMON_PASSWORD));
    }
    PwnedPasswordStatusEnum status = pwnedPasswordsService.check(password);
    return switch (status) {
      case SAFE -> new PasswordValidationResultVO(List.of());
      case COMPROMISED -> new PasswordValidationResultVO(
          List.of(PasswordPolicyViolationEnum.COMPROMISED_PASSWORD));
      case UNAVAILABLE -> new PasswordValidationResultVO(
          List.of(PasswordPolicyViolationEnum.COMPROMISE_CHECK_UNAVAILABLE));
    };
  }

  private static List<PasswordPolicyViolationEnum> validateLocalRules(char[] password) {
    List<PasswordPolicyViolationEnum> violations = new ArrayList<>();
    int length = Character.codePointCount(password, 0, password.length);
    if (length < MINIMUM_LENGTH) {
      violations.add(PasswordPolicyViolationEnum.MINIMUM_LENGTH_REQUIRED);
    }
    if (length > MAXIMUM_LENGTH) {
      violations.add(PasswordPolicyViolationEnum.MAXIMUM_LENGTH_EXCEEDED);
    }
    boolean uppercase = false;
    boolean lowercase = false;
    boolean number = false;
    boolean special = false;
    for (int index = 0; index < password.length; ) {
      int codePoint = Character.codePointAt(password, index, password.length);
      uppercase |= Character.isUpperCase(codePoint);
      lowercase |= Character.isLowerCase(codePoint);
      number |= Character.isDigit(codePoint);
      special |= !Character.isLetterOrDigit(codePoint)
          && !Character.isWhitespace(codePoint)
          && !Character.isISOControl(codePoint);
      index += Character.charCount(codePoint);
    }
    if (!uppercase) {
      violations.add(PasswordPolicyViolationEnum.UPPERCASE_REQUIRED);
    }
    if (!lowercase) {
      violations.add(PasswordPolicyViolationEnum.LOWERCASE_REQUIRED);
    }
    if (!number) {
      violations.add(PasswordPolicyViolationEnum.NUMBER_REQUIRED);
    }
    if (!special) {
      violations.add(PasswordPolicyViolationEnum.SPECIAL_CHARACTER_REQUIRED);
    }
    return violations;
  }
}
