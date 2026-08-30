package br.com.rinos.app.backend.module.storage.service;

import br.com.rinos.app.backend.module.storage.vo.StorageOperationClaimVO;
import br.com.rinos.app.backend.module.storage.enums.StorageOperationType;

/**
 * Fronteira interna para a execução física de uma operação já reclamada pela fila.
 *
 * @author Rodrigo Leitão
 * @since 2026-08-30
 */
public interface StorageOperationExecutionPort {

  /**
   * Informa se o executor possui contrato para o tipo de operação estrutural indicado.
   *
   * @param operationType tipo durável reclamado pela fila global
   * @return {@code true} quando o executor pode tratar o tipo sem delegação implícita
   */
  boolean supports(StorageOperationType operationType);

  /**
   * Executa uma operação sob lease comprovado, sem assumir liderança ou autorização.
   *
   * @param claim posse exclusiva e temporária da operação
   */
  void execute(StorageOperationClaimVO claim);
}
