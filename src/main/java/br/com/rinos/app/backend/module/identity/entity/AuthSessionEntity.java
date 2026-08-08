package br.com.rinos.app.backend.module.identity.entity;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Objects;

import br.com.rinos.app.backend.module.identity.enums.AuthenticationAssuranceEnum;
import br.com.rinos.app.backend.module.identity.enums.AuthenticationMethodEnum;
import br.com.rinos.app.backend.module.identity.enums.AuthSessionStatusEnum;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;

/**
 * Sessão global opaca cuja referência de gestão não autentica.
 *
 * <p>O cookie bruto jamais é persistido. Arrays recebidos e devolvidos são copiados para não
 * permitir alteração externa do estado gerenciado.
 *
 * @author Rodrigo Leitão
 * @since 2026-08-08
 */
@Entity
@Table(
    name = "identity_authSession",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_identity_auth_session_reference",
            columnNames = "publicReference"),
        @UniqueConstraint(
            name = "uk_identity_auth_session_selector",
            columnNames = "selectorHash")
    },
    indexes = {
        @Index(
            name = "idx_identity_auth_session_user_state",
            columnList = "idUser, status, absoluteExpiresAt"),
        @Index(
            name = "idx_identity_auth_session_expiry",
            columnList = "status, idleExpiresAt, absoluteExpiresAt")
    })
