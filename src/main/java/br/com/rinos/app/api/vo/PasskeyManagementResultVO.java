package br.com.rinos.app.api.vo;

import br.com.rinos.app.api.enums.PasskeyManagementStatusEnum;

/**
 * Resultado sem dados de credencial de uma mutacao sobre passkey.
 *
 * @param status resultado publico da operacao
 * @author Rodrigo Leitão
 * @since 2026-08-10
 */
public record PasskeyManagementResultVO(PasskeyManagementStatusEnum status) {

  /**
   * Cria um resultado terminal obrigatorio.
   *
   * @throws IllegalArgumentException quando o status for nulo
   */
  public PasskeyManagementResultVO {
    if (status == null) {
      throw new IllegalArgumentException("status must not be null");
    }
  }
}
