package br.com.rinos.app.backend.module.identity.vo;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import br.com.rinos.app.backend.module.identity.enums.AuthenticationAssuranceEnum;
import br.com.rinos.app.backend.module.identity.enums.AuthenticationMethodEnum;
import br.com.rinos.app.backend.module.identity.enums.AuthenticationOrchestrationStatusEnum;

/**
 * Decisão interna completa do orquestrador sem entidade ou material de credencial.
 *
 * @author Rodrigo Leitão
 * @since 2026-08-08
 */
public record AuthenticationOrchestrationDecisionVO(
    AuthenticationOrchestrationStatusEnum status,
    String continuationReference,
    Long userId,
    String email,
    AuthenticationAssuranceEnum achievedAssurance,
    Set<AuthenticationMethodEnum> permittedMethods,
    List<AuthenticationFlowVerifiedMethodVO> verifiedMethods,
    Set<Long> missingLegalDocumentIds,
    boolean persistentLoginRequested,
    Instant expiresAt,
    UUID correlationId) {

  public AuthenticationOrchestrationDecisionVO {
    Objects.requireNonNull(status, "status must not be null");
    permittedMethods = permittedMethods == null ? Set.of() : Set.copyOf(permittedMethods);
    verifiedMethods = verifiedMethods == null ? List.of() : List.copyOf(verifiedMethods);
    missingLegalDocumentIds = missingLegalDocumentIds == null
        ? Set.of() : Set.copyOf(missingLegalDocumentIds);
  }

  /** Oculta referência, identidade e e-mail em diagnósticos. */
  @Override
  public String toString() {
    return "AuthenticationOrchestrationDecisionVO[status=" + status
        + ", continuationReference=REDACTED, userId=REDACTED, email=REDACTED"
        + ", achievedAssurance=" + achievedAssurance + ", permittedMethods="
        + permittedMethods + ", verifiedMethods=" + verifiedMethods
        + ", missingLegalDocumentIds=" + missingLegalDocumentIds
        + ", persistentLoginRequested=" + persistentLoginRequested + ", expiresAt="
        + expiresAt + ", correlationId=" + correlationId + "]";
  }
}
