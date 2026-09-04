package br.com.rinos.app.api.vo;

import java.util.Objects;

/**
 * Entrega efêmera da ativação concluída ao lifecycle de sessão.
 *
 * <p>O valor só é emitido depois de a identidade e seus aceites obrigatórios terem sido
 * persistidos. A referência opaca não representa uma credencial reutilizável e é removida do
 * contexto depois de a sessão ser publicada.
 *
 * @param principal identidade ativa recém-confirmada
 * @param completion fluxo opaco a ser consumido pelo lifecycle de sessão
 * @author Rodrigo Leitão
 * @since 2026-09-01
 */
public record RegistrationAuthenticationContinuationVO(
    RinosUserPrincipalVO principal,
    RinosAuthenticationCompletionVO completion) {

  /** Exige a identidade ativa e sua continuação atômica. */
  public RegistrationAuthenticationContinuationVO {
    principal = Objects.requireNonNull(principal, "principal must not be null");
    completion = Objects.requireNonNull(completion, "completion must not be null");
  }

  /** Evita expor dados de sessão em diagnósticos. */
  @Override
  public String toString() {
    return "RegistrationAuthenticationContinuationVO[principal=REDACTED, completion=REDACTED]";
  }
}
