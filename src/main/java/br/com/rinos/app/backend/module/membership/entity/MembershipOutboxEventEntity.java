package br.com.rinos.app.backend.module.membership.entity;

import java.time.Instant;
import java.util.Arrays;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import br.com.rinos.app.backend.module.identity.vo.EncryptedAuthenticationSecretVO;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "membership_outboxEvent")
public class MembershipOutboxEventEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "idMembershipOutboxEvent")
  private Long id;

  @JdbcTypeCode(SqlTypes.BINARY)
  @Column(name = "eventId", columnDefinition = "BINARY(16)", nullable = false, updatable = false)
  private UUID eventId;

  @Column(name = "aggregateType", nullable = false, updatable = false)
  private String aggregateType;

  @Column(name = "aggregateId", nullable = false, updatable = false)
  private Long aggregateId;

  @Column(name = "eventType", nullable = false, updatable = false)
  private String eventType;

  @Column(name = "payload", columnDefinition = "JSON", nullable = false, updatable = false)
  private String payload;

  @Column(name = "status", nullable = false)
  private String status;

  @Column(name = "attemptCount", nullable = false)
  private int attemptCount;

  @Column(name = "secretCiphertext")
  private byte[] secretCiphertext;

  @Column(name = "secretNonce", columnDefinition = "BINARY(12)")
  private byte[] secretNonce;

  @Column(name = "secretKeyId")
  private String secretKeyId;

  @Column(name = "secretExpiresAt")
  private Instant secretExpiresAt;

  @Column(name = "nextAttemptAt")
  private Instant nextAttemptAt;

  @Column(name = "leaseOwner")
  private String leaseOwner;

  @Column(name = "leaseUntil")
  private Instant leaseUntil;

  @Column(name = "publishedAt")
  private Instant publishedAt;

  protected MembershipOutboxEventEntity() {}

  public MembershipOutboxEventEntity(UUID eventId, Long aggregateId, String eventType, String payload) {
    this(eventId, "MEMBERSHIP", aggregateId, eventType, payload);
  }

  public MembershipOutboxEventEntity(
      UUID eventId, String aggregateType, Long aggregateId, String eventType, String payload) {
    this.eventId = eventId;
    this.aggregateType = aggregateType;
    this.aggregateId = aggregateId;
    this.eventType = eventType;
    this.payload = payload;
    this.status = "PENDING";
  }

  public MembershipOutboxEventEntity(
      UUID eventId,
      Long invitationId,
      String eventType,
      String payload,
      EncryptedAuthenticationSecretVO secret,
      Instant secretExpiresAt) {
    this(eventId, "INVITATION", invitationId, eventType, payload);
    this.secretCiphertext = secret.ciphertext();
    this.secretNonce = secret.nonce();
    this.secretKeyId = secret.keyVersion();
    this.secretExpiresAt = secretExpiresAt;
  }

  public UUID getEventId() { return eventId; }
  public Long getAggregateId() { return aggregateId; }
  public String getEventType() { return eventType; }
  public String getStatus() { return status; }
  public int getAttemptCount() { return attemptCount; }
  public Instant getSecretExpiresAt() { return secretExpiresAt; }

  public EncryptedAuthenticationSecretVO encryptedSecret() {
    if (secretCiphertext == null || secretNonce == null || secretKeyId == null) return null;
    return new EncryptedAuthenticationSecretVO(secretCiphertext, secretNonce, secretKeyId);
  }

  public void claim(String owner, Instant until) {
    status = "PROCESSING";
    leaseOwner = owner;
    leaseUntil = until;
  }

  public void publish(Instant at) {
    status = "PUBLISHED";
    publishedAt = at;
    clearLeaseAndSecret();
  }

  public void retry(Instant at) {
    status = "PENDING";
    attemptCount++;
    nextAttemptAt = at;
    leaseOwner = null;
    leaseUntil = null;
  }

  public void cancel() {
    status = "CANCELLED";
    clearLeaseAndSecret();
  }

  private void clearLeaseAndSecret() {
    if (secretCiphertext != null) Arrays.fill(secretCiphertext, (byte) 0);
    if (secretNonce != null) Arrays.fill(secretNonce, (byte) 0);
    secretCiphertext = null;
    secretNonce = null;
    secretKeyId = null;
    secretExpiresAt = null;
    nextAttemptAt = null;
    leaseOwner = null;
    leaseUntil = null;
  }
}
