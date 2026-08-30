package br.com.rinos.app.backend.module.storage.vo;

import java.util.Objects;

import br.eng.rodrigogml.rfw.database.vo.DatabaseVersionVO;

/**
 * Evidência histórica de uma atualização de tenant preservada no banco global.
 *
 * @param fileName nome imutável do script observado
 * @param version versão compacta observada
 * @param contentHash hash SHA-256 persistido no histórico
 * @author Rodrigo Leitão
 * @since 2026-08-29
 */
public record TenantDatabaseMigrationEvidenceVO(String fileName, DatabaseVersionVO version, byte[] contentHash) {

  /**
   * Valida a evidência e impede alteração posterior do array de hash.
   *
   * @throws NullPointerException quando versão ou hash forem nulos
   * @throws IllegalArgumentException quando o nome estiver vazio ou hash não tiver 32 bytes
   */
  public TenantDatabaseMigrationEvidenceVO {
    Objects.requireNonNull(version, "version must not be null");
    Objects.requireNonNull(contentHash, "contentHash must not be null");
    if (fileName == null || fileName.isBlank()) {
      throw new IllegalArgumentException("fileName must not be blank");
    }
    if (contentHash.length != 32) {
      throw new IllegalArgumentException("contentHash must have 32 bytes");
    }
    contentHash = contentHash.clone();
  }

  /**
   * Retorna cópia defensiva do hash registrado.
   *
   * @return hash SHA-256 com 32 bytes
   */
  @Override
  public byte[] contentHash() {
    return contentHash.clone();
  }
}
