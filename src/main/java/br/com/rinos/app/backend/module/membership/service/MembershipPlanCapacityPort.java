package br.com.rinos.app.backend.module.membership.service;
public interface MembershipPlanCapacityPort { MembershipPlanCapacityDecision evaluate(long accountId,long prospectiveUserId); }
