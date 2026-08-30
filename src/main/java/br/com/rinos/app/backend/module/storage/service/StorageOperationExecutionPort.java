package br.com.rinos.app.backend.module.storage.service;

import br.com.rinos.app.backend.module.storage.vo.StorageOperationClaimVO;

/**
 * Fronteira interna para a execução física de uma operação já reclamada pela fila.
 *
 * @author Rodrigo Leitão
 * @since 2026-08-30
 */
public interface StorageOperationExecutionPort {

  /**
   * Executa uma operação sob lease comprovado, sem assumir liderança ou autorização.
   *
   * @param claim posse exclusiva e temporária da operação
   */
  void execute(StorageOperationClaimVO claim);
}
