package br.com.rinos.app.backend.module.access.component;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import br.com.rinos.app.api.module.access.dto.AuthorizationRequest;
import br.com.rinos.app.api.module.access.enums.AccessScope;
import br.com.rinos.app.api.module.access.enums.AuthorizationActorType;
import br.com.rinos.app.api.module.access.vo.AuthorizationGateResult;
import br.com.rinos.app.backend.module.access.service.AuthorizationStructuralGateProvider;
import br.com.rinos.app.backend.module.access.service.AccountMembershipAccessPort;
import br.com.rinos.app.backend.module.access.service.AccountMembershipAccessSnapshot;
import br.com.rinos.app.backend.module.access.service.SystemOperationAuthorizer;
import br.com.rinos.app.backend.module.identity.enums.UserStatusEnum;
import br.com.rinos.app.backend.module.identity.repository.UserRepository;

/**
 * Avalia identidade, associação e disponibilidade operacional pelo estado corrente dos módulos proprietários.
 *
 * <p>A requisição não é fonte de verdade para a associação: a porta de membership confirma a identidade, o tenant,
 * o estado da associação e o estado operacional do tenant antes da resolução de regras.</p>
 */
@Component
@org.springframework.context.annotation.Lazy
public class DefaultAuthorizationStructuralGateProvider
    implements AuthorizationStructuralGateProvider {

  private final UserRepository userRepository;
  private final AccountMembershipAccessPort membershipAccessPort;
  private final SystemOperationAuthorizer systemOperations;

  public DefaultAuthorizationStructuralGateProvider(
      UserRepository userRepository, AccountMembershipAccessPort membershipAccessPort,
      SystemOperationAuthorizer systemOperations) {
    this.userRepository = userRepository;
    this.membershipAccessPort = membershipAccessPort;
    this.systemOperations = systemOperations;
  }

  @Override
  public List<AuthorizationGateResult> evaluate(AuthorizationRequest request) {
    List<AuthorizationGateResult> gates = new ArrayList<>();
    if (request.actor().type() == AuthorizationActorType.SYSTEM) {
      boolean registered = systemOperations.matches(request);
      gates.add(new AuthorizationGateResult(
          "IDENTITY", true, null));
      gates.add(new AuthorizationGateResult(
          "SYSTEM_ORIGIN", registered,
          registered ? null : "ACL_SYSTEM_SOURCE_MISMATCH"));
      return gates;
    }
    boolean active = userRepository.findById(request.actor().identityId())
        .map(user -> user.getStatus() == UserStatusEnum.ACTIVE)
        .orElse(false);
    gates.add(new AuthorizationGateResult(
        "IDENTITY", active, active ? null : "ACL_IDENTITY_INACTIVE"));
    if (request.context().scope() == AccessScope.GLOBAL) {
      gates.add(new AuthorizationGateResult(
          "GLOBAL_CONTEXT", true, null));
    } else {
      AccountMembershipAccessSnapshot membership =
          membershipAccessPort.inspect(request.membershipId());
      if (!membership.sourceAvailable()) {
        gates.add(new AuthorizationGateResult(
            "MEMBERSHIP", false, "ACL_ASSOCIATION_UNAVAILABLE"));
        gates.add(new AuthorizationGateResult(
            "TENANT_CONTEXT", false, "ACL_ASSOCIATION_UNAVAILABLE"));
        return gates;
      }
      boolean belongsToActorAndTenant = membership.exists()
          && request.actor().identityId().equals(membership.identityId())
          && request.context().tenantId().equals(membership.tenantId());
      gates.add(new AuthorizationGateResult(
          "MEMBERSHIP", belongsToActorAndTenant && membership.membershipActive(),
          belongsToActorAndTenant && membership.membershipActive()
              ? null : "ACL_ASSOCIATION_INACTIVE"));
      gates.add(new AuthorizationGateResult(
          "TENANT_CONTEXT", belongsToActorAndTenant && membership.tenantOperational(),
          belongsToActorAndTenant && membership.tenantOperational()
              ? null : "ACL_TENANT_UNAVAILABLE"));
    }
    return gates;
  }
}
