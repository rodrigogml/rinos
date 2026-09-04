package br.com.rinos.app.api.facade;

import java.time.Instant;

import br.com.rinos.app.api.dto.ReauthenticationBeginRequestDTO;
import br.com.rinos.app.api.dto.ReauthenticationVerificationRequestDTO;
import br.com.rinos.app.api.vo.ReauthenticationResultVO;

/**
 * Publica o protocolo autenticado sem expor entities, credenciais ou authorities.
 *
 * @author Rodrigo Leitão
 * @since 2026-08-09
 */
public interface ReauthenticationFacade {

  /** Inicia a avaliação de garantia para a operação catalogada. */
  ReauthenticationResultVO begin(ReauthenticationBeginRequestDTO request);

  /** Valida e consome uma prova vinculada à identidade e sessão correntes. */
  ReauthenticationResultVO verify(ReauthenticationVerificationRequestDTO request);

  /** Cancela idempotentemente a continuação sem executar a operação original. */
  ReauthenticationResultVO cancel(
      long userId,
      String sessionReference,
      String challengeReference,
      Instant occurredAt);

  /**
   * Revalida se a sessão possui garantia recente suficiente para uma operação catalogada.
   *
   * @param userId identidade autenticada esperada
   * @param sessionReference referência opaca da sessão atual
   * @param operationId identificador estável da operação de reautenticação
   * @param occurredAt instante UTC da decisão
   * @return {@code true} somente quando a identidade, a sessão e a garantia ainda são válidas
   */
  boolean isRecentlyAuthorized(
      long userId,
      String sessionReference,
      String operationId,
      Instant occurredAt);
}
