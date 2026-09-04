package br.com.rinos.app.backend.module.membership.service;
public record MembershipPlanCapacityDecision(boolean sourceAvailable,boolean allowed){
 public static MembershipPlanCapacityDecision unavailable(){return new MembershipPlanCapacityDecision(false,false);}
 public static MembershipPlanCapacityDecision permit(){return new MembershipPlanCapacityDecision(true,true);}
}
