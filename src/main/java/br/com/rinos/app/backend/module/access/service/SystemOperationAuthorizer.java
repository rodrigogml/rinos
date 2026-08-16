package br.com.rinos.app.backend.module.access.service;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import br.com.rinos.app.api.module.access.dto.AuthorizationRequest;
import br.com.rinos.app.api.module.access.enums.AccessRuleEffect;
import br.com.rinos.app.api.module.access.enums.AuthorizationActorType;
import br.com.rinos.app.api.module.access.enums.AuthorizationSourceStatus;
import br.com.rinos.app.api.module.access.enums.AuthorizationSourceType;
import br.com.rinos.app.api.module.access.vo.AuthorizationKeyResult;
import br.com.rinos.app.api.module.access.vo.AuthorizationRuleSource;
import br.com.rinos.app.backend.module.access.entity.AccessAuditEventEntity;
import br.com.rinos.app.backend.module.access.repository.AccessAuditEventRepository;

/** Resolve somente a operação e o conjunto exatos registrados para uma origem sistêmica. */
@Service
@org.springframework.context.annotation.Lazy
public class SystemOperationAuthorizer {
  private final SystemOperationRegistryService operations;
  private final AccessKeyRegistryService keys;
  private final AccessContextRevisionService revisions;
  private final AccessAuditEventRepository audits;

  public SystemOperationAuthorizer(
      SystemOperationRegistryService operations,
      AccessKeyRegistryService keys,
      AccessContextRevisionService revisions,
      AccessAuditEventRepository audits) {
    this.operations = operations;
    this.keys = keys;
    this.revisions = revisions;
    this.audits = audits;
  }

  public boolean matches(AuthorizationRequest request) {
    if (request.actor().type() != AuthorizationActorType.SYSTEM) return false;
    Set<String> requested = request.requiredKeys().stream()
        .map(value -> value.code()).collect(Collectors.toUnmodifiableSet());
    return operations.find(request.actor().systemOrigin(), request.operationCode(),
            request.context().scope())
        .filter(descriptor -> descriptor.active() && descriptor.requiredKeyCodes().equals(requested))
        .filter(descriptor -> request.requiredKeys().stream().allMatch(key ->
            keys.find(key.code()).filter(key::equals).isPresent()))
        .isPresent();
  }

  public ResolvedAccessSnapshot resolve(AuthorizationRequest request, Instant decidedAt) {
    if (!matches(request)) throw new SecurityException("ACL_SYSTEM_SOURCE_MISMATCH");
    AuthorizationRuleSource source = new AuthorizationRuleSource(
        AuthorizationSourceType.SYSTEM_SOURCE, "system:" + request.actor().systemOrigin(),
        AccessRuleEffect.PERMITIR, AuthorizationSourceStatus.CURRENT, null, null);
    List<AuthorizationKeyResult> results = request.requiredKeys().stream()
        .sorted(Comparator.comparing(value -> value.code()))
        .map(key -> new AuthorizationKeyResult(
            key, true, List.of(source), List.of(), false, List.of()))
        .toList();
    long revision = revisions.current(request.context().scope(), request.context().tenantId());
    audits.save(new AccessAuditEventEntity(
        "SYSTEM_OPERATION_AUTHORIZED", request.context().scope(), request.context().tenantId(),
        null, request.actor().systemOrigin(), "SYSTEM_OPERATION", 1L,
        UUID.randomUUID().toString(), null,
        "{\"operationCode\":" + quote(request.operationCode())
            + ",\"requiredKeyCount\":" + request.requiredKeys().size() + "}", decidedAt));
    return new ResolvedAccessSnapshot(revision, results, null);
  }

  private static String quote(String value) {
    return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
  }
}
