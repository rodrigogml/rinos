package br.com.rinos.app.backend.module.identity.vo;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import br.com.rinos.app.backend.module.identity.enums.AuthenticationAssuranceEnum;
import br.com.rinos.app.backend.module.identity.enums.AuthenticationFlowPurposeEnum;
import br.com.rinos.app.backend.module.identity.enums.AuthenticationMethodEnum;
import br.com.rinos.app.backend.module.identity.enums.AuthenticationOperationStatusEnum;

/**
 * Fotografia interna do fluxo para decisões do orquestrador, sem referência ou segredo bruto.
 *
 * @author Rodrigo Leitão
 * @since 2026-08-08
 */
public record AuthenticationFlowSnapshotVO(
    AuthenticationOperationStatusEnum status,
    Long userId,
    AuthenticationFlowPurposeEnum purpose,
    AuthenticationMethodEnum primaryMethod,
    AuthenticationAssuranceEnum requiredAssurance,
    Set<AuthenticationMethodEnum> permittedMethods,
    List<AuthenticationFlowVerifiedMethodVO> verifiedMethods,
    boolean persistentLoginRequested,
    Instant expiresAt,
    UUID correlationId) {

  public AuthenticationFlowSnapshotVO {
    permittedMethods = permittedMethods == null ? Set.of() : Set.copyOf(permittedMethods);
    verifiedMethods = verifiedMethods == null ? List.of() : List.copyOf(verifiedMethods);
  }
}
