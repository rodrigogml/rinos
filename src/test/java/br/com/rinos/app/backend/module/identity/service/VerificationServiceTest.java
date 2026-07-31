package br.com.rinos.app.backend.module.identity.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import br.com.rinos.app.backend.module.identity.entity.RegistrationEntity;
import br.com.rinos.app.backend.module.identity.entity.UserEntity;
import br.com.rinos.app.backend.module.identity.entity.VerificationEntity;
import br.com.rinos.app.backend.module.identity.enums.RegistrationMethodEnum;
import br.com.rinos.app.backend.module.identity.enums.RegistrationStatusEnum;
import br.com.rinos.app.backend.module.identity.enums.UserStatusEnum;
import br.com.rinos.app.backend.module.identity.enums.VerificationConsumptionStatusEnum;
import br.com.rinos.app.backend.module.identity.enums.VerificationPurposeEnum;
import br.com.rinos.app.backend.module.identity.enums.VerificationStatusEnum;
import br.com.rinos.app.backend.module.identity.repository.RegistrationRepository;
import br.com.rinos.app.backend.module.identity.repository.VerificationRepository;
import br.com.rinos.app.backend.module.identity.vo.IssuedVerificationVO;
import br.com.rinos.app.backend.module.identity.vo.VerificationInspectionVO;
import br.com.rinos.app.config.VerificationPropertiesConfig;

@DisplayName("Comprovações de uso único")
class VerificationServiceTest {

  private static final Instant ISSUED_AT = Instant.parse("2026-07-29T18:00:00Z");
  private static final Duration VALIDITY = Duration.ofHours(24);

  private VerificationRepository repository;
  private RegistrationRepository registrationRepository;
  private VerificationTokenService tokenService;
  private VerificationService service;
  private RegistrationEntity registration;

  /**
   * Prepara cadastro persistido e dependências isoladas.
   */
  @BeforeEach
  void setUp() {
    repository = mock(VerificationRepository.class);
    registrationRepository = mock(RegistrationRepository.class);
    tokenService = new VerificationTokenService();
    service = new VerificationService(
        repository,
        registrationRepository,
        tokenService,
        new VerificationPropertiesConfig(VALIDITY));
    registration = registration(51L);
    when(registrationRepository.findByIdForUpdate(51L))
        .thenReturn(Optional.of(registration));
  }

  /**
   * Invalida a prova anterior e persiste somente o hash da substituta.
   */
  @Test
  void issue_shouldInvalidatePreviousAndReturnRedactedEnvelope_whenPurposeIsReissued() {
    VerificationEntity previous = verification(
        registration,
        VerificationPurposeEnum.REGISTRATION_EMAIL,
        "previous-token",
        ISSUED_AT.minusSeconds(60));
    when(repository.findByRegistrationIdAndPurposeAndStatusForUpdate(
        51L,
        VerificationPurposeEnum.REGISTRATION_EMAIL,
        VerificationStatusEnum.OPEN)).thenReturn(List.of(previous));
    when(repository.saveAndFlush(any(VerificationEntity.class))).thenAnswer(invocation -> {
      VerificationEntity entity = invocation.getArgument(0);
      ReflectionTestUtils.setField(entity, "id", 61L);
      return entity;
    });

    IssuedVerificationVO result = service.issue(
        registration,
        VerificationPurposeEnum.REGISTRATION_EMAIL,
        ISSUED_AT);

    assertThat(previous.getStatus()).isEqualTo(VerificationStatusEnum.INVALIDATED);
    assertThat(previous.getInvalidatedAt()).isEqualTo(ISSUED_AT);
    assertThat(result.getExpiresAt()).isEqualTo(ISSUED_AT.plus(VALIDITY));
    assertThat(result.toString())
        .contains("token=REDACTED")
        .doesNotContain(result.getToken());
    assertThat(result.getToken()).hasSize(43);
  }

  /**
   * Aceita a prova uma única vez e rejeita o replay sem devolver segredo.
   */
  @Test
  void consume_shouldVerifyOnceAndRejectReplay_whenTokenIsPresentedTwice() {
    String token = tokenService.generate();
    VerificationEntity verification = verification(
        registration,
        VerificationPurposeEnum.REGISTRATION_EMAIL,
        token,
        ISSUED_AT);
    when(repository.findByTokenHashForUpdate(any(byte[].class)))
        .thenReturn(Optional.of(verification));

    VerificationConsumptionStatusEnum first = service.consume(
        51L,
        VerificationPurposeEnum.REGISTRATION_EMAIL,
        token,
        ISSUED_AT.plusSeconds(30));
    VerificationConsumptionStatusEnum replay = service.consume(
        51L,
        VerificationPurposeEnum.REGISTRATION_EMAIL,
        token,
        ISSUED_AT.plusSeconds(40));

    assertThat(first).isEqualTo(VerificationConsumptionStatusEnum.VERIFIED);
    assertThat(replay).isEqualTo(VerificationConsumptionStatusEnum.REJECTED);
    assertThat(verification.getStatus()).isEqualTo(VerificationStatusEnum.USED);
    assertThat(verification.getUsedAt()).isEqualTo(ISSUED_AT.plusSeconds(30));
    assertThat(first.toString()).doesNotContain(token);
  }

