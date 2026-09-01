package br.com.rinos.app.backend.module.account.entity;

import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import br.com.rinos.app.api.module.account.enums.AccountPublicStage;
import br.com.rinos.app.backend.module.account.enums.AccountCreationIntentStatus;
import jakarta.persistence.*;

@Entity @Table(name = "account_creationIntent")
public class AccountCreationIntentEntity {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY) @Column(name = "idAccountCreationIntent") private Long id;
  @JdbcTypeCode(SqlTypes.BINARY) @Column(name="publicId",columnDefinition="BINARY(16)",nullable=false,updatable=false) private UUID publicId;
  @JdbcTypeCode(SqlTypes.BINARY) @Column(name="protocolId",columnDefinition="BINARY(16)",nullable=false,updatable=false) private UUID protocolId;
  @Column(name="creatorUserId",nullable=false,updatable=false) private Long creatorUserId;
  @JdbcTypeCode(SqlTypes.BINARY) @Column(name="idempotencyKey",columnDefinition="BINARY(16)",nullable=false,updatable=false) private UUID idempotencyKey;
  @Column(name="payloadHash",columnDefinition="BINARY(32)",nullable=false,updatable=false) private byte[] payloadHash;
  @Column(name="idAccount",nullable=false,updatable=false) private Long accountId;
  @Enumerated(EnumType.STRING) @Column(name="status",nullable=false,length=32) private AccountCreationIntentStatus status;
  @Enumerated(EnumType.STRING) @Column(name="publicStage",nullable=false,length=32) private AccountPublicStage publicStage;
  @Column(name="failureCode",length=100) private String failureCode;
  @Version @Column(name="version",nullable=false) private long version;
  @Column(name="createdAt",insertable=false,updatable=false) private Instant createdAt;
  @Column(name="updatedAt",insertable=false,updatable=false) private Instant updatedAt;
  protected AccountCreationIntentEntity() {}
  public AccountCreationIntentEntity(UUID publicId,UUID protocolId,Long creatorUserId,UUID idempotencyKey,byte[] payloadHash,Long accountId){
    this.publicId=publicId;this.protocolId=protocolId;this.creatorUserId=creatorUserId;this.idempotencyKey=idempotencyKey;
    this.payloadHash=payloadHash.clone();this.accountId=accountId;this.status=AccountCreationIntentStatus.ACCEPTED;this.publicStage=AccountPublicStage.ACCEPTED;
  }
  /**
   * Expõe o protocolo como disponível somente após a ativação atômica da conta e do tenant.
   *
   * @throws IllegalStateException quando a intenção já terminou em um estado incompatível
   */
  public void markReady() {
    if (status != AccountCreationIntentStatus.ACCEPTED
        && status != AccountCreationIntentStatus.PROCESSING) {
      throw new IllegalStateException("account creation intent is not activatable");
    }
    status = AccountCreationIntentStatus.READY;
    publicStage = AccountPublicStage.AVAILABLE;
    failureCode = null;
  }
  public Long getId(){return id;} public UUID getPublicId(){return publicId;} public UUID getProtocolId(){return protocolId;}
  public Long getCreatorUserId(){return creatorUserId;} public UUID getIdempotencyKey(){return idempotencyKey;}
  public byte[] getPayloadHash(){return payloadHash.clone();} public Long getAccountId(){return accountId;}
  public AccountCreationIntentStatus getStatus(){return status;} public AccountPublicStage getPublicStage(){return publicStage;}
  public String getFailureCode(){return failureCode;} public long getVersion(){return version;}
  public Instant getCreatedAt(){return createdAt;} public Instant getUpdatedAt(){return updatedAt;}
}
