package br.com.rinos.app.backend.module.access.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Rastreabilidade exata entre uma chave e um requisito consumidor. */
@Entity
@Table(name = "access_keyRequirement")
public class AccessKeyRequirementEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "idAccessKeyRequirement", nullable = false)
  private Long id;

  @Column(name = "idAccessKey", nullable = false, updatable = false)
  private Long accessKeyId;

  @Column(name = "featureCode", nullable = false, length = 100, updatable = false)
  private String featureCode;

  @Column(name = "requirementCode", nullable = false, length = 100, updatable = false)
  private String requirementCode;

  protected AccessKeyRequirementEntity() {
  }

  public AccessKeyRequirementEntity(Long accessKeyId, String featureCode, String requirementCode) {
    this.accessKeyId = accessKeyId;
    this.featureCode = featureCode;
    this.requirementCode = requirementCode;
  }

  public Long getId() { return id; }
  public Long getAccessKeyId() { return accessKeyId; }
  public String getFeatureCode() { return featureCode; }
  public String getRequirementCode() { return requirementCode; }
}
