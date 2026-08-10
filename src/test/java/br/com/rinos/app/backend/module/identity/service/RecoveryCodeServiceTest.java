package br.com.rinos.app.backend.module.identity.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.IntStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import br.com.rinos.app.backend.module.identity.entity.RecoveryCodeEntity;
import br.com.rinos.app.backend.module.identity.entity.RecoveryCodeSetEntity;
import br.com.rinos.app.backend.module.identity.entity.UserEntity;
import br.com.rinos.app.backend.module.identity.enums.FactorOperationStatusEnum;
import br.com.rinos.app.backend.module.identity.enums.RecoveryCodeSetStatusEnum;
import br.com.rinos.app.backend.module.identity.enums.RecoveryCodeStatusEnum;
import br.com.rinos.app.backend.module.identity.enums.UserStatusEnum;
import br.com.rinos.app.backend.module.identity.repository.RecoveryCodeRepository;
import br.com.rinos.app.backend.module.identity.repository.RecoveryCodeSetRepository;
import br.com.rinos.app.backend.module.identity.repository.UserRepository;
import br.com.rinos.app.backend.module.identity.vo.IssuedRecoveryCodeSetVO;
import br.eng.rodrigogml.rfw.authentication.service.RFWRecoveryCodeService;
import br.eng.rodrigogml.rfw.authentication.vo.RFWRecoveryCodesVO;

@DisplayName("Códigos de recuperação")
class RecoveryCodeServiceTest {

  private static final Instant NOW = Instant.parse("2026-08-09T18:00:00Z");
  private static final UUID CORRELATION_ID = UUID.fromString(
      "f4d0f657-05d8-49b1-9c9e-f5deac746647");

  private UserRepository users;
  private RecoveryCodeSetRepository sets;
  private RecoveryCodeRepository codes;
  private IdentityAuditService audit;
  private RFWRecoveryCodeService protocol;
  private RecoveryCodeService service;
  private UserEntity user;

  @BeforeEach
  void setUp() {
    users = mock(UserRepository.class);
    sets = mock(RecoveryCodeSetRepository.class);
    codes = mock(RecoveryCodeRepository.class);
    audit = mock(IdentityAuditService.class);
    protocol = mock(RFWRecoveryCodeService.class);
    service = new RecoveryCodeService(
        users, sets, codes, new IdentityReferenceService(), audit, protocol);
    user = new UserEntity("recovery@example.test", "recovery@example.test", UserStatusEnum.ACTIVE);
    ReflectionTestUtils.setField(user, "id", 17L);
    when(users.findByIdForUpdate(17L)).thenReturn(Optional.of(user));
  }

  @Test
  void generate_shouldPersistIndependentHashesAndReturnOnlyRawPresentation_whenNoSetExists() {
    List<String> rawCodes = rawCodes();
    List<String> hashes = hashes();
    when(protocol.generate()).thenReturn(new RFWRecoveryCodesVO(rawCodes));
    when(protocol.encode(rawCodes)).thenReturn(hashes);
    when(sets.findByUserIdAndStatusForUpdate(17L, RecoveryCodeSetStatusEnum.ACTIVE))
        .thenReturn(Optional.empty());
    when(sets.saveAndFlush(any(RecoveryCodeSetEntity.class))).thenAnswer(invocation -> {
      RecoveryCodeSetEntity saved = invocation.getArgument(0);
      ReflectionTestUtils.setField(saved, "id", 31L);
      return saved;
    });

    IssuedRecoveryCodeSetVO result = service.generate(17L, CORRELATION_ID, NOW);

    assertThat(result.codes()).containsExactlyElementsOf(rawCodes);
    assertThat(result.toString()).doesNotContain(rawCodes.getFirst()).contains("codes=REDACTED");
    ArgumentCaptor<RecoveryCodeEntity> persisted = ArgumentCaptor.forClass(RecoveryCodeEntity.class);
    verify(codes, org.mockito.Mockito.times(10)).save(persisted.capture());
    assertThat(persisted.getAllValues()).extracting(RecoveryCodeEntity::getCodeHash)
        .containsExactlyElementsOf(hashes);
    assertThat(persisted.getAllValues()).extracting(RecoveryCodeEntity::getOrdinal)
        .containsExactly((short) 1, (short) 2, (short) 3, (short) 4, (short) 5,
            (short) 6, (short) 7, (short) 8, (short) 9, (short) 10);
    verify(audit).record(any(), any(), any(), any(), any(), any(), any(), any(), any());
  }

