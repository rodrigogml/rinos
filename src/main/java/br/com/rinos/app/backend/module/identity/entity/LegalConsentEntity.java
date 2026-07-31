package br.com.rinos.app.backend.module.identity.entity;

import java.time.Instant;
import java.util.Objects;

import br.com.rinos.app.backend.module.identity.enums.LegalConsentDecisionEnum;
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
 * Evidência imutável da decisão de um usuário sobre uma versão legal específica.
 *
 * @author Rodrigo Leitão
 * @since 2026-07-29
 */
@Entity
@Table(
    name = "identity_legalConsent",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_identity_legal_consent_user_version",
        columnNames = {"idUser", "idLegalDocumentVersion"}))
public class LegalConsentEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "id", nullable = false)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "idUser", nullable = false)
  private UserEntity user;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "idRegistration")
  private RegistrationEntity registration;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "idLegalDocumentVersion", nullable = false)
  private LegalDocumentVersionEntity legalDocumentVersion;

  @Enumerated(EnumType.STRING)
  @Column(name = "decision", nullable = false, length = 16)
  private LegalConsentDecisionEnum decision;

  @Column(name = "decidedAt", nullable = false)
  private Instant decidedAt;

  @Column(name = "createdAt", nullable = false, insertable = false, updatable = false)
  private Instant createdAt;

  /**
   * Construtor reservado ao provedor JPA.
   */
  protected LegalConsentEntity() {
  }

  /**
   * Cria uma evidência imutável para a decisão validada pelo serviço.
   *
   * @param user identidade responsável
   * @param registration cadastro de origem opcional
   * @param legalDocumentVersion versão decidida
   * @param decision aceite ou recusa explícita
   * @param decidedAt instante UTC da decisão
   */
  public LegalConsentEntity(
      UserEntity user,
      RegistrationEntity registration,
      LegalDocumentVersionEntity legalDocumentVersion,
      LegalConsentDecisionEnum decision,
      Instant decidedAt) {
    this.user = Objects.requireNonNull(user, "user must not be null");
    this.registration = registration;
    this.legalDocumentVersion = Objects.requireNonNull(
        legalDocumentVersion,
        "legalDocumentVersion must not be null");
    this.decision = Objects.requireNonNull(decision, "decision must not be null");
    this.decidedAt = Objects.requireNonNull(decidedAt, "decidedAt must not be null");
  }

  /**
   * Retorna o identificador interno.
   *
   * @return identidade gerada pelo banco ou {@code null}
   */
  public Long getId() {
    return id;
  }

  /**
   * Retorna o usuário responsável.
   *
   * @return identidade global
   */
  public UserEntity getUser() {
    return user;
  }

  /**
   * Retorna o cadastro que originou a decisão.
   *
   * @return cadastro ou {@code null} depois da minimização
   */
  public RegistrationEntity getRegistration() {
    return registration;
  }

  /**
   * Retorna a versão legal decidida.
   *
   * @return versão imutável
   */
  public LegalDocumentVersionEntity getLegalDocumentVersion() {
    return legalDocumentVersion;
  }

  /**
   * Retorna a decisão explícita.
   *
   * @return aceite ou recusa
   */
  public LegalConsentDecisionEnum getDecision() {
    return decision;
  }

  /**
   * Retorna o instante da decisão.
   *
   * @return instante UTC
   */
  public Instant getDecidedAt() {
    return decidedAt;
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
