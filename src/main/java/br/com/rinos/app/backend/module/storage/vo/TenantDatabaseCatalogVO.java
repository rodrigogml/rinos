package br.com.rinos.app.backend.module.storage.vo;

import java.util.List;
import java.util.Objects;

import br.eng.rodrigogml.rfw.database.vo.DatabaseVersionVO;

/**
 * Fotografia ordenada e imutável do catálogo de atualizações de tenant distribuído com a aplicação.
 *
 * @param scripts scripts em ordem crescente de versão
 * @param targetVersion versão final exigida pelo código atual
 * @author Rodrigo Leitão
 * @since 2026-08-29
 */
public record TenantDatabaseCatalogVO(List<TenantDatabaseUpdateScriptVO> scripts, DatabaseVersionVO targetVersion) {

  /**
   * Rejeita catálogos vazios ou cujo alvo não seja o último script disponível.
   *
   * @throws NullPointerException quando scripts ou versão forem nulos
   * @throws IllegalArgumentException quando o catálogo estiver vazio ou tiver alvo inconsistente
   */
  public TenantDatabaseCatalogVO {
    Objects.requireNonNull(scripts, "scripts must not be null");
    Objects.requireNonNull(targetVersion, "targetVersion must not be null");
    scripts = List.copyOf(scripts);
    if (scripts.isEmpty()) {
      throw new IllegalArgumentException("scripts must not be empty");
    }
    if (!scripts.getLast().version().equals(targetVersion)) {
      throw new IllegalArgumentException("targetVersion must match the last script");
    }
  }
}
