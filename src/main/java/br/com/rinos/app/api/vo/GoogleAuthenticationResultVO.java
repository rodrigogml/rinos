package br.com.rinos.app.api.vo;

import java.util.Objects;

import br.com.rinos.app.api.enums.GoogleAuthenticationStatusEnum;

/**
 * Entrega ao adapter somente a decisão de login ou a necessidade de continuar um novo cadastro.
 *
 * @param status destino interno da identidade validada
 * @param orchestration resultado presente somente quando o login foi processado
 * @author Rodrigo Leitão
 * @since 2026-08-10
 */
public record GoogleAuthenticationResultVO(
    GoogleAuthenticationStatusEnum status,
    AuthenticationOrchestrationResultVO orchestration) {

  public GoogleAuthenticationResultVO {
    Objects.requireNonNull(status, "status must not be null");
    if (status == GoogleAuthenticationStatusEnum.ORCHESTRATED && orchestration == null) {
      throw new IllegalArgumentException("orchestrated login requires a result");
    }
    if (status == GoogleAuthenticationStatusEnum.IDENTITY_NOT_FOUND && orchestration != null) {
      throw new IllegalArgumentException("absent identity must not expose orchestration");
    }
  }

  /** Cria um resultado que deve ser mapeado pela máquina de estados de autenticação. */
  public static GoogleAuthenticationResultVO orchestrated(
      AuthenticationOrchestrationResultVO orchestration) {
    return new GoogleAuthenticationResultVO(
        GoogleAuthenticationStatusEnum.ORCHESTRATED, orchestration);
  }

  /** Permite somente ao adapter server-side continuar o cadastro externo. */
  public static GoogleAuthenticationResultVO identityNotFound() {
    return new GoogleAuthenticationResultVO(
        GoogleAuthenticationStatusEnum.IDENTITY_NOT_FOUND, null);
  }
}
