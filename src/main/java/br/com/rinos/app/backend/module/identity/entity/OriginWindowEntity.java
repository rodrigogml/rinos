package br.com.rinos.app.backend.module.identity.entity;

import java.time.Instant;
import java.util.Arrays;
import java.util.Objects;

import br.com.rinos.app.backend.module.identity.enums.OriginOperationEnum;
import br.com.rinos.app.backend.module.identity.enums.OriginPolicyEnum;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;

/**
 * Mantém um contador temporal por endereço, operação e política de segurança.
 *
 * <p>O endereço não representa identidade e nunca deve ser copiado para eventos permanentes.
 *
 * @author Rodrigo Leitão
 * @since 2026-07-29
 */
@Entity
@Table(
    name = "security_originWindow",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_security_origin_window",
        columnNames = {"originAddress", "operation", "policy", "windowStartedAt"}),
    indexes = @Index(
        name = "idx_security_origin_window_expiry",
        columnList = "windowEndsAt"))
public class OriginWindowEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "id", nullable = false)
  private Long id;

  @Column(name = "originAddress", nullable = false, columnDefinition = "VARBINARY(16)")
  private byte[] originAddress;

  @Enumerated(EnumType.STRING)
  @Column(name = "operation", nullable = false, length = 48)
  private OriginOperationEnum operation;

  @Enumerated(EnumType.STRING)
  @Column(name = "policy", nullable = false, length = 32)
  private OriginPolicyEnum policy;

  @Column(name = "activeMarker")
  private Boolean activeMarker;

  @Column(name = "windowStartedAt", nullable = false)
  private Instant windowStartedAt;

  @Column(name = "windowEndsAt", nullable = false)
  private Instant windowEndsAt;

  @Column(name = "eventCount", nullable = false)
  private int eventCount;

  @Column(name = "blockedUntil")
  private Instant blockedUntil;

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
  protected OriginWindowEntity() {
  }

  /**
   * Cria uma janela ainda não persistida.
   *
   * @param originAddress endereço canônico com quatro ou 16 bytes
   * @param operation operação protegida
   * @param policy política contabilizada
   * @param windowStartedAt início UTC da janela
   * @param windowEndsAt fim UTC da janela
   */
  public OriginWindowEntity(
      byte[] originAddress,
      OriginOperationEnum operation,
      OriginPolicyEnum policy,
      Instant windowStartedAt,
      Instant windowEndsAt) {
    Objects.requireNonNull(originAddress, "originAddress must not be null");
    if (originAddress.length != 4 && originAddress.length != 16) {
      throw new IllegalArgumentException("originAddress must contain 4 or 16 bytes");
    }
    this.originAddress = Arrays.copyOf(originAddress, originAddress.length);
    this.operation = Objects.requireNonNull(operation, "operation must not be null");
    this.policy = Objects.requireNonNull(policy, "policy must not be null");
    activeMarker = Boolean.TRUE;
    this.windowStartedAt =
        Objects.requireNonNull(windowStartedAt, "windowStartedAt must not be null");
    this.windowEndsAt = Objects.requireNonNull(windowEndsAt, "windowEndsAt must not be null");
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
   * Retorna cópia do endereço binário.
   *
   * @return quatro bytes para IPv4 ou 16 para IPv6
   */
  public byte[] getOriginAddress() {
    return Arrays.copyOf(originAddress, originAddress.length);
  }

  /**
   * Retorna a operação protegida.
   *
   * @return operação de segurança
   */
  public OriginOperationEnum getOperation() {
    return operation;
  }

  /**
   * Retorna a política contabilizada.
   *
   * @return política da janela
   */
  public OriginPolicyEnum getPolicy() {
    return policy;
  }

  /**
   * Indica que a linha disputa a unicidade da janela corrente.
   *
   * @return {@code true} enquanto ativa; {@code null} quando histórica
   */
  public Boolean getActiveMarker() {
    return activeMarker;
  }

  /**
   * Libera a chave única para a próxima janela e preserva a linha como histórico.
   */
  public void close() {
    activeMarker = null;
  }

  /**
   * Retorna o início determinístico da janela.
   *
   * @return instante UTC
   */
  public Instant getWindowStartedAt() {
    return windowStartedAt;
  }

  /**
   * Retorna o fim da janela.
   *
   * @return instante UTC
   */
  public Instant getWindowEndsAt() {
    return windowEndsAt;
  }

  /**
   * Retorna os eventos contabilizados.
   *
   * @return contador não negativo
   */
  public int getEventCount() {
    return eventCount;
  }

  /**
   * Retorna o limite explícito do bloqueio.
   *
   * @return instante UTC ou {@code null}
   */
  public Instant getBlockedUntil() {
    return blockedUntil;
  }

  /**
   * Retorna o instante de criação.
   *
   * @return instante UTC produzido pelo MySQL
   */
  public Instant getCreatedAt() {
    return createdAt;
  }

  /**
   * Retorna o instante da última atualização.
   *
   * @return instante UTC produzido pelo MySQL
   */
  public Instant getUpdatedAt() {
    return updatedAt;
  }

  /**
   * Retorna a versão otimista.
   *
   * @return versão persistente
   */
  public long getVersion() {
    return version;
  }
}
