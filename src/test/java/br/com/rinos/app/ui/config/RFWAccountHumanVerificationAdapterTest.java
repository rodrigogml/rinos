package br.com.rinos.app.ui.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import br.eng.rodrigogml.rfw.authentication.enums.*;
import br.eng.rodrigogml.rfw.authentication.provider.RFWHumanVerificationProvider;
import br.eng.rodrigogml.rfw.authentication.vo.*;

class RFWAccountHumanVerificationAdapterTest {
 @Test void shouldSendTheCataloguedActionAndAcceptAValidProof(){
  RFWHumanVerificationProvider provider=mock(RFWHumanVerificationProvider.class);
  when(provider.verify(any(RFWHumanVerificationRequestVO.class))).thenReturn(CompletableFuture.completedFuture(
    new RFWHumanVerificationResultVO(true,"app.example",null,List.of())));
  UUID key=UUID.randomUUID();var result=new RFWAccountHumanVerificationAdapter(provider).verify("token","203.0.113.1",key);
  var captor=ArgumentCaptor.forClass(RFWHumanVerificationRequestVO.class);verify(provider).verify(captor.capture());
  assertThat(captor.getValue().operation()).isEqualTo(RFWHumanVerificationOperationEnum.ACCOUNT_CREATION);
  assertThat(captor.getValue().action()).isEqualTo("account-creation");assertThat(captor.getValue().idempotencyKey()).isEqualTo(key.toString());
  assertThat(result.providerAvailable()).isTrue();assertThat(result.valid()).isTrue();
 }
 @Test void shouldFailSafelyWhenProviderIsUnavailable(){
  RFWHumanVerificationProvider provider=mock(RFWHumanVerificationProvider.class);
  when(provider.verify(any(RFWHumanVerificationRequestVO.class))).thenReturn(CompletableFuture.completedFuture(
    new RFWHumanVerificationResultVO(false,null,null,List.of("internal-error"),RFWHumanVerificationFailureEnum.PROVIDER_UNAVAILABLE)));
  var result=new RFWAccountHumanVerificationAdapter(provider).verify("token","203.0.113.1",UUID.randomUUID());
  assertThat(result.providerAvailable()).isFalse();assertThat(result.valid()).isFalse();
 }
}
