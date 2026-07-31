package br.com.rinos.app.backend.module.identity.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import br.com.rinos.app.backend.module.identity.entity.LegalDocumentVersionEntity;
import br.com.rinos.app.backend.module.identity.enums.LegalDocumentTypeEnum;

/**
 * Acessa o catálogo imutável de versões legais no schema global.
 *
 * @author Rodrigo Leitão
 * @since 2026-07-29
 */
public interface LegalDocumentVersionRepository
    extends JpaRepository<LegalDocumentVersionEntity, Long> {

  /**
   * Resolve todas as versões vigentes em um instante UTC.
   *
   * @param effectiveAt instante de referência
   * @return versões cuja vigência contém o instante
   */
  @Query("""
      SELECT document
      FROM LegalDocumentVersionEntity document
      WHERE document.effectiveAt <= :effectiveAt
        AND (document.retiredAt IS NULL OR document.retiredAt > :effectiveAt)
      ORDER BY document.documentType, document.effectiveAt DESC
      """)
  List<LegalDocumentVersionEntity> findEffectiveAt(
      @Param("effectiveAt") Instant effectiveAt);

  /**
   * Localiza uma versão pela sua chave funcional imutável.
   *
   * @param documentType finalidade legal
   * @param versionName nome estável da versão
   * @return versão correspondente ou vazio
   */
  Optional<LegalDocumentVersionEntity> findByDocumentTypeAndVersionName(
      LegalDocumentTypeEnum documentType,
      String versionName);
}
