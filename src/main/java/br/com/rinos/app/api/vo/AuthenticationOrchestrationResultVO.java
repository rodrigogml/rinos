package br.com.rinos.app.api.vo;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import br.com.rinos.app.api.enums.AuthenticationAssuranceEnum;
import br.com.rinos.app.api.enums.AuthenticationMethodEnum;
import br.com.rinos.app.api.enums.AuthenticationOrchestrationStatusEnum;

/**
 * Resultado do núcleo de autenticação, anterior à publicação pelo RFW.
 *
 * @author Rodrigo Leitão
 * @since 2026-08-08
 */
public record AuthenticationOrchestrationResultVO(
    AuthenticationOrchestrationStatusEnum status,
    String continuationReference,
    RinosUserPrincipalVO principal,
    AuthenticationAssuranceEnum achievedAssurance,
    Set<AuthenticationMethodEnum> permittedMethods,
    List<AuthenticationMethodEvidenceVO> verifiedMethods,
    Set<String> missingLegalDocumentIds,
    boolean persistentLoginRequested,
    Instant expiresAt,
    UUID correlationId) {

  public AuthenticationOrchestrationResultVO {
    Objects.requireNonNull(status, "status must not be null");
    permittedMethods = permittedMethods == null ? Set.of() : Set.copyOf(permittedMethods);
    verifiedMethods = verifiedMethods == null ? List.of() : List.copyOf(verifiedMethods);
    missingLegalDocumentIds = missingLegalDocumentIds == null
        ? Set.of() : Set.copyOf(missingLegalDocumentIds);
    if (status == AuthenticationOrchestrationStatusEnum.READY
        && (principal == null || continuationReference == null || continuationReference.isBlank()
            || achievedAssurance == null || verifiedMethods.isEmpty())) {
      throw new IllegalArgumentException("ready factor flow is incomplete");
    }
    if (status == AuthenticationOrchestrationStatusEnum.CHALLENGE_REQUIRED
        && (continuationReference == null || continuationReference.isBlank()
            || permittedMethods.isEmpty() || expiresAt == null)) {
      throw new IllegalArgumentException("authentication challenge is incomplete");
    }
    if (status == AuthenticationOrchestrationStatusEnum.LEGAL_CONSENT_REQUIRED
        && (continuationReference == null || continuationReference.isBlank()
            || missingLegalDocumentIds.isEmpty() || expiresAt == null)) {
      throw new IllegalArgumentException("legal consent challenge is incomplete");
    }
  }

  /** Oculta a continuação opaca em diagnósticos. */
  @Override
  public String toString() {
    return "AuthenticationOrchestrationResultVO[status=" + status
        + ", continuationReference=REDACTED, principal=" + principal
        + ", achievedAssurance=" + achievedAssurance + ", permittedMethods="
        + permittedMethods + ", verifiedMethods=" + verifiedMethods
        + ", missingLegalDocumentIds=" + missingLegalDocumentIds
        + ", persistentLoginRequested=" + persistentLoginRequested + ", expiresAt="
        + expiresAt + ", correlationId=" + correlationId + "]";
  }
}
