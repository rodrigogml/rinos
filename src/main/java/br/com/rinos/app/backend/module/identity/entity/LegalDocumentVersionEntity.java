package br.com.rinos.app.backend.module.identity.entity;

import java.time.Instant;
import java.util.Arrays;
import java.util.Objects;

import br.com.rinos.app.backend.module.identity.enums.LegalDocumentTypeEnum;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * Representa uma versão imutável do conteúdo apresentado para decisão legal.
 *
 * <p>Conteúdo, hash, finalidade, obrigatoriedade e nome da versão não possuem mutação. O fim de
 * vigência pode ser definido pelo futuro fluxo administrativo sem alterar a evidência aceita.
 *
 * @author Rodrigo Leitão
 * @since 2026-07-29
 */
@Entity
@Table(
    name = "identity_legalDocumentVersion",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_identity_legal_document_version",
        columnNames = {"documentType", "versionName"}),
    indexes = @Index(
        name = "idx_identity_legal_document_effective",
        columnList = "documentType, effectiveAt, retiredAt"))
public class LegalDocumentVersionEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "id", nullable = false)
  private Long id;

  @Enumerated(EnumType.STRING)
  @Column(name = "documentType", nullable = false, length = 32)
  private LegalDocumentTypeEnum documentType;

  @Column(name = "versionName", nullable = false, length = 64)
  private String versionName;

  @Column(name = "required", nullable = false)
  private boolean required;

  @Lob
  @Column(name = "content", nullable = false, columnDefinition = "LONGTEXT")
  private String content;

  @Column(name = "contentHash", nullable = false, length = 32, columnDefinition = "BINARY(32)")
  private byte[] contentHash;

  @Column(name = "effectiveAt", nullable = false)
  private Instant effectiveAt;

  @Column(name = "retiredAt")
  private Instant retiredAt;

  @Column(name = "createdAt", nullable = false, insertable = false, updatable = false)
  private Instant createdAt;

  /**
   * Construtor reservado ao provedor JPA.
   */
  protected LegalDocumentVersionEntity() {
  }

  /**
   * Cria uma versão ainda não persistida com seu conteúdo e hash já validados.
   *
   * @param documentType finalidade legal
   * @param versionName nome estável e legível
   * @param required obrigatoriedade no cadastro
   * @param content conteúdo integral apresentado
   * @param contentHash SHA-256 do conteúdo
   * @param effectiveAt início UTC da vigência
   * @param retiredAt fim UTC opcional da vigência
   */
  public LegalDocumentVersionEntity(
      LegalDocumentTypeEnum documentType,
      String versionName,
      boolean required,
      String content,
      byte[] contentHash,
      Instant effectiveAt,
      Instant retiredAt) {
    this.documentType =
        Objects.requireNonNull(documentType, "documentType must not be null");
    this.versionName = Objects.requireNonNull(versionName, "versionName must not be null");
    this.required = required;
    this.content = Objects.requireNonNull(content, "content must not be null");
    this.contentHash = Arrays.copyOf(
        Objects.requireNonNull(contentHash, "contentHash must not be null"),
        contentHash.length);
    this.effectiveAt = Objects.requireNonNull(effectiveAt, "effectiveAt must not be null");
    this.retiredAt = retiredAt;
  }

  /**
   * Retorna o identificador interno da versão.
   *
   * @return identidade gerada pelo banco ou {@code null}
   */
  public Long getId() {
    return id;
  }

  /**
   * Retorna a finalidade legal.
   *
   * @return tipo do documento
   */
  public LegalDocumentTypeEnum getDocumentType() {
    return documentType;
  }

  /**
   * Retorna o nome estável da versão.
   *
   * @return nome legível
   */
  public String getVersionName() {
    return versionName;
  }

  /**
   * Indica se o aceite é obrigatório.
   *
   * @return {@code true} para documento obrigatório
   */
  public boolean isRequired() {
    return required;
  }

  /**
   * Retorna o conteúdo integral apresentado.
   *
   * @return texto imutável do documento
   */
  public String getContent() {
    return content;
  }

  /**
   * Retorna cópia do hash de integridade.
   *
   * @return SHA-256 com 32 bytes
   */
  public byte[] getContentHash() {
    return Arrays.copyOf(contentHash, contentHash.length);
  }

  /**
   * Retorna o início da vigência.
   *
   * @return instante UTC inclusivo
   */
  public Instant getEffectiveAt() {
    return effectiveAt;
  }

  /**
   * Retorna o fim da vigência.
   *
   * @return instante UTC exclusivo ou {@code null}
   */
  public Instant getRetiredAt() {
    return retiredAt;
  }

  /**
   * Define o fim da vigência previamente validado pelo fluxo administrativo.
   *
   * @param retiredAt instante UTC exclusivo
   */
  public void setRetiredAt(Instant retiredAt) {
    this.retiredAt = Objects.requireNonNull(retiredAt, "retiredAt must not be null");
  }

  /**
   * Retorna o instante de criação.
   *
   * @return instante UTC produzido pelo MySQL
   */
  public Instant getCreatedAt() {
    return createdAt;
  }
}
