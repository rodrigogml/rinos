package br.com.rinos.app.backend.module.identity.vo;

import java.util.Objects;

/**
 * Resultado protegido da preparação da credencial.
 *
 * <p>O hash só pode ser obtido explicitamente para persistência e nunca integra {@link #toString()}.
 *
 * @author Rodrigo Leitão
 * @since 2026-07-29
 */
public final class PasswordPreparationResultVO {

  private final PasswordValidationResultVO validation;
  private final String encodedHash;

  /**
   * Cria um resultado aceito ou rejeitado de forma consistente.
   *
   * @param validation resultado público
   * @param encodedHash hash somente quando aceito
   */
  public PasswordPreparationResultVO(
      PasswordValidationResultVO validation,
      String encodedHash) {
    this.validation = Objects.requireNonNull(validation, "validation must not be null");
    if (validation.accepted() != (encodedHash != null)) {
      throw new IllegalArgumentException("accepted validation and encodedHash must agree");
    }
    this.encodedHash = encodedHash;
  }

  /**
   * Retorna o resultado público.
   *
   * @return violações sem dados sensíveis
   */
  public PasswordValidationResultVO getValidation() {
    return validation;
  }

  /**
   * Retorna o hash somente para entrega imediata à persistência.
   *
   * @return hash com identificador e parâmetros
   * @throws IllegalStateException quando a senha foi rejeitada
   */
  public String getEncodedHash() {
    if (encodedHash == null) {
      throw new IllegalStateException("rejected password does not have an encoded hash");
    }
    return encodedHash;
  }

  /**
   * Não expõe o hash em representação diagnóstica.
   *
   * @return estado público reduzido
   */
  @Override
  public String toString() {
    return "PasswordPreparationResultVO[accepted=" + validation.accepted()
        + ", violations=" + validation.violations() + "]";
  }
}
