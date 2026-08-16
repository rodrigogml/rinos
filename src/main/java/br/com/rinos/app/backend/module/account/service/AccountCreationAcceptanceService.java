package br.com.rinos.app.backend.module.account.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Arrays;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import br.com.rinos.app.api.module.account.dto.AccountCreationRequest;
import br.com.rinos.app.api.module.account.enums.AccountCreationResultStatus;
import br.com.rinos.app.api.module.account.vo.AccountCreationResult;
import br.com.rinos.app.backend.module.account.entity.*;
import br.com.rinos.app.backend.module.account.enums.ProvisioningStepType;
import br.com.rinos.app.backend.module.account.repository.*;

/** Aceita a intenção depois que identidade, garantia e antiabuso foram validados. */
@Service
@org.springframework.context.annotation.Lazy
public class AccountCreationAcceptanceService {
  private final TenantRepository tenants; private final AccountRepository accounts;
  private final AccountCreationIntentRepository intents; private final AccountProvisioningCheckpointRepository checkpoints;
  private final AccountOutboxEventRepository outbox; private final AccountAuditEventRepository audits;
  private final TransactionTemplate transactions;
  public AccountCreationAcceptanceService(TenantRepository tenants,AccountRepository accounts,AccountCreationIntentRepository intents,
      AccountProvisioningCheckpointRepository checkpoints,AccountOutboxEventRepository outbox,AccountAuditEventRepository audits,
      PlatformTransactionManager transactionManager){
    this.tenants=tenants;this.accounts=accounts;this.intents=intents;this.checkpoints=checkpoints;this.outbox=outbox;this.audits=audits;
    this.transactions=new TransactionTemplate(transactionManager);
  }

  public AccountCreationResult accept(long creatorUserId,AccountCreationRequest request,String correlationId,Instant occurredAt){
    if(creatorUserId<=0||correlationId==null||correlationId.isBlank()||occurredAt==null) throw new IllegalArgumentException("trusted account creation context is incomplete");
    byte[] hash=hash(request);
    AccountCreationResult existing=replay(creatorUserId,request.idempotencyKey(),hash);
    if(existing!=null)return existing;
    try{
      return transactions.execute(status->create(creatorUserId,request,hash,correlationId,occurredAt));
    }catch(DataIntegrityViolationException collision){
      AccountCreationResult winner=replay(creatorUserId,request.idempotencyKey(),hash);
      if(winner!=null)return winner;
      throw collision;
    }
  }

  private AccountCreationResult create(long creatorUserId,AccountCreationRequest request,byte[] hash,String correlationId,Instant occurredAt){
    AccountCreationResult existing=replay(creatorUserId,request.idempotencyKey(),hash);
    if(existing!=null)return existing;
    TenantEntity tenant=tenants.saveAndFlush(new TenantEntity(UUID.randomUUID()));
    AccountEntity account=accounts.saveAndFlush(new AccountEntity(UUID.randomUUID(),tenant.getId(),creatorUserId,
        request.displayName(),request.baseCurrency(),request.timeZoneId()));
    for(ProvisioningStepType step:ProvisioningStepType.values()) checkpoints.save(new AccountProvisioningCheckpointEntity(account.getId(),step));
    UUID protocol=UUID.randomUUID();
    AccountCreationIntentEntity intent=intents.saveAndFlush(new AccountCreationIntentEntity(UUID.randomUUID(),protocol,creatorUserId,request.idempotencyKey(),hash,account.getId()));
    audits.save(new AccountAuditEventEntity("ACCOUNT_CREATION_ACCEPTED",account.getId(),tenant.getId(),creatorUserId,correlationId,"ACCEPTED",occurredAt));
    String payload="{\"accountId\":"+account.getId()+",\"tenantId\":"+tenant.getId()+",\"protocolId\":\""+protocol+"\"}";
    outbox.saveAndFlush(new AccountOutboxEventEntity(UUID.randomUUID(),account.getId(),"ACCOUNT_PROVISIONING_REQUESTED",payload));
    return new AccountCreationResult(AccountCreationResultStatus.ACCEPTED,protocol,account.getPublicId(),intent.getPublicStage(),null,null);
  }

  private AccountCreationResult replay(long creatorUserId,UUID key,byte[] hash){
    return intents.findByCreatorUserIdAndIdempotencyKey(creatorUserId,key).map(intent->{
      if(!Arrays.equals(hash,intent.getPayloadHash()))return new AccountCreationResult(AccountCreationResultStatus.CONFLICT,null,null,null,"ACCOUNT_IDEMPOTENCY_CONFLICT",null);
      var account=accounts.findById(intent.getAccountId()).orElseThrow();
      return new AccountCreationResult(AccountCreationResultStatus.REPLAYED,intent.getProtocolId(),account.getPublicId(),intent.getPublicStage(),null,null);
    }).orElse(null);
  }

  private static byte[] hash(AccountCreationRequest request){
    try{
      String value=request.displayName()+"\u001f"+request.baseCurrency()+"\u001f"+request.timeZoneId();
      return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
    }catch(java.security.NoSuchAlgorithmException exception){throw new IllegalStateException("SHA-256 unavailable",exception);}
  }
}
