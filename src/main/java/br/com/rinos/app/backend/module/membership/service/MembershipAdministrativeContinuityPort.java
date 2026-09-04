package br.com.rinos.app.backend.module.membership.service;

public interface MembershipAdministrativeContinuityPort {
  MembershipContinuityDecision evaluate(MembershipContinuityRequest request);
}
