package br.com.rinos.app.api.module.account.port;

import br.com.rinos.app.api.module.account.vo.AccountOperationalSnapshot;

public interface AccountOperationalStatePort {
  AccountOperationalSnapshot inspect(long tenantId);
}
