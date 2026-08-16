package br.com.rinos.app.backend.module.access.entity;

import java.time.Instant;

import br.com.rinos.app.api.module.access.enums.AccessScope;
import br.com.rinos.app.backend.module.access.enums.AccessRecordStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Descriptor persistido de uma chave registrada por módulo. */
@Entity
@Table(name = "access_key")
public class AccessKeyEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "idAccessKey", nullable = false)
  private Long id;

  @Column(name = "accessKeyCode", nullable = false, length = 200, updatable = false)
  private String code;

  @Enumerated(EnumType.STRING)
  @Column(name = "scopeType", nullable = false, length = 16, updatable = false)
  private AccessScope scope;

  @Column(name = "idAccessKeyCategory", nullable = false)
  private Long categoryId;

  @Column(name = "ownerModule", nullable = false, length = 100, updatable = false)
  private String ownerModule;

  @Column(name = "nameI18nKey", nullable = false, length = 200)
  private String nameI18nKey;

  @Column(name = "descriptionI18nKey", nullable = false, length = 200)
  private String descriptionI18nKey;

  @Column(name = "entitlementCode", length = 200)
  private String entitlementCode;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 24)
  private AccessRecordStatus status;

  @Column(name = "descriptorVersion", nullable = false)
  private int descriptorVersion;

  @Column(name = "createdAt", nullable = false, insertable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updatedAt", nullable = false, insertable = false, updatable = false)
  private Instant updatedAt;

  protected AccessKeyEntity() {
  }

  public AccessKeyEntity(String code, AccessScope scope, Long categoryId, String ownerModule,
      String nameI18nKey, String descriptionI18nKey, String entitlementCode,
      AccessRecordStatus status, int descriptorVersion) {
    this.code = code;
    this.scope = scope;
    this.categoryId = categoryId;
    this.ownerModule = ownerModule;
    this.nameI18nKey = nameI18nKey;
    this.descriptionI18nKey = descriptionI18nKey;
    this.entitlementCode = entitlementCode;
    this.status = status;
    this.descriptorVersion = descriptorVersion;
  }

  public Long getId() { return id; }
  public String getCode() { return code; }
  public AccessScope getScope() { return scope; }
  public Long getCategoryId() { return categoryId; }
  public String getOwnerModule() { return ownerModule; }
  public String getNameI18nKey() { return nameI18nKey; }
  public String getDescriptionI18nKey() { return descriptionI18nKey; }
  public String getEntitlementCode() { return entitlementCode; }
  public AccessRecordStatus getStatus() { return status; }
  public int getDescriptorVersion() { return descriptorVersion; }
  public Instant getCreatedAt() { return createdAt; }
  public Instant getUpdatedAt() { return updatedAt; }

  public void synchronize(Long newCategoryId, String newNameI18nKey,
      String newDescriptionI18nKey, String newEntitlementCode,
      AccessRecordStatus newStatus, int newDescriptorVersion) {
    categoryId = newCategoryId;
    nameI18nKey = newNameI18nKey;
    descriptionI18nKey = newDescriptionI18nKey;
    entitlementCode = newEntitlementCode;
    status = newStatus;
    descriptorVersion = newDescriptorVersion;
  }
}
