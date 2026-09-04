package br.com.rinos.app.api.module.account.vo;

import java.util.Objects;

import br.com.rinos.app.api.module.account.enums.AccountBootstrapResultStatus;

/** Resposta segura de uma etapa; detalhes internos não atravessam a fronteira. */
public record AccountBootstrapResult(
    AccountBootstrapResultStatus status,
    String externalReference,
    String safeReasonCode) {

  public AccountBootstrapResult {
    Objects.requireNonNull(status, "status must not be null");
  }

  public static AccountBootstrapResult unavailable() {
    return new AccountBootstrapResult(
        AccountBootstrapResultStatus.UNAVAILABLE, null, "ACCOUNT_DEPENDENCY_UNAVAILABLE");
  }
}
