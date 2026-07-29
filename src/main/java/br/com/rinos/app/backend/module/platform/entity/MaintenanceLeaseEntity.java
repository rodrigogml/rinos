package br.com.rinos.app.backend.module.platform.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;

/**
 * Representa o lease operacional que coordena tarefas globais exclusivas.
 *
 * <p>Os instantes são produzidos pelo MySQL e a entidade não representa configuração funcional,
 * autorização ou vínculo com tenant.
 *
 * @author Rodrigo Leitão
 * @since 2026-07-28
 */
@Entity
@Table(
    name = "platform_maintenanceLease",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_platform_maintenance_lease_key",
        columnNames = "leaseKey"))
public class MaintenanceLeaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "id", nullable = false)
  private Long id;

  @Column(name = "leaseKey", nullable = false, length = 64, updatable = false)
  private String leaseKey;

  @Column(name = "instanceId", nullable = false, length = 128)
  private String instanceId;

  @Column(name = "sessionId", nullable = false, length = 36)
  private String sessionId;

  @Column(name = "epoch", nullable = false)
  private long epoch;

  @Column(name = "acquiredAt", nullable = false)
  private Instant acquiredAt;

  @Column(name = "heartbeatAt", nullable = false)
  private Instant heartbeatAt;

  @Column(name = "leaseUntil", nullable = false)
  private Instant leaseUntil;

  @Column(name = "createdAt", nullable = false, insertable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updatedAt", nullable = false, insertable = false, updatable = false)
  private Instant updatedAt;

  @Version
  @Column(name = "version", nullable = false)
  private long version;

  /**
   * Construtor reservado ao provedor JPA.
   */
  protected MaintenanceLeaseEntity() {
  }

  /**
   * Retorna a identidade persistente da linha.
   *
   * @return identidade gerada pelo banco
   */
  public Long getId() {
    return id;
  }

  /**
   * Retorna a chave lógica do lease.
   *
   * @return chave exclusiva da coordenação
   */
  public String getLeaseKey() {
    return leaseKey;
  }

  /**
   * Retorna a instância que detém o lease.
   *
   * @return identidade estável da instância
   */
  public String getInstanceId() {
    return instanceId;
  }

  /**
   * Retorna a sessão proprietária da aquisição vigente.
   *
   * @return UUID textual da sessão
   */
  public String getSessionId() {
    return sessionId;
  }

  /**
   * Retorna o fencing token da aquisição.
   *
   * @return geração monotônica do lease
   */
  public long getEpoch() {
    return epoch;
  }

  /**
   * Retorna o instante em que a sessão adquiriu o lease.
   *
   * @return instante produzido pelo MySQL
   */
  public Instant getAcquiredAt() {
    return acquiredAt;
  }

  /**
   * Retorna o último heartbeat aceito.
   *
   * @return instante produzido pelo MySQL
   */
  public Instant getHeartbeatAt() {
    return heartbeatAt;
  }

  /**
   * Retorna o limite de validade do lease.
   *
   * @return instante de expiração calculado pelo MySQL
   */
  public Instant getLeaseUntil() {
    return leaseUntil;
  }

  /**
   * Retorna o instante de criação da linha.
   *
   * @return instante produzido pelo MySQL
   */
  public Instant getCreatedAt() {
    return createdAt;
  }

  /**
   * Retorna o instante da última atualização.
   *
   * @return instante produzido pelo MySQL
   */
  public Instant getUpdatedAt() {
    return updatedAt;
  }

  /**
   * Retorna a versão usada no controle otimista.
   *
   * @return versão persistente atual
   */
  public long getVersion() {
    return version;
  }
}
