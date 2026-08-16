package br.com.rinos.app.backend.module.account.entity;
import java.time.Instant;
import br.com.rinos.app.backend.module.account.enums.*;
import jakarta.persistence.*;
@Entity @Table(name="account_provisioningCheckpoint")
public class AccountProvisioningCheckpointEntity {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) @Column(name="idAccountProvisioningCheckpoint") private Long id;
 @Column(name="idAccount",nullable=false,updatable=false) private Long accountId;
 @Enumerated(EnumType.STRING) @Column(name="stepType",nullable=false,updatable=false) private ProvisioningStepType stepType;
 @Enumerated(EnumType.STRING) @Column(name="status",nullable=false) private ProvisioningCheckpointStatus status;
 @Column(name="attemptCount",nullable=false) private int attemptCount;
 @Version @Column(name="version",nullable=false) private long version;
 @Column(name="createdAt",insertable=false,updatable=false) private Instant createdAt;
 @Column(name="updatedAt",insertable=false,updatable=false) private Instant updatedAt;
 protected AccountProvisioningCheckpointEntity(){}
 public AccountProvisioningCheckpointEntity(Long accountId,ProvisioningStepType stepType){this.accountId=accountId;this.stepType=stepType;this.status=ProvisioningCheckpointStatus.PENDING;}
 public Long getId(){return id;} public Long getAccountId(){return accountId;} public ProvisioningStepType getStepType(){return stepType;} public ProvisioningCheckpointStatus getStatus(){return status;}
}
