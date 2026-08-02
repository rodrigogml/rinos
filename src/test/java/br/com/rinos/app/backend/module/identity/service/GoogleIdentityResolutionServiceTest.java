package br.com.rinos.app.backend.module.identity.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import br.com.rinos.app.backend.module.identity.entity.ExternalIdentityEntity;
import br.com.rinos.app.backend.module.identity.entity.RegistrationEntity;
import br.com.rinos.app.backend.module.identity.entity.UserEntity;
import br.com.rinos.app.backend.module.identity.enums.ExternalIdentityProviderEnum;
import br.com.rinos.app.backend.module.identity.enums.GoogleIdentityDomainStatusEnum;
import br.com.rinos.app.backend.module.identity.enums.RegistrationMethodEnum;
import br.com.rinos.app.backend.module.identity.enums.RegistrationStatusEnum;
import br.com.rinos.app.backend.module.identity.enums.UserStatusEnum;
import br.com.rinos.app.backend.module.identity.enums.VerificationPurposeEnum;
import br.com.rinos.app.backend.module.identity.repository.RegistrationRepository;
import br.com.rinos.app.backend.module.identity.vo.GoogleIdentityDomainResultVO;
import br.com.rinos.app.backend.module.identity.vo.IssuedVerificationVO;
import br.com.rinos.app.backend.module.identity.vo.NormalizedEmailVO;
import br.com.rinos.app.config.RegistrationPropertiesConfig;

@DisplayName("Resolução transacional da identidade Google")
class GoogleIdentityResolutionServiceTest {

  private static final Instant NOW = Instant.parse("2026-07-29T12:00:00Z");
  private static final String ISSUER = "https://accounts.google.com";
  private static final UUID CORRELATION_ID =
      UUID.fromString("5f9621c2-6ddc-4e74-bf25-331721003931");

  private IdentityService identityService;
  private EmailNormalizationService emailNormalizationService;
  private ExternalIdentityService externalIdentityService;
  private RegistrationRepository registrationRepository;
  private VerificationService verificationService;
  private IdentityAuditService auditService;
  private GoogleIdentityResolutionService service;

  @BeforeEach
  void setUp() {
    identityService = mock(IdentityService.class);
    emailNormalizationService = mock(EmailNormalizationService.class);
    externalIdentityService = mock(ExternalIdentityService.class);
    registrationRepository = mock(RegistrationRepository.class);
    verificationService = mock(VerificationService.class);
    auditService = mock(IdentityAuditService.class);
    service = new GoogleIdentityResolutionService(
        identityService,
        emailNormalizationService,
        externalIdentityService,
        registrationRepository,
        verificationService,
        auditService,
        new RegistrationPropertiesConfig(
            Duration.ofDays(15),
            3,
            Duration.ofMinutes(15),
            3,
            Duration.ofMinutes(15)));
  }

  @Test
  void resolve_shouldCreateGooglePendingIdentity_whenEmailAndLinkAreNew() {
    RegistrationEntity registration = registration(
        user("person@example.com", UserStatusEnum.PENDING_VERIFICATION),
        RegistrationMethodEnum.GOOGLE);
    when(externalIdentityService.findForUpdate(ISSUER, "subject-1"))
        .thenReturn(Optional.empty());
    when(identityService.findByEmailForUpdate("person@example.com"))
        .thenReturn(Optional.empty());
    when(identityService.createPendingIdentity(
        "person@example.com",
        RegistrationMethodEnum.GOOGLE,
        NOW.plus(Duration.ofDays(15))))
        .thenReturn(registration);
    when(verificationService.issue(
        registration,
        VerificationPurposeEnum.EXTERNAL_REGISTRATION,
        NOW))
        .thenReturn(new IssuedVerificationVO(
            1L,
            "opaque-reference",
            NOW.plusSeconds(600)));

    GoogleIdentityDomainResultVO result = service.resolve(
        ISSUER,
        "subject-1",
        "person@example.com",
        CORRELATION_ID,
        NOW);

    assertThat(result.status())
        .isEqualTo(GoogleIdentityDomainStatusEnum.CONTINUATION_REQUIRED);
    assertThat(result.continuationToken()).isEqualTo("opaque-reference");
    verify(externalIdentityService).createPending(
        registration.getUser(),
        ExternalIdentityProviderEnum.GOOGLE,
        ISSUER,
        "subject-1",
        NOW);
  }

