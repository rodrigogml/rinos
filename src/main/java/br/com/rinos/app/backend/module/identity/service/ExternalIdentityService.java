package br.com.rinos.app.backend.module.identity.service;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.springframework.context.annotation.Lazy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.rinos.app.backend.module.identity.entity.ExternalIdentityEntity;
import br.com.rinos.app.backend.module.identity.entity.UserEntity;
import br.com.rinos.app.backend.module.identity.enums.ExternalIdentityProviderEnum;
import br.com.rinos.app.backend.module.identity.enums.ExternalIdentityStatusEnum;
import br.com.rinos.app.backend.module.identity.repository.ExternalIdentityRepository;

/**
 * Mantém vínculos externos somente a partir de emissor e subject já validados.
 *
 * @author Rodrigo Leitão
 * @since 2026-07-29
 */
@Service
@Lazy
public class ExternalIdentityService {

  private static final int MAXIMUM_EXTERNAL_KEY_LENGTH = 255;

  private final ExternalIdentityRepository repository;
  private final IdentityReferenceService references;

  /**
   * Cria o serviço sobre o repository interno.
   *
   * @param repository persistência dos vínculos externos
   */
  @Autowired
  public ExternalIdentityService(
      ExternalIdentityRepository repository,
      IdentityReferenceService references) {
    this.repository = repository;
    this.references = references;
  }

  /**
   * Preserva a construção direta usada por testes e integrações internas legadas.
   *
   * @param repository persistência dos vínculos externos
   */
  public ExternalIdentityService(ExternalIdentityRepository repository) {
    this(repository, new IdentityReferenceService());
  }

  /**
   * Cria um vínculo pendente sem receber e-mail ou token do provedor.
   *
   * @param user identidade persistida
   * @param provider provedor reconhecido
   * @param issuer emissor validado
   * @param subject identificador estável validado
   * @param verifiedAt instante UTC da validação
   * @return vínculo pendente persistido
   */
  @Transactional
  public ExternalIdentityEntity createPending(
      UserEntity user,
      ExternalIdentityProviderEnum provider,
      String issuer,
      String subject,
      Instant verifiedAt) {
    Objects.requireNonNull(user, "user must not be null");
    Objects.requireNonNull(provider, "provider must not be null");
    validateExternalKey(issuer, "issuer");
    validateExternalKey(subject, "subject");
    Objects.requireNonNull(verifiedAt, "verifiedAt must not be null");
    return repository.save(new ExternalIdentityEntity(
        user,
        references.generate(),
        provider,
        issuer,
        subject,
        verifiedAt));
  }

  /**
   * Substitui qualquer candidato ainda pendente pela identidade validada mais recente.
   *
   * <p>A operação mantém uma única candidata por usuário. Com isso, a prova mais recente do
   * cadastro aponta inequivocamente para o único vínculo pendente que pode ser ativado.
   *
   * @param user identidade persistida
   * @param provider provedor reconhecido
   * @param issuer emissor validado
   * @param subject identificador estável validado
   * @param verifiedAt instante da validação
   * @return novo vínculo pendente
   */
  @Transactional
  public ExternalIdentityEntity replacePending(
      UserEntity user,
      ExternalIdentityProviderEnum provider,
      String issuer,
      String subject,
      Instant verifiedAt) {
    Objects.requireNonNull(user, "user must not be null");
    validateUserId(user.getId());
    List<ExternalIdentityEntity> previous =
        repository.findByUserIdAndStatusForUpdate(
            user.getId(),
            ExternalIdentityStatusEnum.PENDING);
    repository.deleteAll(previous);
    repository.flush();
    return createPending(user, provider, issuer, subject, verifiedAt);
  }

  /**
   * Localiza o vínculo sem considerar o e-mail do provedor.
   *
   * @param issuer emissor validado
   * @param subject identificador estável validado
   * @return vínculo correspondente ou vazio
   */
  @Transactional(readOnly = true)
  public Optional<ExternalIdentityEntity> find(String issuer, String subject) {
    validateExternalKey(issuer, "issuer");
    validateExternalKey(subject, "subject");
    return repository.findByIssuerAndSubject(issuer, subject);
  }

  /**
   * Localiza e bloqueia o vínculo externo estável.
   *
   * @param issuer emissor validado
   * @param subject identificador estável validado
   * @return vínculo bloqueado ou vazio
   */
  @Transactional
  public Optional<ExternalIdentityEntity> findForUpdate(String issuer, String subject) {
    validateExternalKey(issuer, "issuer");
    validateExternalKey(subject, "subject");
        return repository.findByIssuerAndSubjectForUpdate(issuer, subject);
  }

  /**
   * Localiza a única identidade candidata do usuário sob lock.
   *
   * @param userId identificador interno do usuário
   * @return vínculo pendente ou vazio
   * @throws IllegalStateException quando dados anteriores deixaram múltiplas candidatas
   */
  @Transactional
  public Optional<ExternalIdentityEntity> findSinglePendingForUpdate(Long userId) {
    validateUserId(userId);
    List<ExternalIdentityEntity> pending =
        repository.findByUserIdAndStatusForUpdate(
            userId,
            ExternalIdentityStatusEnum.PENDING);
    if (pending.size() > 1) {
      throw new IllegalStateException(
          "User has more than one pending external identity");
    }
    return pending.stream().findFirst();
  }

  /**
   * Ativa idempotentemente um vínculo pendente.
   *
   * @param identity vínculo gerenciado
   * @param activatedAt instante UTC da ativação do usuário
   */
  @Transactional
  public void activate(ExternalIdentityEntity identity, Instant activatedAt) {
    Objects.requireNonNull(identity, "identity must not be null");
    Objects.requireNonNull(activatedAt, "activatedAt must not be null");
    if (identity.getStatus() == ExternalIdentityStatusEnum.ACTIVE) {
      return;
    }
    identity.setStatus(ExternalIdentityStatusEnum.ACTIVE);
    identity.setActivatedAt(activatedAt);
  }

  /**
   * Remove vínculos ainda não ativados quando a pendência vence pelo fluxo local.
   *
   * @param userId identificador persistido da identidade
   * @return quantidade de vínculos pendentes removidos
   */
  @Transactional
  public int removePendingForLocalActivation(Long userId) {
    if (userId == null || userId <= 0) {
      throw new IllegalArgumentException("userId must be positive");
    }
    List<ExternalIdentityEntity> pending = repository.findByUserIdAndStatus(
        userId,
        ExternalIdentityStatusEnum.PENDING);
    repository.deleteAll(pending);
    return pending.size();
  }

  /**
   * Protege o contrato físico das chaves externas.
   *
   * @param value chave a validar
   * @param fieldName nome seguro para diagnóstico
   */
  private static void validateExternalKey(String value, String fieldName) {
    Objects.requireNonNull(value, fieldName + " must not be null");
    if (value.isBlank() || value.length() > MAXIMUM_EXTERNAL_KEY_LENGTH) {
      throw new IllegalArgumentException(
          fieldName + " must contain between 1 and 255 characters");
    }
  }

  private static void validateUserId(Long userId) {
    Objects.requireNonNull(userId, "userId must not be null");
    if (userId <= 0) {
      throw new IllegalArgumentException("userId must be positive");
    }
  }
}
