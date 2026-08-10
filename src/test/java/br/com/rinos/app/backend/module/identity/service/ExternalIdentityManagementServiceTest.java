package br.com.rinos.app.backend.module.identity.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import br.com.rinos.app.backend.module.identity.entity.ExternalIdentityEntity;
import br.com.rinos.app.backend.module.identity.entity.UserEntity;
import br.com.rinos.app.backend.module.identity.enums.ExternalIdentityOperationStatusEnum;
import br.com.rinos.app.backend.module.identity.enums.ExternalIdentityProviderEnum;
import br.com.rinos.app.backend.module.identity.enums.ExternalIdentityStatusEnum;
import br.com.rinos.app.backend.module.identity.enums.UserStatusEnum;
import br.com.rinos.app.backend.module.identity.repository.ExternalIdentityRepository;
import br.com.rinos.app.backend.module.identity.repository.UserRepository;
import br.com.rinos.app.backend.module.identity.vo.AuthenticationMethodInventoryVO;

@DisplayName("Gestão transacional de identidades externas")
class ExternalIdentityManagementServiceTest {

  private static final Instant NOW = Instant.parse("2026-08-10T21:00:00Z");
  private static final UUID SESSION = UUID.fromString("6c70506f-3123-4403-8b33-855ca039e630");
  private static final UUID CORRELATION = UUID.fromString("23d28781-2a82-4027-8380-e50dcf333291");
  private UserRepository users;
  private ExternalIdentityRepository identities;
  private ReauthenticationService reauthentication;
  private AuthenticationMethodInventoryService inventory;
  private IdentityAuditService audit;
  private IdentityReferenceService references;
  private ExternalIdentityManagementService service;
  private UserEntity user;

  @BeforeEach
  void setUp() {
    users = mock(UserRepository.class);
    identities = mock(ExternalIdentityRepository.class);
    reauthentication = mock(ReauthenticationService.class);
    inventory = mock(AuthenticationMethodInventoryService.class);
    audit = mock(IdentityAuditService.class);
    references = new IdentityReferenceService();
    user = user(41L, "owner@example.test");
    when(users.findByIdForUpdate(41L)).thenReturn(Optional.of(user));
    when(reauthentication.isRecentlyAuthorized(any(), any(), any(), any())).thenReturn(true);
    service = new ExternalIdentityManagementService(
        users,
        identities,
        mock(AuthSessionService.class),
        reauthentication,
        inventory,
        references,
        audit);
  }

  @Test
  void link_shouldPersistOnlyStableKeys_afterConfirmationAndRecentAuthentication() {
    when(identities.findByIssuerAndSubjectForUpdate(
        "https://accounts.google.com", "google-subject")).thenReturn(Optional.empty());
    when(identities.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

    ExternalIdentityOperationStatusEnum status = service.link(
        41L,
        SESSION,
        ExternalIdentityProviderEnum.GOOGLE,
        "https://accounts.google.com",
        "google-subject",
        true,
        CORRELATION,
        NOW);

    assertThat(status).isEqualTo(ExternalIdentityOperationStatusEnum.LINKED);
    org.mockito.ArgumentCaptor<ExternalIdentityEntity> captured =
        org.mockito.ArgumentCaptor.forClass(ExternalIdentityEntity.class);
    verify(identities).saveAndFlush(captured.capture());
    assertThat(captured.getValue().getStatus()).isEqualTo(ExternalIdentityStatusEnum.ACTIVE);
    assertThat(captured.getValue().getReference()).isNotNull();
    assertThat(captured.getValue().getIssuer()).isEqualTo("https://accounts.google.com");
    assertThat(captured.getValue().getSubject()).isEqualTo("google-subject");
  }

  @Test
  void link_shouldRejectMissingExplicitConfirmation_withoutChangingPersistence() {
    ExternalIdentityOperationStatusEnum status = service.link(
        41L,
        SESSION,
        ExternalIdentityProviderEnum.GOOGLE,
        "https://accounts.google.com",
        "google-subject",
        false,
        CORRELATION,
        NOW);

    assertThat(status).isEqualTo(ExternalIdentityOperationStatusEnum.REJECTED);
    verify(reauthentication, never()).isRecentlyAuthorized(any(), any(), any(), any());
    verify(identities, never()).saveAndFlush(any());
  }

  @Test
  void link_shouldReportConflict_withoutRevealingDifferentOwner() {
    UserEntity differentOwner = user(99L, "different@example.test");
    ExternalIdentityEntity existing = activeIdentity(differentOwner);
    when(identities.findByIssuerAndSubjectForUpdate(
        "https://accounts.google.com", "google-subject")).thenReturn(Optional.of(existing));

    ExternalIdentityOperationStatusEnum status = service.link(
        41L,
        SESSION,
        ExternalIdentityProviderEnum.GOOGLE,
        "https://accounts.google.com",
        "google-subject",
        true,
        CORRELATION,
        NOW);

    assertThat(status).isEqualTo(ExternalIdentityOperationStatusEnum.CONFLICT);
    verify(identities, never()).saveAndFlush(any());
  }

  @Test
  void unlink_shouldProtectLastMethodAndAllowRemovalWhenAnotherIdentityRemains() {
    UUID reference = UUID.fromString("0f954946-8152-49a8-8eef-dc91b68b2649");
    ExternalIdentityEntity identity = activeIdentity(user, reference);
    when(identities.findByUserIdAndReferenceForUpdate(
        org.mockito.ArgumentMatchers.eq(41L),
        org.mockito.ArgumentMatchers.any(byte[].class))).thenReturn(Optional.of(identity));
    when(inventory.inspect(41L)).thenReturn(
        new AuthenticationMethodInventoryVO(false, 1, 0, 0, false, 0),
        new AuthenticationMethodInventoryVO(false, 2, 0, 0, false, 0));

    assertThat(service.unlink(41L, SESSION, reference, CORRELATION, NOW))
        .isEqualTo(ExternalIdentityOperationStatusEnum.LAST_METHOD);
    assertThat(identity.getStatus()).isEqualTo(ExternalIdentityStatusEnum.ACTIVE);

    assertThat(service.unlink(41L, SESSION, reference, CORRELATION, NOW.plusSeconds(1)))
        .isEqualTo(ExternalIdentityOperationStatusEnum.UNLINKED);
    assertThat(identity.getStatus()).isEqualTo(ExternalIdentityStatusEnum.REVOKED);
    assertThat(identity.getRevokedAt()).isEqualTo(NOW.plusSeconds(1));
  }

  private static ExternalIdentityEntity activeIdentity(UserEntity owner) {
    return activeIdentity(owner, UUID.randomUUID());
  }

  private static ExternalIdentityEntity activeIdentity(UserEntity owner, UUID reference) {
    ExternalIdentityEntity identity = new ExternalIdentityEntity(
        owner,
        reference,
        ExternalIdentityProviderEnum.GOOGLE,
        "https://accounts.google.com",
        "google-subject",
        NOW.minusSeconds(60));
    identity.setStatus(ExternalIdentityStatusEnum.ACTIVE);
    identity.setActivatedAt(NOW.minusSeconds(60));
    return identity;
  }

  private static UserEntity user(long id, String email) {
    UserEntity value = new UserEntity(email, email, UserStatusEnum.ACTIVE);
    ReflectionTestUtils.setField(value, "id", id);
    return value;
  }
}
