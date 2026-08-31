package br.com.rinos.app.backend.module.account.entity;

import java.time.Instant;
import java.util.Objects;

import br.com.rinos.app.backend.module.account.enums.ProvisioningCheckpointStatus;
import br.com.rinos.app.backend.module.account.enums.ProvisioningStepType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * Guarda o resultado durável de uma etapa da saga de criação da conta.
 *
 * <p>O checkpoint não ativa conta nem tenant. Ele apenas conserva a evolução de uma dependência
 * explícita para que a ativação posterior exija todas as quatro confirmações planejadas.
 *
 * @author Rodrigo Leitão
 * @since 2026-08-31
 */
@Entity
@Table(name = "account_provisioningCheckpoint")
public class AccountProvisioningCheckpointEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "idAccountProvisioningCheckpoint")
  private Long id;

  @Column(name = "idAccount", nullable = false, updatable = false)
  private Long accountId;

  @Enumerated(EnumType.STRING)
  @Column(name = "stepType", nullable = false, updatable = false)
  private ProvisioningStepType stepType;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false)
  private ProvisioningCheckpointStatus status;

  @Column(name = "externalReference", length = 200)
  private String externalReference;

  @Column(name = "attemptCount", nullable = false)
  private int attemptCount;

  @Column(name = "nextAttemptAt")
  private Instant nextAttemptAt;

  @Column(name = "failureCode", length = 100)
  private String failureCode;

  @Version
  @Column(name = "version", nullable = false)
  private long version;

  @Column(name = "createdAt", insertable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updatedAt", insertable = false, updatable = false)
  private Instant updatedAt;

  protected AccountProvisioningCheckpointEntity() {
  }

  /**
   * Cria uma etapa pendente para uma conta recém-aceita.
   *
   * @param accountId identificador interno da conta
   * @param stepType etapa exclusiva a ser registrada
   */
  public AccountProvisioningCheckpointEntity(Long accountId, ProvisioningStepType stepType) {
    this.accountId = Objects.requireNonNull(accountId, "accountId must not be null");
    this.stepType = Objects.requireNonNull(stepType, "stepType must not be null");
    status = ProvisioningCheckpointStatus.PENDING;
  }

  /** @return identificador interno persistente */
  public Long getId() {
    return id;
  }

  /** @return identificador interno da conta dona da etapa */
  public Long getAccountId() {
    return accountId;
  }

  /** @return etapa exclusiva desta linha */
  public ProvisioningStepType getStepType() {
    return stepType;
  }

  /** @return estado durável da etapa */
  public ProvisioningCheckpointStatus getStatus() {
    return status;
  }

  /** @return referência opaca do módulo consumidor, quando existente */
  public String getExternalReference() {
    return externalReference;
  }

  /** @return número de chamadas despachadas para a etapa */
  public int getAttemptCount() {
    return attemptCount;
  }

  /** @return instante de nova tentativa, ou {@code null} quando não aplicável */
  public Instant getNextAttemptAt() {
    return nextAttemptAt;
  }

  /** @return código seguro da falha terminal ou transitória mais recente */
  public String getFailureCode() {
    return failureCode;
  }

  /** Registra o início de uma nova chamada à dependência da etapa. */
  public void beginAttempt() {
    status = ProvisioningCheckpointStatus.PROCESSING;
    attemptCount++;
    nextAttemptAt = null;
    failureCode = null;
  }

  /**
   * Mantém a etapa processando depois que a dependência aceitou a intenção durável.
   *
   * @param reference referência opaca validada pelo adaptador consumidor
   */
  public void markProcessing(String reference) {
    if (reference == null || reference.isBlank() || reference.length() > 200) {
      throw new IllegalArgumentException("checkpoint external reference is invalid");
    }
    status = ProvisioningCheckpointStatus.PROCESSING;
    externalReference = reference;
    nextAttemptAt = null;
    failureCode = null;
  }

  /**
   * Mantém uma etapa já aceita em processamento até sua próxima observação segura.
   *
   * @param nextAttempt instante UTC da próxima verificação
   */
  public void deferProcessing(Instant nextAttempt) {
    status = ProvisioningCheckpointStatus.PROCESSING;
    nextAttemptAt = Objects.requireNonNull(nextAttempt, "nextAttempt must not be null");
  }

  /**
   * Confirma que a etapa foi integralmente observada como concluída.
   *
   * <p>A referência opaca previamente recebida é preservada; uma referência nova somente pode
   * substituí-la quando for válida para a coluna persistida.
   *
   * @param reference referência opaca atualizada, ou {@code null} para manter a existente
   */
  public void complete(String reference) {
    if (reference != null && (reference.isBlank() || reference.length() > 200)) {
      throw new IllegalArgumentException("checkpoint external reference is invalid");
    }
    if (reference != null) {
      externalReference = reference;
    }
    status = ProvisioningCheckpointStatus.COMPLETED;
    nextAttemptAt = null;
    failureCode = null;
  }

  /**
   * Reabre a etapa para a próxima tentativa sem anunciar conclusão.
   *
   * @param nextAttempt instante UTC de elegibilidade futura
   * @param safeFailureCode código seguro da indisponibilidade observada
   */
  public void scheduleRetry(Instant nextAttempt, String safeFailureCode) {
    status = ProvisioningCheckpointStatus.PENDING;
    nextAttemptAt = Objects.requireNonNull(nextAttempt, "nextAttempt must not be null");
    failureCode = requireSafeCode(safeFailureCode);
  }

  /**
   * Registra que a dependência rejeitou terminalmente a etapa.
   *
   * @param safeFailureCode código seguro, sem detalhe de infraestrutura
   */
  public void fail(String safeFailureCode) {
    status = ProvisioningCheckpointStatus.FAILED;
    nextAttemptAt = null;
    failureCode = requireSafeCode(safeFailureCode);
  }

  private static String requireSafeCode(String value) {
    if (value == null || value.isBlank() || value.length() > 100) {
      throw new IllegalArgumentException("checkpoint safe failure code is invalid");
    }
    return value;
  }
}
