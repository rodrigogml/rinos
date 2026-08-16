package br.com.rinos.app.backend.module.membership.service;

public record MembershipContinuityDecision(boolean sourceAvailable, boolean allowed) {
  public static MembershipContinuityDecision permit() { return new MembershipContinuityDecision(true, true); }
  public static MembershipContinuityDecision deny() { return new MembershipContinuityDecision(true, false); }
  public static MembershipContinuityDecision unavailable() { return new MembershipContinuityDecision(false, false); }
}
