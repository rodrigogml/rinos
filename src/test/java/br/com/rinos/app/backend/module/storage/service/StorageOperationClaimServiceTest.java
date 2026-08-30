package br.com.rinos.app.backend.module.storage.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import br.com.rinos.app.backend.module.storage.entity.StorageOperationEntity;
import br.com.rinos.app.backend.module.storage.enums.StorageOperationType;
import br.com.rinos.app.backend.module.storage.repository.StorageOperationRepository;
import br.com.rinos.app.backend.module.storage.vo.StorageOperationClaimVO;
import br.com.rinos.app.config.StoragePropertiesConfig;

@DisplayName("Claim da fila estrutural")
class StorageOperationClaimServiceTest {

  @Test
  void claimNext_shouldClaimOnlyFirstEligibleOperation_whenQueueHasMultipleItems() {
    StorageOperationRepository repository = mock(StorageOperationRepository.class);
    StorageOperationEntity operation = new StorageOperationEntity(UUID.randomUUID(), 7L,
        StorageOperationType.MIGRATE, UUID.randomUUID(), "claim-test");
    Instant now = Instant.parse("2026-08-30T12:00:00Z");
    when(repository.findNextEligibleForUpdate(any())).thenReturn(List.of(operation));
    StorageOperationClaimService service = new StorageOperationClaimService(repository, properties(),
        Clock.fixed(now, ZoneOffset.UTC));

    Optional<StorageOperationClaimVO> claim = service.claimNext("instance-a");

    assertThat(claim).isPresent();
    assertThat(claim.orElseThrow().registryId()).isEqualTo(7L);
    assertThat(claim.orElseThrow().operationType()).isEqualTo(StorageOperationType.MIGRATE);
    assertThat(claim.orElseThrow().leaseUntil()).isEqualTo(now.plus(Duration.ofMinutes(10)));
    assertThat(operation.getLeaseOwner()).isEqualTo("instance-a");
    verify(repository).findNextEligibleForUpdate(any());
  }

  private static StoragePropertiesConfig properties() {
    return new StoragePropertiesConfig(Duration.ofSeconds(30), Duration.ofMinutes(10), Duration.ofSeconds(30), 3, 1);
  }
}
