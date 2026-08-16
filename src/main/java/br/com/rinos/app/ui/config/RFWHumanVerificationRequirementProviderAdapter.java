package br.com.rinos.app.ui.config;

import org.springframework.stereotype.Component;

import br.com.rinos.app.api.enums.HumanVerificationOperationEnum;
import br.com.rinos.app.api.facade.HumanVerificationPolicyFacade;
import br.eng.rodrigogml.rfw.authentication.enums.RFWHumanVerificationOperationEnum;
import br.eng.rodrigogml.rfw.authentication.provider.RFWHumanVerificationRequirementProvider;

/**
 * Adapta as operações públicas do RFW para a política de comprovação humana do Rinos.
 *
 * @author Rodrigo Leitão
 * @since 2026-07-29
 */
@Component
public class RFWHumanVerificationRequirementProviderAdapter
    implements RFWHumanVerificationRequirementProvider {

  private final HumanVerificationPolicyFacade policyFacade;

  /**
   * Cria o adapter sem dependência da apresentação sobre o backend.
   *
   * @param policyFacade contrato público de decisão
   */
  public RFWHumanVerificationRequirementProviderAdapter(
      HumanVerificationPolicyFacade policyFacade) {
    this.policyFacade = policyFacade;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean isRequired(
      RFWHumanVerificationOperationEnum operation,
      String remoteAddress) {
    return policyFacade.isHumanVerificationRequired(map(operation), remoteAddress);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean isRequired(
      RFWHumanVerificationOperationEnum operation,
      String remoteAddress,
      String identifier) {
    return policyFacade.isHumanVerificationRequired(
        map(operation), remoteAddress, identifier);
  }

  /**
   * Mantém um mapeamento exaustivo para que novas operações do RFW exijam decisão explícita do Rinos.
   *
   * @param operation operação RFW não nula
   * @return operação equivalente do contrato público
   */
  private HumanVerificationOperationEnum map(
      RFWHumanVerificationOperationEnum operation) {
    return switch (operation) {
      case SIGN_IN -> HumanVerificationOperationEnum.SIGN_IN;
      case REGISTRATION -> HumanVerificationOperationEnum.REGISTRATION;
      case REGISTRATION_CANCELLATION ->
          HumanVerificationOperationEnum.REGISTRATION_CANCELLATION;
      case PASSWORD_RECOVERY -> HumanVerificationOperationEnum.PASSWORD_RECOVERY;
      case ACCOUNT_CREATION -> HumanVerificationOperationEnum.ACCOUNT_CREATION;
    };
  }
}
