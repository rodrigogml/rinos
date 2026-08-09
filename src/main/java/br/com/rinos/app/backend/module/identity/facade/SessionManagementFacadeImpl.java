package br.com.rinos.app.backend.module.identity.facade;

import java.security.MessageDigest;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import br.com.rinos.app.api.dto.SessionBulkRevocationRequestDTO;
import br.com.rinos.app.api.dto.SessionManagementContextDTO;
import br.com.rinos.app.api.dto.SessionRevocationRequestDTO;
import br.com.rinos.app.api.facade.SessionManagementFacade;
import br.com.rinos.app.api.vo.AuthenticatedSessionVO;
import br.com.rinos.app.api.vo.SessionRevocationResultVO;
import br.com.rinos.app.backend.module.identity.service.AuthSessionService;
import br.com.rinos.app.backend.module.identity.vo.AuthSessionRevocationVO;
import br.com.rinos.app.backend.module.identity.vo.AuthSessionSummaryVO;

/**
 * Aplica escopo obrigatório da sessão corrente à gestão persistente cross-instance.
 *
 * @author Rodrigo Leitão
 * @since 2026-08-09
 */
@Service
@Lazy
public class SessionManagementFacadeImpl implements SessionManagementFacade {

  private final AuthSessionService sessionService;

  /** @param sessionService autoridade transacional das sessões globais */
  public SessionManagementFacadeImpl(AuthSessionService sessionService) {
    this.sessionService = sessionService;
  }

  @Override
  public List<AuthenticatedSessionVO> list(SessionManagementContextDTO context) {
    Objects.requireNonNull(context, "context must not be null");
    UUID current = requireReference(context.currentSessionReference(), "currentSessionReference");
    return sessionService.listManaged(context.userId(), current, context.occurredAt()).stream()
        .map(session -> publicView(session, current))
        .toList();
  }

  @Override
  public SessionRevocationResultVO revoke(SessionRevocationRequestDTO request) {
    Objects.requireNonNull(request, "request must not be null");
    UUID current = requireReference(
        request.context().currentSessionReference(), "currentSessionReference");
    UUID target = requireReference(request.targetSessionReference(), "targetSessionReference");
    return publicView(sessionService.revokeManaged(
        request.context().userId(),
        current,
        target,
        request.context().occurredAt(),
        request.correlationId()));
  }

  @Override
  public SessionRevocationResultVO revokeAll(SessionBulkRevocationRequestDTO request) {
    Objects.requireNonNull(request, "request must not be null");
    UUID current = requireReference(
        request.context().currentSessionReference(), "currentSessionReference");
    return publicView(sessionService.revokeAllManaged(
        request.context().userId(),
        current,
        request.keepCurrent(),
        request.context().occurredAt(),
        request.correlationId()));
  }

  private static AuthenticatedSessionVO publicView(
      AuthSessionSummaryVO session,
      UUID current) {
    boolean isCurrent = MessageDigest.isEqual(
        uuidBytes(session.publicReference()), uuidBytes(current));
    return new AuthenticatedSessionVO(
        session.publicReference().toString(),
        isCurrent,
        session.createdAt(),
        session.lastActivityAt(),
        session.deviceDescription(),
        null);
  }

  private static SessionRevocationResultVO publicView(AuthSessionRevocationVO result) {
    return new SessionRevocationResultVO(
        result.revokedCount(), result.currentSessionRevoked());
  }

  private static UUID requireReference(String value, String name) {
    try {
      return UUID.fromString(value);
    } catch (IllegalArgumentException invalidReference) {
      throw new IllegalArgumentException(name + " is invalid", invalidReference);
    }
  }

  private static byte[] uuidBytes(UUID value) {
    return java.nio.ByteBuffer.allocate(16)
        .putLong(value.getMostSignificantBits())
        .putLong(value.getLeastSignificantBits())
        .array();
  }
}
