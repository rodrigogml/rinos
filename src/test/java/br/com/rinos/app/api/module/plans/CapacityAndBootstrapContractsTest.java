package br.com.rinos.app.api.module.plans;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import br.com.rinos.app.api.module.plans.dto.InvitationCapacityRequest;
import br.com.rinos.app.api.module.plans.enums.ContractBootstrapStatus;
import br.com.rinos.app.api.module.plans.enums.ContractScope;
import br.com.rinos.app.api.module.plans.enums.TenantUserCapacityStatus;
import br.com.rinos.app.api.module.plans.vo.ContractBootstrapResult;
import br.com.rinos.app.api.module.plans.vo.TenantUserCapacityResult;

class CapacityAndBootstrapContractsTest {

  @Test
  void capacity_shouldCountOccupationAndReservationWithoutExceedingContractShape() {
    TenantUserCapacityResult result = new TenantUserCapacityResult(
        TenantUserCapacityStatus.RESERVED, 10, 8, 2, null);

    assertThat(result.used()).isEqualTo(10);
    assertThatThrownBy(() -> new TenantUserCapacityResult(
        TenantUserCapacityStatus.LIMIT_REACHED, 10, 10, 0, null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("safeReasonCode");
  }

  @Test
  void bootstrapResult_shouldNotMixSuccessReferenceAndFailureReason() {
    UUID contractId = UUID.randomUUID();
    ContractBootstrapResult completed = new ContractBootstrapResult(
        ContractBootstrapStatus.COMPLETED, ContractScope.PERSONAL, contractId, null);

    assertThat(completed.contractPublicId()).isEqualTo(contractId);
    assertThat(completed.toString()).doesNotContain(contractId.toString());
    assertThatThrownBy(() -> new ContractBootstrapResult(
        ContractBootstrapStatus.REJECTED, ContractScope.TENANT, contractId,
        "PLAN_DEFAULT_UNAVAILABLE"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void invitationReservation_shouldCarryOnlyProtectedRecipientReference() {
    InvitationCapacityRequest request = new InvitationCapacityRequest(
        42L, UUID.randomUUID(), "hmac:v1:opaque", null,
        Instant.parse("2026-08-16T18:00:00Z"),
        Instant.parse("2026-08-20T18:00:00Z"), "correlation-1");

    assertThat(request.toString())
        .doesNotContain("42", "hmac:v1:opaque")
        .contains("recipientFingerprint=REDACTED");
  }
}
