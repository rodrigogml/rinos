package br.com.rinos.app.api.module.account.facade;

import java.util.Optional;

import br.com.rinos.app.api.module.account.vo.AccountCreationContext;

/**
 * Deriva a fotografia autenticada da entrada hospedeira sem aceitar identidade ou origem livres.
 *
 * @author Rodrigo Leitão
 * @since 2026-08-24
 */
public interface AccountCreationContextFacade {

  /**
   * Obtém a sessão humana atual quando ela foi validada pela fronteira hospedeira.
   *
   * @return contexto transitório ou vazio quando a origem, a sessão ou a identidade não são válidas
   */
  Optional<AccountCreationContext> current();
}
