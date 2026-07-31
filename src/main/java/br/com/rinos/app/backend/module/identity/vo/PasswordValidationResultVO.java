package br.com.rinos.app.backend.module.identity.vo;

import java.util.List;

import br.com.rinos.app.backend.module.identity.enums.PasswordPolicyViolationEnum;

/**
 * Resultado público da política sem conter senha, hash ou resposta externa.
 *
 * @param violations violações em ordem determinística
 * @author Rodrigo Leitão
 * @since 2026-07-29
 */
public record PasswordValidationResultVO(List<PasswordPolicyViolationEnum> violations) {

  /**
   * Protege o resultado contra alteração externa.
   */
  public PasswordValidationResultVO {
    violations = List.copyOf(violations);
  }

  /**
   * Indica que todas as verificações obrigatórias foram concluídas.
   *
   * @return {@code true} quando não existe violação
   */
  public boolean accepted() {
    return violations.isEmpty();
  }
}
