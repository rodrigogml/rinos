package br.com.rinos.app.backend.module.membership.service;

public interface MembershipContextInvalidationPort {
  void lock(long tenantId);
  long revise(long tenantId);
}
