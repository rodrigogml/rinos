package br.com.rinos.app.api.module.account.vo;

import br.com.rinos.app.api.module.account.enums.AccountStatus;

public record AccountOperationalSnapshot(
    boolean sourceAvailable,
    boolean exists,
    Long accountId,
    Long tenantId,
    AccountStatus accountStatus,
    boolean tenantOperational) {

  public static AccountOperationalSnapshot unavailable() {
    return new AccountOperationalSnapshot(false, false, null, null, null, false);
  }
}
