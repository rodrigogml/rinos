package br.com.rinos.app.ui.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import br.com.rinos.app.api.enums.HumanVerificationOperationEnum;
import br.com.rinos.app.api.facade.HumanVerificationPolicyFacade;
import br.eng.rodrigogml.rfw.authentication.enums.RFWHumanVerificationOperationEnum;

@DisplayName("Adapter RFW da política de comprovação humana")
class RFWHumanVerificationRequirementProviderAdapterTest {

  @Test
  void isRequired_shouldMapEveryStableRfwOperationToPublicContract() {
    HumanVerificationPolicyFacade facade = mock(HumanVerificationPolicyFacade.class);
    RFWHumanVerificationRequirementProviderAdapter adapter =
        new RFWHumanVerificationRequirementProviderAdapter(facade);
    Map<RFWHumanVerificationOperationEnum, HumanVerificationOperationEnum> mappings = Map.of(
        RFWHumanVerificationOperationEnum.SIGN_IN, HumanVerificationOperationEnum.SIGN_IN,
        RFWHumanVerificationOperationEnum.REGISTRATION, HumanVerificationOperationEnum.REGISTRATION,
        RFWHumanVerificationOperationEnum.REGISTRATION_CANCELLATION,
        HumanVerificationOperationEnum.REGISTRATION_CANCELLATION,
        RFWHumanVerificationOperationEnum.PASSWORD_RECOVERY,
        HumanVerificationOperationEnum.PASSWORD_RECOVERY);
    mappings.values().forEach(operation ->
        when(facade.isHumanVerificationRequired(operation, "203.0.113.10")).thenReturn(true));

    mappings.forEach((rfwOperation, operation) -> {
      assertThat(adapter.isRequired(rfwOperation, "203.0.113.10")).isTrue();
      verify(facade).isHumanVerificationRequired(operation, "203.0.113.10");
    });
  }

  @Test
  void isRequired_shouldForwardEphemeralIdentifierToContextualPolicy() {
    HumanVerificationPolicyFacade facade = mock(HumanVerificationPolicyFacade.class);
    when(facade.isHumanVerificationRequired(
        HumanVerificationOperationEnum.SIGN_IN,
        "203.0.113.10",
        "person@example.test"))
        .thenReturn(true);
    RFWHumanVerificationRequirementProviderAdapter adapter =
        new RFWHumanVerificationRequirementProviderAdapter(facade);

    assertThat(adapter.isRequired(
        RFWHumanVerificationOperationEnum.SIGN_IN,
        "203.0.113.10",
        "person@example.test"))
        .isTrue();
    verify(facade).isHumanVerificationRequired(
        HumanVerificationOperationEnum.SIGN_IN,
        "203.0.113.10",
        "person@example.test");
  }
}
