package br.com.rinos.app.backend.module.access.entity;

import java.time.Instant;

import br.com.rinos.app.backend.module.access.enums.AccessGroupSubjectStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/** Associação temporal de uma identidade global ou membership de tenant a um grupo. */
@Entity
@Table(name = "access_groupSubject")
public class AccessGroupSubjectEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "idAccessGroupSubject", nullable = false)
  private Long id;

  @Column(name = "idAccessGroup", nullable = false, updatable = false)
  private Long groupId;

  @Column(name = "idUser", updatable = false)
  private Long userId;

  @Column(name = "idAccountMembership", updatable = false)
  private Long accountMembershipId;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 24)
  private AccessGroupSubjectStatus status;

  @Column(name = "validFrom")
  private Instant validFrom;

  @Column(name = "validUntil")
  private Instant validUntil;

  @Column(name = "createdByUserId", updatable = false)
  private Long createdByUserId;

  @Column(name = "createdAt", nullable = false, insertable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updatedAt", nullable = false, insertable = false, updatable = false)
  private Instant updatedAt;

  @Version
  @Column(name = "version", nullable = false)
  private long version;

  protected AccessGroupSubjectEntity() {
  }

  public AccessGroupSubjectEntity(Long groupId, Long userId, Long accountMembershipId,
      Instant validFrom, Instant validUntil, Long createdByUserId) {
    this.groupId = groupId;
    this.userId = userId;
    this.accountMembershipId = accountMembershipId;
    this.validFrom = validFrom;
    this.validUntil = validUntil;
    this.createdByUserId = createdByUserId;
    this.status = AccessGroupSubjectStatus.ACTIVE;
  }

  public Long getId() { return id; }
  public Long getGroupId() { return groupId; }
  public Long getUserId() { return userId; }
  public Long getAccountMembershipId() { return accountMembershipId; }
  public AccessGroupSubjectStatus getStatus() { return status; }
  public Instant getValidFrom() { return validFrom; }
  public Instant getValidUntil() { return validUntil; }
  public Long getCreatedByUserId() { return createdByUserId; }
  public Instant getCreatedAt() { return createdAt; }
  public Instant getUpdatedAt() { return updatedAt; }
  public long getVersion() { return version; }

  public void replaceValidity(Instant newValidFrom, Instant newValidUntil) {
    validFrom = newValidFrom;
    validUntil = newValidUntil;
    status = AccessGroupSubjectStatus.ACTIVE;
  }

  public void end() {
    status = AccessGroupSubjectStatus.ENDED;
  }
}