  @Test
  void resolve_shouldReusePendingUserWithoutRemovingCredential_whenEmailExists() {
    UserEntity user = user("person@example.com", UserStatusEnum.PENDING_VERIFICATION);
    RegistrationEntity registration = registration(user, RegistrationMethodEnum.LOCAL);
    when(externalIdentityService.findForUpdate(ISSUER, "subject-1"))
        .thenReturn(Optional.empty());
    when(identityService.findByEmailForUpdate("person@example.com"))
        .thenReturn(Optional.of(user));
    when(registrationRepository.findByUserIdAndStatus(
        10L,
        RegistrationStatusEnum.PENDING_VERIFICATION))
        .thenReturn(Optional.of(registration));
    when(registrationRepository.findByIdForUpdate(20L))
        .thenReturn(Optional.of(registration));
    when(verificationService.issue(
        registration,
        VerificationPurposeEnum.EXTERNAL_REGISTRATION,
        NOW))
        .thenReturn(new IssuedVerificationVO(
            1L,
            "opaque-reference",
            NOW.plusSeconds(600)));

    GoogleIdentityDomainResultVO result = service.resolve(
        ISSUER,
        "subject-1",
        "person@example.com",
        CORRELATION_ID,
        NOW);

    assertThat(result.status())
        .isEqualTo(GoogleIdentityDomainStatusEnum.CONTINUATION_REQUIRED);
    verify(externalIdentityService).replacePending(
        user,
        ExternalIdentityProviderEnum.GOOGLE,
        ISSUER,
        "subject-1",
        NOW);
  }

  @Test
  void resolve_shouldRequireReauthenticationWithoutCreatingLink_whenEmailBelongsToActiveUser() {
    UserEntity user = user("person@example.com", UserStatusEnum.ACTIVE);
    when(externalIdentityService.findForUpdate(ISSUER, "subject-1"))
        .thenReturn(Optional.empty());
    when(identityService.findByEmailForUpdate("person@example.com"))
        .thenReturn(Optional.of(user));

    GoogleIdentityDomainResultVO result = service.resolve(
        ISSUER,
        "subject-1",
        "person@example.com",
        CORRELATION_ID,
        NOW);

    assertThat(result.status()).isEqualTo(
        GoogleIdentityDomainStatusEnum.EXISTING_USER_REAUTHENTICATION_REQUIRED);
    verify(externalIdentityService, never()).createPending(
        any(),
        any(),
        any(),
        any(),
        any());
  }

  @Test
  void resolve_shouldRejectConflict_whenStableLinkEmailDiffers() {
    UserEntity linkedUser = user("old@example.com", UserStatusEnum.PENDING_VERIFICATION);
    ExternalIdentityEntity identity = new ExternalIdentityEntity(
        linkedUser,
        ExternalIdentityProviderEnum.GOOGLE,
        ISSUER,
        "subject-1",
        NOW.minusSeconds(60));
    when(externalIdentityService.findForUpdate(ISSUER, "subject-1"))
        .thenReturn(Optional.of(identity));
    when(emailNormalizationService.normalize("new@example.com"))
        .thenReturn(new NormalizedEmailVO("new@example.com", "new@example.com"));

    GoogleIdentityDomainResultVO result = service.resolve(
        ISSUER,
        "subject-1",
        "new@example.com",
        CORRELATION_ID,
        NOW);

    assertThat(result.status())
        .isEqualTo(GoogleIdentityDomainStatusEnum.EXTERNAL_IDENTITY_CONFLICT);
    verify(verificationService, never()).issue(any(), any(), any());
  }

