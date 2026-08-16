package br.com.rinos.app.backend.module.access.entity;

import java.time.Instant;

import br.com.rinos.app.api.module.access.enums.AccessRuleEffect;
import br.com.rinos.app.api.module.access.enums.AccessScope;
import br.com.rinos.app.backend.module.access.enums.AccessRecordStatus;
import br.com.rinos.app.backend.module.access.enums.AccessRuleOriginType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/** Regra corrente única por origem, chave e contexto. */
@Entity
@Table(name = "access_rule")
public class AccessRuleEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "idAccessRule", nullable = false)
  private Long id;

  @Enumerated(EnumType.STRING)
  @Column(name = "scopeType", nullable = false, length = 16, updatable = false)
  private AccessScope scope;

  @Column(name = "idTenant", updatable = false)
  private Long tenantId;

  @Enumerated(EnumType.STRING)
  @Column(name = "originType", nullable = false, length = 24, updatable = false)
  private AccessRuleOriginType originType;

  @Column(name = "idUser", updatable = false)
  private Long userId;

  @Column(name = "idAccountMembership", updatable = false)
  private Long accountMembershipId;

  @Column(name = "idAccessGroup", updatable = false)
  private Long accessGroupId;

  @Column(name = "idAccessKey", nullable = false, updatable = false)
  private Long accessKeyId;

  @Enumerated(EnumType.STRING)
  @Column(name = "effect", nullable = false, length = 16)
  private AccessRuleEffect effect;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 24)
  private AccessRecordStatus status;

  @Column(name = "validFrom")
  private Instant validFrom;

  @Column(name = "validUntil")
  private Instant validUntil;

  @Column(name = "createdByUserId", updatable = false)
  private Long createdByUserId;

  @Column(name = "updatedByUserId")
  private Long updatedByUserId;

  @Column(name = "createdAt", nullable = false, insertable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updatedAt", nullable = false, insertable = false, updatable = false)
  private Instant updatedAt;

  @Version
  @Column(name = "version", nullable = false)
  private long version;

  protected AccessRuleEntity() {
  }

  public AccessRuleEntity(AccessScope scope, Long tenantId, AccessRuleOriginType originType,
      Long userId, Long accountMembershipId, Long accessGroupId, Long accessKeyId,
      AccessRuleEffect effect, Instant validFrom, Instant validUntil, Long actorUserId) {
    this.scope = scope;
    this.tenantId = tenantId;
    this.originType = originType;
    this.userId = userId;
    this.accountMembershipId = accountMembershipId;
    this.accessGroupId = accessGroupId;
    this.accessKeyId = accessKeyId;
    this.effect = effect;
    this.validFrom = validFrom;
    this.validUntil = validUntil;
    this.createdByUserId = actorUserId;
    this.updatedByUserId = actorUserId;
    this.status = AccessRecordStatus.ACTIVE;
  }

  public Long getId() { return id; }
  public AccessScope getScope() { return scope; }
  public Long getTenantId() { return tenantId; }
  public AccessRuleOriginType getOriginType() { return originType; }
  public Long getUserId() { return userId; }
  public Long getAccountMembershipId() { return accountMembershipId; }
  public Long getAccessGroupId() { return accessGroupId; }
  public Long getAccessKeyId() { return accessKeyId; }
  public AccessRuleEffect getEffect() { return effect; }
  public AccessRecordStatus getStatus() { return status; }
  public Instant getValidFrom() { return validFrom; }
  public Instant getValidUntil() { return validUntil; }
  public Long getCreatedByUserId() { return createdByUserId; }
  public Long getUpdatedByUserId() { return updatedByUserId; }
  public Instant getCreatedAt() { return createdAt; }
  public Instant getUpdatedAt() { return updatedAt; }
  public long getVersion() { return version; }

  public void replace(AccessRuleEffect newEffect, Instant newValidFrom, Instant newValidUntil,
      Long actorUserId) {
    effect = newEffect;
    validFrom = newValidFrom;
    validUntil = newValidUntil;
    updatedByUserId = actorUserId;
    status = AccessRecordStatus.ACTIVE;
  }

  public void deactivate(Long actorUserId) {
    status = AccessRecordStatus.INACTIVE;
    updatedByUserId = actorUserId;
  }
}