  /**
   * Rejeita token válido usado com outra finalidade sem consumir a prova.
   */
  @Test
  void consume_shouldRejectCrossPurposeProof_whenPurposeDoesNotMatch() {
    String token = tokenService.generate();
    VerificationEntity verification = verification(
        registration,
        VerificationPurposeEnum.REGISTRATION_CANCEL,
        token,
        ISSUED_AT);
    when(repository.findByTokenHashForUpdate(any(byte[].class)))
        .thenReturn(Optional.of(verification));

    VerificationConsumptionStatusEnum result = service.consume(
        51L,
        VerificationPurposeEnum.REGISTRATION_EMAIL,
        token,
        ISSUED_AT.plusSeconds(30));

    assertThat(result).isEqualTo(VerificationConsumptionStatusEnum.REJECTED);
    assertThat(verification.getStatus()).isEqualTo(VerificationStatusEnum.OPEN);
  }

  /**
   * Rejeita token válido apresentado para outro cadastro.
   */
  @Test
  void consume_shouldRejectCrossRegistrationProof_whenRegistrationDoesNotMatch() {
    String token = tokenService.generate();
    VerificationEntity verification = verification(
        registration,
        VerificationPurposeEnum.REGISTRATION_EMAIL,
        token,
        ISSUED_AT);
    when(repository.findByTokenHashForUpdate(any(byte[].class)))
        .thenReturn(Optional.of(verification));

    VerificationConsumptionStatusEnum result = service.consume(
        99L,
        VerificationPurposeEnum.REGISTRATION_EMAIL,
        token,
        ISSUED_AT.plusSeconds(30));

    assertThat(result).isEqualTo(VerificationConsumptionStatusEnum.REJECTED);
    assertThat(verification.getStatus()).isEqualTo(VerificationStatusEnum.OPEN);
  }

  /**
   * Expira a prova exatamente no limite sem permitir consumo tardio.
   */
  @Test
  void consume_shouldExpireProof_whenValidityBoundaryWasReached() {
    String token = tokenService.generate();
    VerificationEntity verification = verification(
        registration,
        VerificationPurposeEnum.REGISTRATION_EMAIL,
        token,
        ISSUED_AT);
    when(repository.findByTokenHashForUpdate(any(byte[].class)))
        .thenReturn(Optional.of(verification));

    VerificationConsumptionStatusEnum result = service.consume(
        51L,
        VerificationPurposeEnum.REGISTRATION_EMAIL,
        token,
        ISSUED_AT.plus(VALIDITY));

    assertThat(result).isEqualTo(VerificationConsumptionStatusEnum.EXPIRED);
    assertThat(verification.getStatus()).isEqualTo(VerificationStatusEnum.EXPIRED);
    assertThat(verification.getUsedAt()).isNull();
  }

  /**
   * Invalida todas as finalidades abertas antes de ativação ou cancelamento.
   */
  @Test
  void invalidateAllOpen_shouldInvalidateEveryOpenProof_whenRegistrationEnds() {
    VerificationEntity email = verification(
        registration,
        VerificationPurposeEnum.REGISTRATION_EMAIL,
        tokenService.generate(),
        ISSUED_AT);
    VerificationEntity cancellation = verification(
        registration,
        VerificationPurposeEnum.REGISTRATION_CANCEL,
        tokenService.generate(),
        ISSUED_AT);
    when(repository.findByRegistrationIdAndStatusForUpdate(
        51L,
        VerificationStatusEnum.OPEN)).thenReturn(List.of(email, cancellation));

    int affected = service.invalidateAllOpen(51L, ISSUED_AT.plusSeconds(10));

    assertThat(affected).isEqualTo(2);
    assertThat(email.getStatus()).isEqualTo(VerificationStatusEnum.INVALIDATED);
    assertThat(cancellation.getStatus()).isEqualTo(VerificationStatusEnum.INVALIDATED);
  }

  /**
   * Inspeciona a prova aberta sem consumi-la para permitir aceite legal complementar.
   */
  @Test
  void inspect_shouldKeepProofOpen_whenActivationStillNeedsLegalConsent() {
    String token = tokenService.generate();
    VerificationEntity verification = verification(
        registration,
        VerificationPurposeEnum.REGISTRATION_EMAIL,
        token,
        ISSUED_AT);
    when(repository.findRegistrationIdByTokenHash(any(byte[].class)))
        .thenReturn(Optional.of(51L));
    when(repository.findByTokenHashForUpdate(any(byte[].class)))
        .thenReturn(Optional.of(verification));

    VerificationInspectionVO result = service.inspect(
        VerificationPurposeEnum.REGISTRATION_EMAIL,
        token,
        ISSUED_AT.plusSeconds(30));

    assertThat(result.status()).isEqualTo(VerificationConsumptionStatusEnum.VERIFIED);
    assertThat(result.registration()).isSameAs(registration);
    assertThat(verification.getStatus()).isEqualTo(VerificationStatusEnum.OPEN);
  }

  private RegistrationEntity registration(Long id) {
    UserEntity user = new UserEntity(
        "user@example.com",
        "user@example.com",
        UserStatusEnum.PENDING_VERIFICATION);
    RegistrationEntity entity = new RegistrationEntity(
        user,
        RegistrationMethodEnum.LOCAL,
        RegistrationStatusEnum.PENDING_VERIFICATION,
        ISSUED_AT.plus(Duration.ofDays(15)));
    ReflectionTestUtils.setField(entity, "id", id);
    return entity;
  }

  private VerificationEntity verification(
      RegistrationEntity owner,
      VerificationPurposeEnum purpose,
      String token,
      Instant issuedAt) {
    return new VerificationEntity(
        owner,
        purpose,
        tokenService.hash(token),
        issuedAt,
        issuedAt.plus(VALIDITY));
  }
}
