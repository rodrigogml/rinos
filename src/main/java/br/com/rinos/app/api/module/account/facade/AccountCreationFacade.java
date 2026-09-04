package br.com.rinos.app.api.module.account.facade;

import java.util.UUID;

import br.com.rinos.app.api.module.account.dto.AccountCreationRequest;
import br.com.rinos.app.api.module.account.vo.AccountCreationResult;
import br.com.rinos.app.api.module.account.vo.AccountCreationStatus;

public interface AccountCreationFacade {
  AccountCreationResult request(AccountCreationRequest request);
  AccountCreationStatus status(UUID protocolId);
}
