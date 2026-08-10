package br.com.rinos.app.backend.module.identity.facade;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import br.com.rinos.app.api.dto.ExternalIdentityLinkRequestDTO;
import br.com.rinos.app.api.dto.ExternalIdentityManagementContextDTO;
import br.com.rinos.app.api.dto.ExternalIdentityUnlinkRequestDTO;
import br.com.rinos.app.api.enums.ExternalIdentityManagementStatusEnum;
import br.com.rinos.app.backend.module.identity.enums.ExternalIdentityOperationStatusEnum;
import br.com.rinos.app.backend.module.identity.service.ExternalIdentityManagementService;

@DisplayName("Fachada de gestão de identidades externas")
class ExternalIdentityManagementFacadeImplTest {

  private static final Instant NOW = Instant.parse("2026-08-10T22:00:00Z");
  private static final String SESSION = "4b0b0c02-6245-469a-a27b-f997be1840cc";
  private ExternalIdentityManagementService service;
  private ExternalIdentityManagementFacadeImpl facade;

  @BeforeEach
  void setUp() {
    service = mock(ExternalIdentityManagementService.class);
    facade = new ExternalIdentityManagementFacadeImpl(service);
  }

  @Test
  void link_shouldTranslateDatabaseUniquenessRaceToSafeConflict() {
    when(service.link(any(), any(), any(), any(), any(),
        org.mockito.ArgumentMatchers.anyBoolean(), any(), any()))
        .thenThrow(new DataIntegrityViolationException("unique issuer subject"));

    assertThat(facade.link(linkRequest()).status())
        .isEqualTo(ExternalIdentityManagementStatusEnum.CONFLICT);
  }

  @Test
  void unlink_shouldRejectMalformedOpaqueReferenceWithoutCallingService() {
    ExternalIdentityUnlinkRequestDTO request = new ExternalIdentityUnlinkRequestDTO(
        context(), "internal-id-41", UUID.randomUUID());

    assertThat(facade.unlink(request).status())
        .isEqualTo(ExternalIdentityManagementStatusEnum.REJECTED);
  }

  @Test
  void unlink_shouldPreserveLastMethodOutcome() {
    when(service.unlink(any(), any(), any(), any(), any()))
        .thenReturn(ExternalIdentityOperationStatusEnum.LAST_METHOD);
    ExternalIdentityUnlinkRequestDTO request = new ExternalIdentityUnlinkRequestDTO(
        context(), UUID.randomUUID().toString(), UUID.randomUUID());

    assertThat(facade.unlink(request).status())
        .isEqualTo(ExternalIdentityManagementStatusEnum.LAST_METHOD);
  }

  private static ExternalIdentityLinkRequestDTO linkRequest() {
    return new ExternalIdentityLinkRequestDTO(
        context(),
        "google",
        "https://accounts.google.com",
        "subject",
        true,
        UUID.randomUUID());
  }

  private static ExternalIdentityManagementContextDTO context() {
    return new ExternalIdentityManagementContextDTO(41L, SESSION, NOW);
  }
}
