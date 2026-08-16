package br.com.rinos.app.backend.module.access.entity;

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
import jakarta.persistence.Version;

/** Categoria persistida usada somente para navegação e apresentação do catálogo. */
@Entity
@Table(name = "access_keyCategory")
public class AccessKeyCategoryEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "idAccessKeyCategory", nullable = false)
  private Long id;

  @Column(name = "categoryCode", nullable = false, length = 160, updatable = false)
  private String code;

  @Column(name = "parentIdAccessKeyCategory")
  private Long parentId;

  @Enumerated(EnumType.STRING)
  @Column(name = "scopeType", nullable = false, length = 16)
  private AccessScope scope;

  @Column(name = "nameI18nKey", nullable = false, length = 200)
  private String nameI18nKey;

  @Column(name = "descriptionI18nKey", nullable = false, length = 200)
  private String descriptionI18nKey;

  @Column(name = "displayOrder", nullable = false)
  private int displayOrder;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 24)
  private AccessRecordStatus status;

  @Version
  @Column(name = "version", nullable = false)
  private long version;

  protected AccessKeyCategoryEntity() {
  }

  public AccessKeyCategoryEntity(String code, Long parentId, AccessScope scope,
      String nameI18nKey, String descriptionI18nKey, int displayOrder,
      AccessRecordStatus status) {
    this.code = code;
    this.parentId = parentId;
    this.scope = scope;
    this.nameI18nKey = nameI18nKey;
    this.descriptionI18nKey = descriptionI18nKey;
    this.displayOrder = displayOrder;
    this.status = status;
  }

  public Long getId() { return id; }
  public String getCode() { return code; }
  public Long getParentId() { return parentId; }
  public AccessScope getScope() { return scope; }
  public String getNameI18nKey() { return nameI18nKey; }
  public String getDescriptionI18nKey() { return descriptionI18nKey; }
  public int getDisplayOrder() { return displayOrder; }
  public AccessRecordStatus getStatus() { return status; }
  public long getVersion() { return version; }

  public void synchronize(Long newParentId, String newNameI18nKey,
      String newDescriptionI18nKey, int newDisplayOrder, AccessRecordStatus newStatus) {
    parentId = newParentId;
    nameI18nKey = newNameI18nKey;
    descriptionI18nKey = newDescriptionI18nKey;
    displayOrder = newDisplayOrder;
    status = newStatus;
  }
}
