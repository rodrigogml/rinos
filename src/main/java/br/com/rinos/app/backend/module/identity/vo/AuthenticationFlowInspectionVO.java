package br.com.rinos.app.backend.module.identity.vo;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import br.com.rinos.app.backend.module.identity.enums.AuthenticationAssuranceEnum;
import br.com.rinos.app.backend.module.identity.enums.AuthenticationFlowPurposeEnum;
import br.com.rinos.app.backend.module.identity.enums.AuthenticationMethodEnum;
import br.com.rinos.app.backend.module.identity.enums.AuthenticationOperationStatusEnum;

/**
 * Visão interna sanitizada de um fluxo, sem entidade ou evidência criptográfica.
 *
 * @author Rodrigo Leitão
 * @since 2026-08-08
 */
public record AuthenticationFlowInspectionVO(
    AuthenticationOperationStatusEnum status,
    Long userId,
    AuthenticationFlowPurposeEnum purpose,
    AuthenticationMethodEnum primaryMethod,
    AuthenticationAssuranceEnum requiredAssurance,
    Set<AuthenticationMethodEnum> permittedMethods,
    boolean persistentLoginRequested,
    Instant expiresAt,
    UUID correlationId) {

  public AuthenticationFlowInspectionVO {
    permittedMethods = permittedMethods == null ? Set.of() : Set.copyOf(permittedMethods);
  }

  /** Cria uma rejeição que não revela se a referência existia. */
  public static AuthenticationFlowInspectionVO rejected() {
    return new AuthenticationFlowInspectionVO(
        AuthenticationOperationStatusEnum.REJECTED,
        null,
        null,
        null,
        null,
        Set.of(),
        false,
        null,
        null);
  }
}
