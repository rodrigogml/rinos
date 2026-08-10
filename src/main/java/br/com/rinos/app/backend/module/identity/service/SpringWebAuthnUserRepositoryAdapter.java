package br.com.rinos.app.backend.module.identity.service;

import java.util.Arrays;

import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.web.webauthn.api.Bytes;
import org.springframework.security.web.webauthn.api.ImmutablePublicKeyCredentialUserEntity;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialUserEntity;
import org.springframework.security.web.webauthn.management.PublicKeyCredentialUserEntityRepository;

import br.com.rinos.app.backend.module.identity.entity.PasskeyUserEntity;
import br.com.rinos.app.backend.module.identity.entity.UserEntity;
import br.com.rinos.app.backend.module.identity.enums.UserStatusEnum;
import br.com.rinos.app.backend.module.identity.repository.PasskeyUserRepository;
import br.com.rinos.app.backend.module.identity.repository.UserRepository;

/**
 * Adapta o owner WebAuthn do Spring à identidade global e ao handle estável do Rinos.
 *
 * <p>O adapter não cria identidades nem permite substituir um handle existente. Exclusões devem
 * passar pela gestão de métodos, pois o contrato técnico do Spring não carrega as invariantes de
 * último método, garantia administrativa e auditoria.
 *
 * @author Rodrigo Leitão
 * @since 2026-08-10
 */
@Service
@Lazy
public class SpringWebAuthnUserRepositoryAdapter
    implements PublicKeyCredentialUserEntityRepository {

  private final UserRepository users;
  private final PasskeyUserRepository passkeyUsers;
  private final EmailNormalizationService emailNormalization;

  /** Cria o adapter sobre as autoridades globais da identidade. */
  public SpringWebAuthnUserRepositoryAdapter(
      UserRepository users,
      PasskeyUserRepository passkeyUsers,
      EmailNormalizationService emailNormalization) {
    this.users = users;
    this.passkeyUsers = passkeyUsers;
    this.emailNormalization = emailNormalization;
  }

  /** {@inheritDoc} */
  @Override
  @Transactional(readOnly = true)
  public PublicKeyCredentialUserEntity findById(Bytes id) {
    if (id == null) {
      return null;
    }
    return passkeyUsers.findByUserHandle(id.getBytes())
        .filter(owner -> owner.getUser().getStatus() == UserStatusEnum.ACTIVE)
        .map(SpringWebAuthnUserRepositoryAdapter::view)
        .orElse(null);
  }

  /** {@inheritDoc} */
  @Override
  @Transactional(readOnly = true)
  public PublicKeyCredentialUserEntity findByUsername(String username) {
    if (username == null || username.isBlank()) {
      return null;
    }
    String normalized;
    try {
      normalized = emailNormalization.normalize(username).normalizedEmail();
    } catch (IllegalArgumentException invalid) {
      return null;
    }
    return users.findByNormalizedEmailAndStatus(normalized, UserStatusEnum.ACTIVE)
        .flatMap(user -> passkeyUsers.findByUserId(user.getId()))
        .map(SpringWebAuthnUserRepositoryAdapter::view)
        .orElse(null);
  }

  /** {@inheritDoc} */
  @Override
  @Transactional
  public void save(PublicKeyCredentialUserEntity userEntity) {
    if (userEntity == null || userEntity.getId() == null) {
      throw new IllegalArgumentException("WebAuthn user entity is required");
    }
    String normalized = emailNormalization.normalize(userEntity.getName()).normalizedEmail();
    UserEntity user = users.findByNormalizedEmailForUpdate(normalized)
        .filter(candidate -> candidate.getStatus() == UserStatusEnum.ACTIVE)
        .orElseThrow(() -> new IllegalArgumentException("Active WebAuthn user is required"));
    byte[] proposedHandle = userEntity.getId().getBytes();
    PasskeyUserEntity current = passkeyUsers.findByUserIdForUpdate(user.getId()).orElse(null);
    if (current != null) {
      if (!Arrays.equals(current.getUserHandle(), proposedHandle)) {
        throw new IllegalStateException("WebAuthn user handle is immutable");
      }
      return;
    }
    PasskeyUserEntity conflicting = passkeyUsers.findByUserHandle(proposedHandle).orElse(null);
    if (conflicting != null && !conflicting.getUser().getId().equals(user.getId())) {
      throw new IllegalStateException("WebAuthn user handle already belongs to another identity");
    }
    passkeyUsers.saveAndFlush(new PasskeyUserEntity(user, proposedHandle));
  }

  /**
   * Rejeita exclusão técnica para preservar as invariantes da gestão de métodos.
   *
   * @throws UnsupportedOperationException sempre; revogação usa a fachada de gestão
   */
  @Override
  public void delete(Bytes id) {
    throw new UnsupportedOperationException(
        "WebAuthn user deletion must use passkey management");
  }

  private static PublicKeyCredentialUserEntity view(PasskeyUserEntity owner) {
    String email = owner.getUser().getEmail();
    return ImmutablePublicKeyCredentialUserEntity.builder()
        .name(email)
        .displayName(email)
        .id(new Bytes(owner.getUserHandle()))
        .build();
  }
}
