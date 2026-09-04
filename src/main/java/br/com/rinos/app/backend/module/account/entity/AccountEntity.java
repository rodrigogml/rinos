package br.com.rinos.app.backend.module.account.entity;

import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import br.com.rinos.app.api.module.account.enums.AccountStatus;
import jakarta.persistence.*;

@Entity @Table(name = "account_account")
public class AccountEntity {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY) @Column(name = "idAccount") private Long id;
  @JdbcTypeCode(SqlTypes.BINARY) @Column(name = "publicId", columnDefinition = "BINARY(16)", nullable = false, updatable = false) private UUID publicId;
  @Column(name = "idTenant", nullable = false, updatable = false) private Long tenantId;
  @Column(name = "founderUserId", nullable = false, updatable = false) private Long founderUserId;
  @Column(name = "displayName", nullable = false, length = 160) private String displayName;
  @Column(name = "baseCurrency", nullable = false, length = 3) private String baseCurrency;
  @Column(name = "timeZoneId", nullable = false, length = 100) private String timeZoneId;
  @Enumerated(EnumType.STRING) @Column(name = "status", nullable = false, length = 24) private AccountStatus status;
  @Version @Column(name = "version", nullable = false) private long version;
  @Column(name = "createdAt", insertable = false, updatable = false) private Instant createdAt;
  @Column(name = "updatedAt", insertable = false, updatable = false) private Instant updatedAt;
  protected AccountEntity() {}
  public AccountEntity(UUID publicId, Long tenantId, Long founderUserId, String displayName, String baseCurrency, String timeZoneId) {
    this.publicId=publicId; this.tenantId=tenantId; this.founderUserId=founderUserId; this.displayName=displayName;
    this.baseCurrency=baseCurrency; this.timeZoneId=timeZoneId; this.status=AccountStatus.CREATING;
  }
  /**
   * Torna a conta operacional depois que a saga confirmou todos os seus pré-requisitos.
   *
   * @throws IllegalStateException quando a conta não está no estado {@code CREATING}
   */
  public void activate() {
    if (status != AccountStatus.CREATING) {
      throw new IllegalStateException("account is not creating");
    }
    status = AccountStatus.ACTIVE;
  }
  public Long getId(){return id;} public UUID getPublicId(){return publicId;} public Long getTenantId(){return tenantId;}
  public Long getFounderUserId(){return founderUserId;} public String getDisplayName(){return displayName;}
  public String getBaseCurrency(){return baseCurrency;} public String getTimeZoneId(){return timeZoneId;}
  public AccountStatus getStatus(){return status;} public long getVersion(){return version;}
  public Instant getCreatedAt(){return createdAt;} public Instant getUpdatedAt(){return updatedAt;}
}
