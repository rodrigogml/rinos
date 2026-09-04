package br.com.rinos.app.backend.module.storage.vo;

import java.util.Arrays;
import java.util.Objects;

import br.eng.rodrigogml.rfw.database.vo.DatabaseVersionVO;

/**
 * Descreve um script do catálogo estrutural de tenant já validado e acompanhado de sua evidência SHA-256.
 *
 * @param fileName nome imutável do arquivo de atualização
 * @param version versão compacta derivada do nome do arquivo
 * @param contentHash hash SHA-256 do conteúdo UTF-8 distribuído
 * @author Rodrigo Leitão
 * @since 2026-08-29
 */
public record TenantDatabaseUpdateScriptVO(String fileName, DatabaseVersionVO version, byte[] contentHash) {

  /**
   * Valida a descrição e protege a evidência contra mutação externa.
   *
   * @throws NullPointerException quando versão ou hash forem nulos
   * @throws IllegalArgumentException quando o nome estiver vazio ou o hash não tiver 32 bytes
   */
  public TenantDatabaseUpdateScriptVO {
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
   * Retorna cópia defensiva da evidência criptográfica.
   *
   * @return hash SHA-256 com 32 bytes
   */
  @Override
  public byte[] contentHash() {
    return contentHash.clone();
  }

  /**
   * Compara hashes sem expor o array interno.
   *
   * @param candidate evidência candidata obrigatória
   * @return {@code true} quando os 32 bytes forem idênticos
   */
  public boolean matchesHash(byte[] candidate) {
    return candidate != null && Arrays.equals(contentHash, candidate);
  }
}
