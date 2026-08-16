package br.com.rinos.app.backend.module.membership.entity;

import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import br.com.rinos.app.api.module.membership.enums.*;
import jakarta.persistence.*;

@Entity
@Table(name = "membership_accountMembership")
public class AccountMembershipEntity {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "idAccountMembership") private Long id;
  @JdbcTypeCode(SqlTypes.BINARY) @Column(name = "publicId", columnDefinition = "BINARY(16)", nullable = false, updatable = false)
  private UUID publicId;
  @Column(name = "idAccount", nullable = false, updatable = false) private Long accountId;
  @Column(name = "idUser", nullable = false, updatable = false) private Long userId;
  @Enumerated(EnumType.STRING) @Column(name = "roleType", nullable = false) private MembershipRoleType roleType;
  @Enumerated(EnumType.STRING) @Column(name = "originType", nullable = false) private MembershipOriginType originType;
  @Enumerated(EnumType.STRING) @Column(name = "status", nullable = false) private MembershipStatus status;
  @Column(name = "currentMarker") private Integer currentMarker;
  @Column(name = "startedAt", nullable = false, updatable = false) private Instant startedAt;
  @Column(name = "endedAt") private Instant endedAt;
  @Version @Column(name = "version", nullable = false) private long version;
  protected AccountMembershipEntity() {}
  public AccountMembershipEntity(UUID publicId, Long accountId, Long userId,
      MembershipRoleType roleType, MembershipOriginType originType, Instant startedAt) {
    this.publicId=publicId; this.accountId=accountId; this.userId=userId; this.roleType=roleType;
    this.originType=originType; this.startedAt=startedAt; this.status=MembershipStatus.ACTIVE;
    this.currentMarker=1;
  }
  public Long getId(){return id;} public UUID getPublicId(){return publicId;}
  public Long getAccountId(){return accountId;} public Long getUserId(){return userId;}
  public MembershipRoleType getRoleType(){return roleType;} public MembershipOriginType getOriginType(){return originType;}
  public MembershipStatus getStatus(){return status;} public long getVersion(){return version;}

  public void changeRole(MembershipRoleType newRole) {
    if (status != MembershipStatus.ACTIVE || newRole == null) {
      throw new IllegalStateException("membership role cannot be changed");
    }
    roleType = newRole;
  }

  public void suspend() {
    if (status != MembershipStatus.ACTIVE) throw new IllegalStateException("membership cannot be suspended");
    status = MembershipStatus.SUSPENDED;
  }

  public void reactivate() {
    if (status != MembershipStatus.SUSPENDED) throw new IllegalStateException("membership cannot be reactivated");
    status = MembershipStatus.ACTIVE;
  }

  public void remove(Instant at) {
    end(MembershipStatus.REMOVED, at);
  }

  public void leave(Instant at) {
    if (status != MembershipStatus.ACTIVE) throw new IllegalStateException("membership cannot leave");
    end(MembershipStatus.LEFT, at);
  }

  private void end(MembershipStatus terminalStatus, Instant at) {
    if ((status != MembershipStatus.ACTIVE && status != MembershipStatus.SUSPENDED) || at == null) {
      throw new IllegalStateException("membership cannot be ended");
    }
    status = terminalStatus;
    currentMarker = null;
    endedAt = at;
  }
}
