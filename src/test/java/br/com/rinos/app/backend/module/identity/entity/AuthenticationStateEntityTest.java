package br.com.rinos.app.backend.module.identity.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import br.com.rinos.app.backend.module.identity.enums.AuthenticationAssuranceEnum;
import br.com.rinos.app.backend.module.identity.enums.AuthenticationFlowPurposeEnum;
import br.com.rinos.app.backend.module.identity.enums.AuthenticationFlowMethodStateEnum;
import br.com.rinos.app.backend.module.identity.enums.AuthenticationFlowStatusEnum;
import br.com.rinos.app.backend.module.identity.enums.AuthenticationMethodEnum;
import br.com.rinos.app.backend.module.identity.enums.AuthenticationProofStatusEnum;
import br.com.rinos.app.backend.module.identity.enums.AuthenticationProofTypeEnum;

@DisplayName("Estados persistentes da autenticação")
class AuthenticationStateEntityTest {

  private static final Instant ISSUED_AT = Instant.parse("2026-08-08T12:00:00Z");

  @Test
  void flow_shouldAllowOnlyOneTerminalTransition() {
    AuthenticationFlowEntity flow = flow();

    flow.markUsed(ISSUED_AT.plusSeconds(10));

    assertThat(flow.getStatus()).isEqualTo(AuthenticationFlowStatusEnum.USED);
    assertThat(flow.getUsedAt()).isEqualTo(ISSUED_AT.plusSeconds(10));
    assertThatThrownBy(() -> flow.invalidate(ISSUED_AT.plusSeconds(20)))
        .isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(flow::registerFailure).isInstanceOf(IllegalStateException.class);
  }

  @Test
  void proof_shouldClearActiveMarkerAndDefensivelyCopyDigest() {
    AuthenticationFlowEntity flow = flow();
    byte[] digest = new byte[] {1, 2, 3};
    AuthenticationProofEntity proof = new AuthenticationProofEntity(
        flow,
        AuthenticationProofTypeEnum.EMAIL_OTP,
        digest,
        "key-1",
        ISSUED_AT,
        ISSUED_AT.plusSeconds(120));
    digest[0] = 9;

    proof.registerAttempt();
    proof.expire(ISSUED_AT.plusSeconds(120));

    assertThat(proof.getProofDigest()).containsExactly(1, 2, 3);
    assertThat(proof.getAttemptCount()).isEqualTo(1);
    assertThat(proof.getStatus()).isEqualTo(AuthenticationProofStatusEnum.EXPIRED);
    assertThat(proof.getActiveMarker()).isNull();
    assertThatThrownBy(() -> proof.markUsed(ISSUED_AT.plusSeconds(121)))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void constructors_shouldRejectInvalidTemporalAndBinaryBoundaries() {
    assertThatThrownBy(() -> new AuthenticationFlowEntity(
        null,
        new byte[31],
        AuthenticationFlowPurposeEnum.SIGN_IN,
        AuthenticationMethodEnum.PASSWORD,
        AuthenticationAssuranceEnum.SINGLE_FACTOR,
        false,
        ISSUED_AT,
        ISSUED_AT.plusSeconds(10),
        UUID.randomUUID()))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new AuthenticationProofEntity(
        flow(),
        AuthenticationProofTypeEnum.EMAIL_OTP,
        new byte[0],
        null,
        ISSUED_AT,
        ISSUED_AT.plusSeconds(10)))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void flowMethod_shouldPreserveVerifiedEvidence_afterIdempotentReplay() {
    AuthenticationFlowMethodEntity method = new AuthenticationFlowMethodEntity(
        flow(), AuthenticationMethodEnum.PASSKEY);
    Instant verifiedAt = ISSUED_AT.plusSeconds(5);

    method.markVerified(verifiedAt, true);
    method.markVerified(verifiedAt, true);

    assertThat(method.getState()).isEqualTo(AuthenticationFlowMethodStateEnum.VERIFIED);
    assertThat(method.getVerifiedAt()).isEqualTo(verifiedAt);
    assertThat(method.getUserVerification()).isTrue();
    assertThatThrownBy(() -> method.markVerified(verifiedAt.plusSeconds(1), true))
        .isInstanceOf(IllegalStateException.class);
  }

  private static AuthenticationFlowEntity flow() {
    return new AuthenticationFlowEntity(
        null,
        new byte[32],
        AuthenticationFlowPurposeEnum.SIGN_IN,
        AuthenticationMethodEnum.PASSWORD,
        AuthenticationAssuranceEnum.MULTI_FACTOR,
        true,
        ISSUED_AT,
        ISSUED_AT.plusSeconds(300),
        UUID.fromString("da2ce531-15a0-48b6-b953-c7b8ff99b0d7"));
  }
}
