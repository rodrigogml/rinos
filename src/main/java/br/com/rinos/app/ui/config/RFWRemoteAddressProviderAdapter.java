package br.com.rinos.app.ui.config;

import java.util.Collections;
import java.util.List;

import org.springframework.stereotype.Component;

import br.com.rinos.app.api.facade.HumanVerificationPolicyFacade;
import br.com.rinos.app.api.vo.RemoteOriginRequestVO;
import br.eng.rodrigogml.rfw.platform.ui.access.provider.RFWRemoteAddressProvider;
import jakarta.servlet.http.HttpServletRequest;

/**
 * Entrega ao RFW somente a origem validada pelas regras explícitas de proxy do Rinos.
 *
 * @author Rodrigo Leitão
 * @since 2026-07-29
 */
@Component
public class RFWRemoteAddressProviderAdapter implements RFWRemoteAddressProvider {

  private static final String FORWARDED = "Forwarded";
  private static final String X_FORWARDED_FOR = "X-Forwarded-For";

  private final HumanVerificationPolicyFacade policyFacade;

  /**
   * Cria o adapter apoiado exclusivamente na fachada pública.
   *
   * @param policyFacade resolução confiável da origem
   */
  public RFWRemoteAddressProviderAdapter(HumanVerificationPolicyFacade policyFacade) {
    this.policyFacade = policyFacade;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public String resolve(HttpServletRequest request) {
    return policyFacade.resolveTrustedOrigin(new RemoteOriginRequestVO(
        request.getRemoteAddr(),
        readHeader(request, FORWARDED),
        readHeader(request, X_FORWARDED_FOR)));
  }

  /**
   * Preserva todas as linhas de um cabeçalho encaminhado na ordem recebida.
   *
   * @param request requisição atual
   * @param name nome estável do cabeçalho
   * @return cadeia única ou {@code null} quando ausente
   */
  private String readHeader(HttpServletRequest request, String name) {
    List<String> values = Collections.list(request.getHeaders(name));
    return values.isEmpty() ? null : String.join(",", values);
  }
}
