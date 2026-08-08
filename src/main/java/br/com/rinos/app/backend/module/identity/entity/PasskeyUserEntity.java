package br.com.rinos.app.backend.module.identity.entity;

import java.time.Instant;
import java.util.Arrays;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * Associa a identidade a um user handle WebAuthn aleatório e estável.
 *
 * @author Rodrigo Leitão
 * @since 2026-08-08
 */
@Entity
@Table(name = "identity_passkeyUser", uniqueConstraints = {
    @UniqueConstraint(name = "uk_identity_passkey_user_user", columnNames = "idUser"),
    @UniqueConstraint(name = "uk_identity_passkey_user_handle", columnNames = "userHandle")})
public class PasskeyUserEntity {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY) @Column(name = "id", nullable = false) private Long id;
  @OneToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "idUser", nullable = false) private UserEntity user;
  @Column(name = "userHandle", nullable = false, length = 64, columnDefinition = "VARBINARY(64)") private byte[] userHandle;
  @Column(name = "createdAt", nullable = false, insertable = false, updatable = false) private Instant createdAt;

  protected PasskeyUserEntity() { }
  public PasskeyUserEntity(UserEntity user, byte[] userHandle) {
    this.user = Objects.requireNonNull(user, "user must not be null");
    Objects.requireNonNull(userHandle, "userHandle must not be null");
    if (userHandle.length < 16 || userHandle.length > 64) throw new IllegalArgumentException("userHandle length must be between 16 and 64 bytes");
    this.userHandle = Arrays.copyOf(userHandle, userHandle.length);
  }
  public Long getId() { return id; }
  public UserEntity getUser() { return user; }
  public byte[] getUserHandle() { return Arrays.copyOf(userHandle, userHandle.length); }
  public Instant getCreatedAt() { return createdAt; }
}
