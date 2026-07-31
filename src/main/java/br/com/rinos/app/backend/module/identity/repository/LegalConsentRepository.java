package br.com.rinos.app.backend.module.identity.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import br.com.rinos.app.backend.module.identity.entity.LegalConsentEntity;

/**
 * Acessa evidências imutáveis de decisões legais.
 *
 * @author Rodrigo Leitão
 * @since 2026-07-29
 */
public interface LegalConsentRepository extends JpaRepository<LegalConsentEntity, Long> {

  /**
   * Localiza uma decisão já registrada para a versão.
   *
   * @param userId identificador do usuário
   * @param legalDocumentVersionId identificador da versão
   * @return evidência existente ou vazio
   */
  Optional<LegalConsentEntity> findByUserIdAndLegalDocumentVersionId(
      Long userId,
      Long legalDocumentVersionId);

  /**
   * Localiza as decisões de um usuário para um conjunto de versões.
   *
   * @param userId identificador do usuário
   * @param legalDocumentVersionIds versões consultadas
   * @return evidências existentes
   */
  List<LegalConsentEntity> findByUserIdAndLegalDocumentVersionIdIn(
      Long userId,
      Collection<Long> legalDocumentVersionIds);

  /**
   * Lista todo o histórico legal preservado de uma identidade.
   *
   * @param userId identificador do usuário
   * @return decisões imutáveis existentes
   */
  List<LegalConsentEntity> findByUserId(Long userId);
}
