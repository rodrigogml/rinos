package br.com.rinos.app.backend.module.access.entity;

import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/** Inclusão explícita de uma chave em uma baseline, sem curingas. */
@Entity
@Table(name = "access_protectedGroupBaselineKey")
public class ProtectedGroupBaselineKeyEntity {

  @EmbeddedId
  private ProtectedGroupBaselineKeyId id;

  protected ProtectedGroupBaselineKeyEntity() {
  }

  public ProtectedGroupBaselineKeyEntity(Long baselineId, Long accessKeyId) {
    this.id = new ProtectedGroupBaselineKeyId(baselineId, accessKeyId);
  }

  public ProtectedGroupBaselineKeyId getId() { return id; }
}
