package br.com.rinos.app.backend.module.account.service;
import java.util.UUID; import org.springframework.stereotype.Service; import org.springframework.transaction.annotation.Transactional;
import br.com.rinos.app.api.module.account.vo.AccountCreationStatus; import br.com.rinos.app.backend.module.account.repository.*;
@Service
@org.springframework.context.annotation.Lazy
public class AccountCreationStatusService {
 private final AccountCreationIntentRepository intents; private final AccountRepository accounts;
 public AccountCreationStatusService(AccountCreationIntentRepository intents,AccountRepository accounts){this.intents=intents;this.accounts=accounts;}
 @Transactional(readOnly=true)
 public AccountCreationStatus find(long requesterUserId,UUID protocolId){
  if(requesterUserId<=0||protocolId==null)throw new IllegalArgumentException("account status context is invalid");
  var intent=intents.findByProtocolId(protocolId).filter(i->i.getCreatorUserId()==requesterUserId)
    .orElseThrow(()->new IllegalArgumentException("account creation status is unavailable"));
  var account=accounts.findById(intent.getAccountId()).orElseThrow();
  return new AccountCreationStatus(intent.getProtocolId(),account.getPublicId(),account.getDisplayName(),intent.getPublicStage(),
      intent.getFailureCode(),intent.getCreatedAt(),intent.getUpdatedAt());
 }
}
