package br.com.rinos.app.backend.module.membership.component;
import br.com.rinos.app.backend.module.membership.service.*;
public class UnavailableMembershipPlanCapacityAdapter implements MembershipPlanCapacityPort{
 public MembershipPlanCapacityDecision evaluate(long accountId,long userId){return MembershipPlanCapacityDecision.unavailable();}
}
