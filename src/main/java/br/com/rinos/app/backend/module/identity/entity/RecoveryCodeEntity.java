package br.com.rinos.app.backend.module.identity.entity;

import java.time.Instant;
import java.util.Objects;

import br.com.rinos.app.backend.module.identity.enums.RecoveryCodeStatusEnum;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * Hash independente e estado de um código de recuperação.
 *
 * @author Rodrigo Leitão
 * @since 2026-08-08
 */
@Entity
@Table(name = "identity_recoveryCode", uniqueConstraints = @UniqueConstraint(
    name = "uk_identity_recovery_code_ordinal", columnNames = {"idRecoveryCodeSet", "ordinal"}))
public class RecoveryCodeEntity {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY) @Column(name = "id", nullable = false) private Long id;
  @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "idRecoveryCodeSet", nullable = false) private RecoveryCodeSetEntity codeSet;
  @Column(name = "codeHash", nullable = false, length = 255) private String codeHash;
  @Column(name = "ordinal", nullable = false) private short ordinal;
  @Enumerated(EnumType.STRING) @Column(name = "status", nullable = false, length = 24) private RecoveryCodeStatusEnum status;
  @Column(name = "usedAt") private Instant usedAt;
  @Column(name = "createdAt", nullable = false, insertable = false, updatable = false) private Instant createdAt;

  protected RecoveryCodeEntity() { }
  public RecoveryCodeEntity(RecoveryCodeSetEntity codeSet, String codeHash, int ordinal) {
    this.codeSet = Objects.requireNonNull(codeSet, "codeSet must not be null");
    if (codeHash == null || codeHash.isBlank() || codeHash.length() > 255) throw new IllegalArgumentException("codeHash is invalid");
    if (ordinal < 1 || ordinal > 10) throw new IllegalArgumentException("ordinal must be between 1 and 10");
    this.codeHash = codeHash;
    this.ordinal = (short) ordinal;
    status = RecoveryCodeStatusEnum.AVAILABLE;
  }
  public Long getId() { return id; }
  public RecoveryCodeSetEntity getCodeSet() { return codeSet; }
  public String getCodeHash() { return codeHash; }
  public short getOrdinal() { return ordinal; }
  public RecoveryCodeStatusEnum getStatus() { return status; }
  public Instant getUsedAt() { return usedAt; }
  public Instant getCreatedAt() { return createdAt; }
  public void use(Instant occurredAt) {
    if (status != RecoveryCodeStatusEnum.AVAILABLE) throw new IllegalStateException("recovery code is unavailable");
    status = RecoveryCodeStatusEnum.USED;
    usedAt = Objects.requireNonNull(occurredAt, "occurredAt must not be null");
  }
  public void invalidate() {
    if (status == RecoveryCodeStatusEnum.AVAILABLE) status = RecoveryCodeStatusEnum.INVALIDATED;
  }
}
