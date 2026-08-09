package br.com.rinos.app.backend.module.identity.service;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import br.com.rinos.app.backend.module.identity.enums.AuthenticationAssuranceEnum;
import br.com.rinos.app.backend.module.identity.enums.AuthenticationMethodEnum;
import br.com.rinos.app.backend.module.identity.enums.ReauthenticationOperationEnum;
import br.com.rinos.app.backend.module.identity.enums.ReauthenticationPolicyStatusEnum;
import br.com.rinos.app.backend.module.identity.vo.ReauthenticationPolicyDecisionVO;
import br.com.rinos.app.backend.module.identity.vo.VerifiedAuthSessionMethodVO;
import br.com.rinos.app.config.AuthenticationSessionPropertiesConfig;

/**
 * Calcula garantia recente e catálogo de métodos sem confiar em estado da interface.
 *
 * @author Rodrigo Leitão
 */
@Service
@Lazy
public class ReauthenticationPolicyService {

  private final AuthenticationAssurancePolicyService assurancePolicy;
  private final AuthenticationSessionPropertiesConfig properties;

  /** Cria a política com a validade fixa definida no arquivo de propriedades. */
  public ReauthenticationPolicyService(
      AuthenticationAssurancePolicyService assurancePolicy,
      AuthenticationSessionPropertiesConfig properties) {
    this.assurancePolicy = Objects.requireNonNull(assurancePolicy, "assurancePolicy must not be null");
    this.properties = Objects.requireNonNull(properties, "properties must not be null");
  }

  /**
   * Avalia a sessão corrente para a operação e, se necessário, oferece somente métodos ainda
   * utilizáveis e compatíveis.
   */
  public ReauthenticationPolicyDecisionVO evaluate(
      ReauthenticationOperationEnum operation,
      AuthenticationAssuranceEnum sessionAssurance,
      Instant lastStrongAuthAt,
      List<VerifiedAuthSessionMethodVO> sessionMethods,
      Set<AuthenticationMethodEnum> availableMethods,
      Instant occurredAt) {
    Objects.requireNonNull(operation, "operation must not be null");
    Objects.requireNonNull(sessionMethods, "sessionMethods must not be null");
    Objects.requireNonNull(availableMethods, "availableMethods must not be null");
    Objects.requireNonNull(occurredAt, "occurredAt must not be null");
    Set<AuthenticationMethodEnum> compatibleAvailable = operation.allowedMethods().stream()
        .filter(availableMethods::contains)
        .collect(Collectors.toUnmodifiableSet());
    boolean compatibleEvidence = sessionMethods.stream()
        .map(VerifiedAuthSessionMethodVO::method)
        .anyMatch(operation.allowedMethods()::contains);
    boolean recent = sessionAssurance != null
        && lastStrongAuthAt != null
        && !occurredAt.isBefore(lastStrongAuthAt)
        && !occurredAt.isAfter(lastStrongAuthAt.plus(properties.reauthenticationValidity()))
        && compatibleEvidence
        && assurancePolicy.satisfies(sessionAssurance, operation.requiredAssurance());
    if (recent) {
      return new ReauthenticationPolicyDecisionVO(
          ReauthenticationPolicyStatusEnum.ALREADY_RECENT,
          operation.labelKey(),
          Set.of());
    }
    if (compatibleAvailable.isEmpty()) {
      return new ReauthenticationPolicyDecisionVO(
          ReauthenticationPolicyStatusEnum.ACCESS_DENIED,
          operation.labelKey(),
          Set.of());
    }
    return new ReauthenticationPolicyDecisionVO(
        ReauthenticationPolicyStatusEnum.CHALLENGE_REQUIRED,
        operation.labelKey(),
        compatibleAvailable);
  }
}