  @Test
  void resolve_shouldReissueContinuation_whenStablePendingLinkMatches() {
    UserEntity linkedUser = user("person@example.com", UserStatusEnum.PENDING_VERIFICATION);
    RegistrationEntity registration = registration(linkedUser, RegistrationMethodEnum.GOOGLE);
    ExternalIdentityEntity identity = new ExternalIdentityEntity(
        linkedUser,
        ExternalIdentityProviderEnum.GOOGLE,
        ISSUER,
        "subject-1",
        NOW.minusSeconds(60));
    when(externalIdentityService.findForUpdate(ISSUER, "subject-1"))
        .thenReturn(Optional.of(identity));
    when(emailNormalizationService.normalize("person@example.com"))
        .thenReturn(new NormalizedEmailVO("person@example.com", "person@example.com"));
    when(registrationRepository.findByUserIdAndStatus(
        10L,
        RegistrationStatusEnum.PENDING_VERIFICATION))
        .thenReturn(Optional.of(registration));
    when(registrationRepository.findByIdForUpdate(20L))
        .thenReturn(Optional.of(registration));
    when(verificationService.issue(
        registration,
        VerificationPurposeEnum.EXTERNAL_REGISTRATION,
        NOW))
        .thenReturn(new IssuedVerificationVO(
            1L,
            "new-reference",
            NOW.plusSeconds(600)));

    GoogleIdentityDomainResultVO result = service.resolve(
        ISSUER,
        "subject-1",
        "person@example.com",
        CORRELATION_ID,
        NOW);

    assertThat(result.continuationToken()).isEqualTo("new-reference");
    verify(externalIdentityService, never()).createPending(
        any(),
        any(),
        any(),
        any(),
        any());
    verify(auditService).record(
        eq(linkedUser),
        eq(registration),
        eq(CORRELATION_ID),
        any(),
        any(),
        any(),
        any(),
        eq(GoogleIdentityDomainStatusEnum.CONTINUATION_REQUIRED.name()),
        eq(NOW));
  }

  @Test
  void resolve_shouldRequireReauthentication_whenStableLinkIsAlreadyActive() {
    UserEntity linkedUser = user("person@example.com", UserStatusEnum.ACTIVE);
    ExternalIdentityEntity identity = new ExternalIdentityEntity(
        linkedUser,
        ExternalIdentityProviderEnum.GOOGLE,
        ISSUER,
        "subject-1",
        NOW.minusSeconds(60));
    when(externalIdentityService.findForUpdate(ISSUER, "subject-1"))
        .thenReturn(Optional.of(identity));
    when(emailNormalizationService.normalize("person@example.com"))
        .thenReturn(new NormalizedEmailVO("person@example.com", "person@example.com"));

    GoogleIdentityDomainResultVO result = service.resolve(
        ISSUER,
        "subject-1",
        "person@example.com",
        CORRELATION_ID,
        NOW);

    assertThat(result.status()).isEqualTo(
        GoogleIdentityDomainStatusEnum.EXISTING_USER_REAUTHENTICATION_REQUIRED);
    verify(verificationService, never()).issue(any(), any(), any());
  }

  @Test
  void resolve_shouldRejectConflict_whenPendingRegistrationHasExpired() {
    UserEntity user = user("person@example.com", UserStatusEnum.PENDING_VERIFICATION);
    RegistrationEntity expired = new RegistrationEntity(
        user,
        RegistrationMethodEnum.LOCAL,
        RegistrationStatusEnum.PENDING_VERIFICATION,
        NOW);
    ReflectionTestUtils.setField(expired, "id", 20L);
    when(externalIdentityService.findForUpdate(ISSUER, "subject-1"))
        .thenReturn(Optional.empty());
    when(identityService.findByEmailForUpdate("person@example.com"))
        .thenReturn(Optional.of(user));
    when(registrationRepository.findByUserIdAndStatus(
        10L,
        RegistrationStatusEnum.PENDING_VERIFICATION))
        .thenReturn(Optional.of(expired));
    when(registrationRepository.findByIdForUpdate(20L))
        .thenReturn(Optional.of(expired));

    GoogleIdentityDomainResultVO result = service.resolve(
        ISSUER,
        "subject-1",
        "person@example.com",
        CORRELATION_ID,
        NOW);

    assertThat(result.status())
        .isEqualTo(GoogleIdentityDomainStatusEnum.EXTERNAL_IDENTITY_CONFLICT);
    verify(externalIdentityService, never()).createPending(
        any(),
        any(),
        any(),
        any(),
        any());
  }

  private static UserEntity user(String email, UserStatusEnum status) {
    UserEntity user = new UserEntity(email, email.toLowerCase(), status);
    ReflectionTestUtils.setField(user, "id", 10L);
    return user;
  }

  private static RegistrationEntity registration(
      UserEntity user,
      RegistrationMethodEnum method) {
    RegistrationEntity registration = new RegistrationEntity(
        user,
        method,
        RegistrationStatusEnum.PENDING_VERIFICATION,
        NOW.plus(Duration.ofDays(15)));
    ReflectionTestUtils.setField(registration, "id", 20L);
    return registration;
  }
}