public class AuthSessionEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "id", nullable = false)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "idUser", nullable = false)
  private UserEntity user;

  @Column(name = "publicReference", nullable = false, length = 16,
      columnDefinition = "BINARY(16)")
  private byte[] publicReference;

  @Column(name = "selectorHash", nullable = false, length = 32,
      columnDefinition = "BINARY(32)")
  private byte[] selectorHash;

  @Column(name = "validatorDigest", nullable = false, length = 96,
      columnDefinition = "VARBINARY(96)")
  private byte[] validatorDigest;

  @Column(name = "keyVersion", nullable = false, length = 32)
  private String keyVersion;

  @Column(name = "remembered", nullable = false)
  private boolean remembered;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 24)
  private AuthSessionStatusEnum status;

  @Enumerated(EnumType.STRING)
  @Column(name = "primaryMethod", nullable = false, length = 32)
  private AuthenticationMethodEnum primaryMethod;

  @Enumerated(EnumType.STRING)
  @Column(name = "assuranceLevel", nullable = false, length = 24)
  private AuthenticationAssuranceEnum assuranceLevel;

  @Column(name = "authenticatedAt", nullable = false)
  private Instant authenticatedAt;

  @Column(name = "lastStrongAuthAt", nullable = false)
  private Instant lastStrongAuthAt;

  @Column(name = "lastActivityAt", nullable = false)
  private Instant lastActivityAt;

  @Column(name = "absoluteExpiresAt", nullable = false)
  private Instant absoluteExpiresAt;

  @Column(name = "idleExpiresAt", nullable = false)
  private Instant idleExpiresAt;

  @Column(name = "deviceDescription", length = 255)
  private String deviceDescription;

  @Column(name = "originAddress", length = 16, columnDefinition = "VARBINARY(16)")
  private byte[] originAddress;

  @Column(name = "userAgentDigest", length = 32, columnDefinition = "BINARY(32)")
  private byte[] userAgentDigest;

  @Column(name = "revokedAt")
  private Instant revokedAt;

  @Column(name = "revocationReason", length = 48)
  private String revocationReason;

  @Column(name = "createdAt", nullable = false, insertable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updatedAt", nullable = false, insertable = false, updatable = false)
  private Instant updatedAt;

  @Version
  @Column(name = "version", nullable = false)
  private long version;

  /** Construtor reservado ao provedor JPA. */
  protected AuthSessionEntity() {
  }

  /**
   * Cria uma sessão ativa a partir de valores já validados pelo serviço.
   *
   * @throws IllegalArgumentException quando tamanho, texto ou limites temporais são inválidos
   * @throws NullPointerException quando um valor obrigatório é nulo
   */
  public AuthSessionEntity(
      UserEntity user,
      byte[] publicReference,
      byte[] selectorHash,
      byte[] validatorDigest,
      String keyVersion,
      boolean remembered,
      AuthenticationMethodEnum primaryMethod,
      AuthenticationAssuranceEnum assuranceLevel,
      Instant authenticatedAt,
      Instant absoluteExpiresAt,
      Instant idleExpiresAt,
      String deviceDescription,
      byte[] originAddress,
      byte[] userAgentDigest) {
    this.user = Objects.requireNonNull(user, "user must not be null");
    this.publicReference = exact(publicReference, 16, "publicReference");
    this.selectorHash = exact(selectorHash, 32, "selectorHash");
    this.validatorDigest = bounded(validatorDigest, 1, 96, "validatorDigest");
    this.keyVersion = requiredText(keyVersion, 32, "keyVersion");
    this.remembered = remembered;
    this.primaryMethod = Objects.requireNonNull(primaryMethod, "primaryMethod must not be null");
    this.assuranceLevel = Objects.requireNonNull(
        assuranceLevel, "assuranceLevel must not be null");
    this.authenticatedAt = Objects.requireNonNull(
        authenticatedAt, "authenticatedAt must not be null");
    this.lastStrongAuthAt = authenticatedAt;
    this.lastActivityAt = authenticatedAt;
    this.absoluteExpiresAt = Objects.requireNonNull(
        absoluteExpiresAt, "absoluteExpiresAt must not be null");
    this.idleExpiresAt = Objects.requireNonNull(
        idleExpiresAt, "idleExpiresAt must not be null");
    if (!absoluteExpiresAt.isAfter(authenticatedAt)
        || idleExpiresAt.isAfter(absoluteExpiresAt)) {
      throw new IllegalArgumentException("session expiry is invalid");
    }
    this.deviceDescription = optionalText(deviceDescription, 255, "deviceDescription");
    this.originAddress = nullableBounded(originAddress, 4, 16, "originAddress");
    this.userAgentDigest = userAgentDigest == null
        ? null : exact(userAgentDigest, 32, "userAgentDigest");
    status = AuthSessionStatusEnum.ACTIVE;
  }

  /** @return identificador interno ou {@code null} antes da persistência */
  public Long getId() {
    return id;
  }

  /** @return identidade proprietária */
  public UserEntity getUser() {
    return user;
  }

  /** @return cópia da referência de gestão */
  public byte[] getPublicReference() {
    return copy(publicReference);
  }

  /** @return cópia do digest de localização */
  public byte[] getSelectorHash() {
    return copy(selectorHash);
  }

  /** @return cópia do verificador persistido */
  public byte[] getValidatorDigest() {
    return copy(validatorDigest);
  }

  /** @return versão do mecanismo usado no verificador */
  public String getKeyVersion() {
    return keyVersion;
  }

  /** @return {@code true} quando usa a política persistente */
  public boolean isRemembered() {
    return remembered;
  }

  /** @return estado persistente */
  public AuthSessionStatusEnum getStatus() {
    return status;
  }

  /** @return método primário */
  public AuthenticationMethodEnum getPrimaryMethod() {
    return primaryMethod;
  }

  /** @return garantia calculada */
  public AuthenticationAssuranceEnum getAssuranceLevel() {
    return assuranceLevel;
  }

  /** @return instante da autenticação */
  public Instant getAuthenticatedAt() {
    return authenticatedAt;
  }

  /** @return instante da última autenticação forte */
  public Instant getLastStrongAuthAt() {
    return lastStrongAuthAt;
  }

  /** @return última atividade efetivamente persistida */
  public Instant getLastActivityAt() {
    return lastActivityAt;
  }

  /** @return limite absoluto */
  public Instant getAbsoluteExpiresAt() {
    return absoluteExpiresAt;
  }

  /** @return limite vigente de inatividade */
  public Instant getIdleExpiresAt() {
    return idleExpiresAt;
  }

  /** @return descrição sanitizada do dispositivo ou {@code null} */
  public String getDeviceDescription() {
    return deviceDescription;
  }

  /**
   * Atualiza a atividade e limita a nova inatividade ao vencimento absoluto.
   *
   * @throws IllegalStateException quando a sessão não está ativa
   */
  public void refreshActivity(Instant at, Duration idleTimeout) {
    requireActive();
    Objects.requireNonNull(at, "at must not be null");
    Objects.requireNonNull(idleTimeout, "idleTimeout must not be null");
    if (idleTimeout.isZero() || idleTimeout.isNegative()) {
      throw new IllegalArgumentException("idleTimeout must be positive");
    }
    if (at.isAfter(lastActivityAt)) {
      lastActivityAt = at;
    }
    Instant candidate = at.plus(idleTimeout);
    idleExpiresAt = candidate.isBefore(absoluteExpiresAt) ? candidate : absoluteExpiresAt;
  }

  /** Substitui o validator depois de uso válido. */
  public void rotateValidator(byte[] digest, String keyVersion) {
    requireActive();
    validatorDigest = bounded(digest, 1, 96, "validatorDigest");
    this.keyVersion = requiredText(keyVersion, 32, "keyVersion");
  }

  /** Encerra a sessão por motivo fechado previamente validado. */
  public void revoke(Instant at, String reason) {
    requireActive();
    status = AuthSessionStatusEnum.REVOKED;
    revokedAt = Objects.requireNonNull(at, "at must not be null");
    revocationReason = requiredText(reason, 48, "reason");
  }

  /** Encerra a sessão por vencimento observado. */
  public void expire(Instant at) {
    requireActive();
    status = AuthSessionStatusEnum.EXPIRED;
    revokedAt = Objects.requireNonNull(at, "at must not be null");
    revocationReason = "EXPIRY";
  }

  private void requireActive() {
    if (status != AuthSessionStatusEnum.ACTIVE) {
      throw new IllegalStateException("session is not active");
    }
  }

  private static byte[] exact(byte[] value, int size, String name) {
    if (value == null || value.length != size) {
      throw new IllegalArgumentException(name + " length is invalid");
    }
    return copy(value);
  }

  private static byte[] bounded(byte[] value, int minimum, int maximum, String name) {
    if (value == null || value.length < minimum || value.length > maximum) {
      throw new IllegalArgumentException(name + " length is invalid");
    }
    return copy(value);
  }

  private static byte[] nullableBounded(
      byte[] value, int minimum, int maximum, String name) {
    return value == null ? null : bounded(value, minimum, maximum, name);
  }

  private static byte[] copy(byte[] value) {
    return Arrays.copyOf(value, value.length);
  }

  private static String requiredText(String value, int maximum, String name) {
    if (value == null || value.isBlank() || value.length() > maximum) {
      throw new IllegalArgumentException(name + " is invalid");
    }
    return value;
  }

  private static String optionalText(String value, int maximum, String name) {
    if (value != null && (value.isBlank() || value.length() > maximum)) {
      throw new IllegalArgumentException(name + " is invalid");
    }
    return value;
  }
}
