package br.com.rinos.app.api.module.account.vo;

import java.util.Objects;

/**
 * Fotografia transitória e confiável da sessão que solicitou a criação de uma conta.
 *
 * <p>Este valor nunca é preenchido por formulário, serializado para o navegador ou persistido
 * pela feature de contas.
 *
 * @param userId identidade global autenticada
 * @param sessionReference referência opaca da sessão global
 * @param canonicalOrigin origem resolvida pela política de proxy confiável
 * @author Rodrigo Leitão
 * @since 2026-08-24
 */
public record AccountCreationContext(
    long userId,
    String sessionReference,
    String canonicalOrigin) {

  /** Valida a fotografia mínima antes que a fachada execute efeitos persistentes. */
  public AccountCreationContext {
    if (userId <= 0) {
      throw new IllegalArgumentException("userId must be positive");
    }
    sessionReference = required(sessionReference, "sessionReference");
    canonicalOrigin = required(canonicalOrigin, "canonicalOrigin");
  }

  /** Redige valores que não podem compor diagnósticos ou logs. */
  @Override
  public String toString() {
    return "AccountCreationContext[userId=REDACTED, sessionReference=REDACTED, "
        + "canonicalOrigin=REDACTED]";
  }

  private static String required(String value, String name) {
    String result = Objects.requireNonNull(value, name + " must not be null").strip();
    if (result.isEmpty()) {
      throw new IllegalArgumentException(name + " must not be blank");
    }
    return result;
  }
}
