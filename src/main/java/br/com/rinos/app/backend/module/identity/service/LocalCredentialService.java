package br.com.rinos.app.backend.module.identity.service;

import java.time.Instant;
import java.util.Objects;

import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.rinos.app.backend.module.identity.entity.LocalCredentialEntity;
import br.com.rinos.app.backend.module.identity.entity.UserEntity;
import br.com.rinos.app.backend.module.identity.enums.LocalCredentialStatusEnum;
import br.com.rinos.app.backend.module.identity.repository.LocalCredentialRepository;

/**
 * Mantém a credencial local vigente sem receber ou retornar senha em claro.
 *
 * @author Rodrigo Leitão
 * @since 2026-07-29
 */
@Service
@Lazy
public class LocalCredentialService {

  private static final int MAXIMUM_HASH_LENGTH = 255;

  private final LocalCredentialRepository repository;

  /**
   * Cria o serviço sobre o repository interno de credenciais.
   *
   * @param repository persistência restrita ao backend
   */
  public LocalCredentialService(LocalCredentialRepository repository) {
    this.repository = repository;
  }

  /**
   * Cria ou substitui a credencial local de uma identidade.
   *
   * <p>O argumento deve estar no formato codificado do {@code DelegatingPasswordEncoder}. A
   * produção do hash e a validação da senha pertencem à tarefa 3.1.
   *
   * @param user identidade persistida
   * @param passwordHash hash codificado, nunca a senha em claro
   * @throws NullPointerException quando qualquer argumento é nulo
   * @throws IllegalArgumentException quando o usuário não está persistido ou o hash é inválido
   */
  @Transactional
  public void replace(UserEntity user, String passwordHash) {
    Objects.requireNonNull(user, "user must not be null");
    validateUserId(user.getId());
    validatePasswordHash(passwordHash);

    LocalCredentialEntity credential = repository.findByUserIdForUpdate(user.getId())
        .orElseGet(() -> new LocalCredentialEntity(user, passwordHash));
    credential.setPasswordHash(passwordHash);
    credential.setStatus(LocalCredentialStatusEnum.ACTIVE);
    credential.clearInvalidatedAt();
    repository.save(credential);
  }

  /**
   * Invalida e remove a senha local antes de reaproveitar a pendência pelo Google.
   *
   * @param userId identificador persistido da identidade
   * @param invalidatedAt instante UTC da remoção
   * @return {@code true} quando havia credencial para remover
   * @throws NullPointerException quando os argumentos são nulos
   * @throws IllegalArgumentException quando o identificador não é positivo
   */
  @Transactional
  public boolean invalidateAndRemoveForGoogle(Long userId, Instant invalidatedAt) {
    validateUserId(userId);
    Objects.requireNonNull(invalidatedAt, "invalidatedAt must not be null");
    return repository.findByUserIdForUpdate(userId)
        .map(credential -> {
          credential.setStatus(LocalCredentialStatusEnum.INVALIDATED);
          credential.setInvalidatedAt(invalidatedAt);
          repository.saveAndFlush(credential);
          repository.delete(credential);
          repository.flush();
          return true;
        })
        .orElse(false);
  }

  /**
   * Valida somente o contrato físico do hash já produzido.
   *
   * @param passwordHash hash codificado
   */
  private static void validatePasswordHash(String passwordHash) {
    Objects.requireNonNull(passwordHash, "passwordHash must not be null");
    if (passwordHash.isBlank()) {
      throw new IllegalArgumentException("passwordHash must not be blank");
    }
    if (passwordHash.length() > MAXIMUM_HASH_LENGTH) {
      throw new IllegalArgumentException("passwordHash must not exceed 255 characters");
    }
  }

  /**
   * Protege operações bloqueáveis contra identidade transitória.
   *
   * @param userId identificador a validar
   */
  private static void validateUserId(Long userId) {
    Objects.requireNonNull(userId, "userId must not be null");
    if (userId <= 0) {
      throw new IllegalArgumentException("userId must be positive");
    }
  }
}
