package br.com.rinos.app.ui.config;

import java.util.Optional;

import org.springframework.context.annotation.Lazy;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import br.com.rinos.app.api.module.account.facade.AccountCreationContextFacade;
import br.com.rinos.app.api.module.account.vo.AccountCreationContext;
import jakarta.servlet.http.HttpServletRequest;

/**
 * Deriva o contexto de criação exclusivamente da sessão Spring e da origem tratada pelo RFW.
 *
 * @author Rodrigo Leitão
 * @since 2026-08-24
 */
@Component
@Lazy
public class SpringAccountCreationContextFacadeImpl implements AccountCreationContextFacade {

  private final RFWRemoteAddressProviderAdapter remoteAddressProvider;

  /**
   * Cria o resolvedor sobre a única política confiável de proxy da aplicação.
   *
   * @param remoteAddressProvider adapter que resolve origem canônica
   */
  public SpringAccountCreationContextFacadeImpl(
      RFWRemoteAddressProviderAdapter remoteAddressProvider) {
    this.remoteAddressProvider = remoteAddressProvider;
  }

  /** {@inheritDoc} */
  @Override
  public Optional<AccountCreationContext> current() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication == null || !authentication.isAuthenticated()
        || !(authentication.getPrincipal() instanceof RFWAuthenticatedPrincipalAdapter principal)) {
      return Optional.empty();
    }
    RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
    if (!(attributes instanceof ServletRequestAttributes servletAttributes)) {
      return Optional.empty();
    }
    try {
      HttpServletRequest request = servletAttributes.getRequest();
      return Optional.of(new AccountCreationContext(
          principal.user().userId(),
          principal.sessionReference(),
          remoteAddressProvider.resolve(request)));
    } catch (RuntimeException unavailableContext) {
      return Optional.empty();
    }
  }
}
