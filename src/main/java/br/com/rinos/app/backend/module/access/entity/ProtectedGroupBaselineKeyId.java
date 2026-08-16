package br.com.rinos.app.backend.module.access.entity;

import java.io.Serializable;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/** Identidade composta de uma chave incluída em uma baseline protegida. */
@Embeddable
public class ProtectedGroupBaselineKeyId implements Serializable {

  @Column(name = "idProtectedGroupBaseline", nullable = false)
  private Long baselineId;

  @Column(name = "idAccessKey", nullable = false)
  private Long accessKeyId;

  protected ProtectedGroupBaselineKeyId() {
  }

  public ProtectedGroupBaselineKeyId(Long baselineId, Long accessKeyId) {
    this.baselineId = baselineId;
    this.accessKeyId = accessKeyId;
  }

  public Long getBaselineId() { return baselineId; }
  public Long getAccessKeyId() { return accessKeyId; }

  @Override
  public boolean equals(Object other) {
    return this == other || other instanceof ProtectedGroupBaselineKeyId that
        && Objects.equals(baselineId, that.baselineId)
        && Objects.equals(accessKeyId, that.accessKeyId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(baselineId, accessKeyId);
  }
}
