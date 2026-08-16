package br.com.rinos.app.api.module.plans.vo;

import br.com.rinos.app.api.module.plans.enums.ContractScope;

/** Identidade global titular de contrato pessoal. */
public record PersonalEntitlementSubject(long userId) implements EntitlementSubject {

  public PersonalEntitlementSubject {
    if (userId <= 0) {
      throw new IllegalArgumentException("userId must be positive");
    }
  }

  @Override
  public ContractScope scope() {
    return ContractScope.PERSONAL;
  }

  @Override
  public String toString() {
    return "PersonalEntitlementSubject[userId=REDACTED]";
  }
}
