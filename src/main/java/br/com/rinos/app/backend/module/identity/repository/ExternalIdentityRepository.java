package br.com.rinos.app.backend.module.identity.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import br.com.rinos.app.backend.module.identity.entity.ExternalIdentityEntity;
import br.com.rinos.app.backend.module.identity.enums.ExternalIdentityProviderEnum;
import br.com.rinos.app.backend.module.identity.enums.ExternalIdentityStatusEnum;
import jakarta.persistence.LockModeType;

/**
 * Acessa vínculos externos exclusivamente por chaves estáveis do provedor.
 *
 * @author Rodrigo Leitão
 * @since 2026-07-29
 */
public interface ExternalIdentityRepository
    extends JpaRepository<ExternalIdentityEntity, Long> {

  /**
   * Localiza um vínculo pela chave imutável do emissor.
   *
   * @param issuer emissor validado
   * @param subject identificador estável
   * @return vínculo correspondente ou vazio
   */
  Optional<ExternalIdentityEntity> findByIssuerAndSubject(String issuer, String subject);

  /**
   * Bloqueia o vínculo estável antes de emitir ou substituir sua continuação.
   *
   * @param issuer emissor validado
   * @param subject identificador no emissor
   * @return vínculo bloqueado ou vazio
   */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("""
      SELECT identity
      FROM ExternalIdentityEntity identity
      JOIN FETCH identity.user
      WHERE identity.issuer = :issuer
        AND identity.subject = :subject
      """)
  Optional<ExternalIdentityEntity> findByIssuerAndSubjectForUpdate(
      @Param("issuer") String issuer,
      @Param("subject") String subject);

  /**
   * Lista vínculos de um usuário no estado esperado.
   *
   * @param userId identificador interno do usuário
   * @param status estado do vínculo
   * @return vínculos correspondentes
   */
  List<ExternalIdentityEntity> findByUserIdAndStatus(
      Long userId,
      ExternalIdentityStatusEnum status);

  /**
   * Lista vínculos do usuário em ordem persistente para apresentação estável.
   *
   * @param userId proprietário dos vínculos
   * @param status estado selecionado
   * @return vínculos ordenados pelo identificador interno, que não é exposto
   */
  List<ExternalIdentityEntity> findByUserIdAndStatusOrderById(
      Long userId,
      ExternalIdentityStatusEnum status);

  boolean existsByUserIdAndStatus(Long userId, ExternalIdentityStatusEnum status);

  /**
   * Conta métodos externos utilizáveis individualmente para a invariant do último método.
   *
   * @param userId proprietário dos vínculos
   * @param status estado selecionado
   * @return quantidade de vínculos correspondentes
   */
  long countByUserIdAndStatus(Long userId, ExternalIdentityStatusEnum status);

  boolean existsByUserIdAndProviderAndStatus(
      Long userId,
      ExternalIdentityProviderEnum provider,
      ExternalIdentityStatusEnum status);

  /**
   * Bloqueia os vínculos pendentes de um usuário antes de substituição ou ativação.
   *
   * @param userId identificador interno do usuário
   * @param status estado esperado
   * @return vínculos bloqueados em ordem estável
   */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("""
      SELECT identity
      FROM ExternalIdentityEntity identity
      JOIN FETCH identity.user
      WHERE identity.user.id = :userId
        AND identity.status = :status
      ORDER BY identity.id
      """)
  List<ExternalIdentityEntity> findByUserIdAndStatusForUpdate(
      @Param("userId") Long userId,
      @Param("status") ExternalIdentityStatusEnum status);

  /**
   * Bloqueia um vínculo pertencente ao usuário por sua referência pública opaca.
   *
   * @param userId proprietário autenticado
   * @param reference referência binária
   * @return vínculo correspondente ou vazio
   */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("""
      SELECT identity
      FROM ExternalIdentityEntity identity
      JOIN FETCH identity.user
      WHERE identity.user.id = :userId
        AND identity.reference = :reference
      """)
  Optional<ExternalIdentityEntity> findByUserIdAndReferenceForUpdate(
      @Param("userId") Long userId,
      @Param("reference") byte[] reference);
}
