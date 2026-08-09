package br.com.rinos.app.ui.config;

import java.io.Serial;
import java.io.Serializable;

import br.com.rinos.app.api.vo.RinosUserPrincipalVO;
import br.eng.rodrigogml.rfw.authentication.principal.RFWAuthenticationSessionPrincipal;

/**
 * Principal local mínimo que une a identidade Rinos à referência opaca da sessão global.
 *
 * @author Rodrigo Leitão
 * @since 2026-08-09
 */
public record RFWAuthenticatedPrincipalAdapter(
    RinosUserPrincipalVO user,
    String sessionReference)
    implements RFWAuthenticationSessionPrincipal, Serializable {

  @Serial
  private static final long serialVersionUID = 1L;

  public RFWAuthenticatedPrincipalAdapter {
    if (user == null) {
      throw new IllegalArgumentException("user must not be null");
    }
    if (sessionReference == null || sessionReference.isBlank()) {
      throw new IllegalArgumentException("sessionReference must not be blank");
    }
  }

  /** Redige identidade e sessão em diagnósticos. */
  @Override
  public String toString() {
    return "RFWAuthenticatedPrincipalAdapter[user=REDACTED, sessionReference=REDACTED]";
  }
}
