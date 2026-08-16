package br.com.rinos.app.backend.module.membership.component;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

import br.com.rinos.app.backend.module.membership.service.MembershipAdministrativeContinuityPort;
import br.com.rinos.app.backend.module.membership.service.MembershipContinuityDecision;
import br.com.rinos.app.backend.module.membership.service.MembershipContinuityRequest;

@Component
@ConditionalOnMissingBean(MembershipAdministrativeContinuityPort.class)
public class UnavailableMembershipAdministrativeContinuityAdapter
    implements MembershipAdministrativeContinuityPort {
  @Override
  public MembershipContinuityDecision evaluate(MembershipContinuityRequest request) {
    return MembershipContinuityDecision.unavailable();
  }
}
