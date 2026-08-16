package br.com.rinos.app.backend.module.account.entity;

import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import br.com.rinos.app.backend.module.account.enums.TenantStatus;
import jakarta.persistence.*;

@Entity @Table(name = "account_tenant")
public class TenantEntity {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY) @Column(name = "idTenant") private Long id;
  @JdbcTypeCode(SqlTypes.BINARY) @Column(name = "publicId", columnDefinition = "BINARY(16)", nullable = false, updatable = false) private UUID publicId;
  @Enumerated(EnumType.STRING) @Column(name = "status", nullable = false, length = 24) private TenantStatus status;
  @Version @Column(name = "version", nullable = false) private long version;
  @Column(name = "createdAt", insertable = false, updatable = false) private Instant createdAt;
  @Column(name = "updatedAt", insertable = false, updatable = false) private Instant updatedAt;
  protected TenantEntity() {}
  public TenantEntity(UUID publicId) { this.publicId = publicId; this.status = TenantStatus.RESERVED; }
  public Long getId(){return id;} public UUID getPublicId(){return publicId;} public TenantStatus getStatus(){return status;}
  public long getVersion(){return version;} public Instant getCreatedAt(){return createdAt;} public Instant getUpdatedAt(){return updatedAt;}
}
