package br.com.rinos.app.api.facade;

import java.time.Instant;

import br.com.rinos.app.api.vo.PersistentLoginResultVO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Fronteira HTTP restrita que mantém o autenticador persistente fora dos contratos de dados.
 *
 * <p>Request e response são deliberadamente recebidos pela fachada para que seletor e validador
 * nunca sejam retornados ao adapter, armazenados em estado Vaadin ou publicados em um VO.
 *
 * @author Rodrigo Leitão
 * @since 2026-08-09
 */
public interface PersistentLoginFacade {

  /** Emite o cookie somente para a sessão persistente já publicada. */
  void create(String sessionReference, HttpServletResponse response, Instant occurredAt);

  /** Lê, valida e rotaciona atomicamente a credencial apresentada. */
  PersistentLoginResultVO resolveAndRotate(
      HttpServletRequest request,
      HttpServletResponse response,
      Instant occurredAt);

  /** Revoga idempotentemente a sessão identificada pela referência não autenticadora. */
  void revoke(String sessionReference, Instant occurredAt);

  /** Expira o cookie no navegador independentemente do estado persistente. */
  void clear(HttpServletResponse response);
}
