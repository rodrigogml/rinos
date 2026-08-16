package br.com.rinos.app.ui.config;

import java.util.UUID;

import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import br.com.rinos.app.backend.module.account.service.AccountHumanVerificationPort;
import br.com.rinos.app.backend.module.account.service.AccountHumanVerificationResult;
import br.eng.rodrigogml.rfw.authentication.enums.RFWHumanVerificationFailureEnum;
import br.eng.rodrigogml.rfw.authentication.enums.RFWHumanVerificationOperationEnum;
import br.eng.rodrigogml.rfw.authentication.provider.RFWHumanVerificationProvider;
import br.eng.rodrigogml.rfw.authentication.vo.RFWHumanVerificationRequestVO;

/** Adapta a prova efêmera de criação de conta ao provider público do RFW. */
@Component
@org.springframework.boot.autoconfigure.condition.ConditionalOnBean(RFWHumanVerificationProvider.class)
@Primary
public class RFWAccountHumanVerificationAdapter implements AccountHumanVerificationPort {

  private final RFWHumanVerificationProvider provider;

  public RFWAccountHumanVerificationAdapter(RFWHumanVerificationProvider provider) {
    this.provider = provider;
  }

  @Override
  public AccountHumanVerificationResult verify(
      String token, String canonicalOrigin, UUID idempotencyKey) {
    if (token == null || token.isBlank() || canonicalOrigin == null
        || canonicalOrigin.isBlank() || idempotencyKey == null) {
      return new AccountHumanVerificationResult(true, false);
    }
    RFWHumanVerificationOperationEnum operation =
        RFWHumanVerificationOperationEnum.ACCOUNT_CREATION;
    try {
      var result = provider.verify(new RFWHumanVerificationRequestVO(
          token, canonicalOrigin, operation, operation.action(), idempotencyKey.toString()))
          .toCompletableFuture().join();
      boolean available = result.valid()
          || result.failure() != RFWHumanVerificationFailureEnum.PROVIDER_UNAVAILABLE
              && result.failure() != RFWHumanVerificationFailureEnum.CONFIGURATION_INVALID;
      return new AccountHumanVerificationResult(available, result.valid());
    } catch (RuntimeException unavailable) {
      return AccountHumanVerificationResult.unavailable();
    }
  }
}