  @Test
  void generate_shouldInvalidateEveryAvailableCodeAndPreviousSet_whenReplacing() {
    RecoveryCodeSetEntity previous = new RecoveryCodeSetEntity(user, UUID.randomUUID(), NOW.minusSeconds(60));
    ReflectionTestUtils.setField(previous, "id", 29L);
    RecoveryCodeEntity available = new RecoveryCodeEntity(previous, "previous-available", 1);
    RecoveryCodeEntity used = new RecoveryCodeEntity(previous, "previous-used", 2);
    used.use(NOW.minusSeconds(30));
    when(sets.findByUserIdAndStatusForUpdate(17L, RecoveryCodeSetStatusEnum.ACTIVE))
        .thenReturn(Optional.of(previous));
    when(codes.findByCodeSetIdForUpdate(29L)).thenReturn(List.of(available, used));
    when(protocol.generate()).thenReturn(new RFWRecoveryCodesVO(rawCodes()));
    when(protocol.encode(any())).thenReturn(hashes());
    when(sets.saveAndFlush(any(RecoveryCodeSetEntity.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    service.generate(17L, CORRELATION_ID, NOW);

    assertThat(previous.getStatus()).isEqualTo(RecoveryCodeSetStatusEnum.INVALIDATED);
    assertThat(available.getStatus()).isEqualTo(RecoveryCodeStatusEnum.INVALIDATED);
    assertThat(used.getStatus()).isEqualTo(RecoveryCodeStatusEnum.USED);
    verify(codes).saveAll(List.of(available, used));
  }

  @Test
  void generate_shouldRejectIncompleteProtocolOutput_withoutChangingPersistence() {
    when(protocol.generate()).thenReturn(new RFWRecoveryCodesVO(rawCodes().subList(0, 9)));

    assertThatThrownBy(() -> service.generate(17L, CORRELATION_ID, NOW))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("exactly 10");
    verify(sets, never()).saveAndFlush(any());
    verify(codes, never()).save(any());
  }

  @Test
  void consume_shouldConsumeMatchedCodeAndExhaustSet_whenItIsTheLastAvailableCode() {
    RecoveryCodeSetEntity set = new RecoveryCodeSetEntity(user, UUID.randomUUID(), NOW.minusSeconds(60));
    ReflectionTestUtils.setField(set, "id", 33L);
    RecoveryCodeEntity code = new RecoveryCodeEntity(set, "available-hash", 1);
    when(sets.findByUserIdAndStatusForUpdate(17L, RecoveryCodeSetStatusEnum.ACTIVE))
        .thenReturn(Optional.of(set));
    when(codes.findByCodeSetIdForUpdate(33L)).thenReturn(List.of(code));
    when(protocol.findMatchingIndex("AAAA-BBBB-CCCC", List.of("available-hash")))
        .thenReturn(0);

    FactorOperationStatusEnum result = service.consume(17L, "AAAA-BBBB-CCCC", NOW);

    assertThat(result).isEqualTo(FactorOperationStatusEnum.EXHAUSTED);
    assertThat(code.getStatus()).isEqualTo(RecoveryCodeStatusEnum.USED);
    assertThat(set.getStatus()).isEqualTo(RecoveryCodeSetStatusEnum.EXHAUSTED);
  }

  @Test
  void consume_shouldRejectBlankCode_withoutReadingActiveSet() {
    FactorOperationStatusEnum result = service.consume(17L, "  ", NOW);

    assertThat(result).isEqualTo(FactorOperationStatusEnum.REJECTED);
    verify(sets, never()).findByUserIdAndStatusForUpdate(any(), any());
  }

  @Test
  void generate_shouldDenyInactiveUser_beforeGeneratingSecrets() {
    user.setStatus(UserStatusEnum.BLOCKED);

    assertThatThrownBy(() -> service.generate(17L, CORRELATION_ID, NOW))
        .isInstanceOf(SecurityException.class);
    verify(protocol, never()).generate();
  }

  private static List<String> rawCodes() {
    return IntStream.rangeClosed(1, 10)
        .mapToObj(index -> "AAAA-BBBB-%04d".formatted(index))
        .toList();
  }

  private static List<String> hashes() {
    return IntStream.rangeClosed(1, 10)
        .mapToObj(index -> "{argon2id}hash-" + index)
        .toList();
  }
}
