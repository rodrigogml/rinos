package br.com.rinos.app.api.facade;

import br.com.rinos.app.api.enums.HumanVerificationOperationEnum;
import br.com.rinos.app.api.vo.RemoteOriginRequestVO;

/**
 * Publica a resolução confiável da origem e a política de exigência de comprovação humana.
 *
 * <p>A interface mantém adapters de apresentação desacoplados de services, entities e repositories do backend.
 *
 * @author Rodrigo Leitão
 * @since 2026-07-29
 */
public interface HumanVerificationPolicyFacade {

  /**
   * Resolve a origem canônica depois de validar a cadeia encaminhada pela instalação.
   *
   * @param request conexão direta e cabeçalhos recebidos
   * @return literal IPv4 ou IPv6 canônico
   * @throws IllegalArgumentException quando a origem não é confiável ou consistente
   */
  String resolveTrustedOrigin(RemoteOriginRequestVO request);

  /**
   * Decide se a operação deve apresentar Turnstile sem alterar contadores persistidos.
   *
   * @param operation operação pública
   * @param canonicalOrigin origem previamente validada; pode ser nula quando não houver requisição HTTP
   * @return {@code true} quando a comprovação é obrigatória
   */
  boolean isHumanVerificationRequired(
      HumanVerificationOperationEnum operation,
      String canonicalOrigin);
}
