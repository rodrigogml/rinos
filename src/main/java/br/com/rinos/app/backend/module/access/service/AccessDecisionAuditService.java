package br.com.rinos.app.backend.module.access.service;

import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import br.com.rinos.app.api.module.access.dto.AuthorizationRequest;
import br.com.rinos.app.api.module.access.enums.AuthorizationActorType;
import br.com.rinos.app.api.module.access.vo.AuthorizationDecision;
import br.com.rinos.app.backend.module.access.entity.AccessAuditEventEntity;
import br.com.rinos.app.backend.module.access.repository.AccessAuditEventRepository;

/** Registra negações sensíveis com dados minimizados e transação independente. */
@Service
@org.springframework.context.annotation.Lazy
public class AccessDecisionAuditService {

  private final AccessAuditEventRepository audits;

  public AccessDecisionAuditService(AccessAuditEventRepository audits) {
    this.audits = audits;
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void recordDeniedIfSensitive(
      AuthorizationRequest request, AuthorizationDecision decision) {
    if (!request.sensitive() || decision.allowed()) {
      return;
    }
    boolean human = request.actor().type() == AuthorizationActorType.HUMAN;
    String reason = decision.safeReasonCodes().stream().sorted().findFirst()
        .orElse("ACL_DENIED");
    audits.save(new AccessAuditEventEntity(
        "SENSITIVE_AUTHORIZATION_DENIED", request.context().scope(),
        request.context().tenantId(), human ? request.actor().identityId() : null,
        human ? null : request.actor().systemOrigin(), "AUTHORIZATION_ACTOR",
        human ? request.actor().identityId() : 0L, decision.correlationId().toString(),
        reason, details(request), Instant.now()));
  }

  private String details(AuthorizationRequest request) {
    return "{\"operationCode\":\"" + escapeJson(request.operationCode())
        + "\",\"requiredKeyCount\":" + request.requiredKeys().size() + "}";
  }

  private static String escapeJson(String value) {
    StringBuilder escaped = new StringBuilder(value.length());
    for (int index = 0; index < value.length(); index++) {
      char character = value.charAt(index);
      switch (character) {
        case '\"' -> escaped.append("\\\"");
        case '\\' -> escaped.append("\\\\");
        case '\b' -> escaped.append("\\b");
        case '\f' -> escaped.append("\\f");
        case '\n' -> escaped.append("\\n");
        case '\r' -> escaped.append("\\r");
        case '\t' -> escaped.append("\\t");
        default -> {
          if (character < 0x20) {
            escaped.append(String.format("\\u%04x", (int) character));
          } else {
            escaped.append(character);
          }
        }
      }
    }
    return escaped.toString();
  }
}
