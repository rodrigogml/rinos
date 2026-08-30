package br.com.rinos.app.api.module.storage.dto;

import java.time.Instant;
import java.util.Objects;

import br.com.rinos.app.api.module.access.enums.AccessScope;
import br.com.rinos.app.api.module.access.vo.HumanAuthorizationContext;

/**
 * Contexto confiável de uma ação administrativa global sobre o armazenamento de tenants.
 *
 * <p>O adaptador autenticado o deriva da sessão atual; campos desse tipo nunca são recebidos de uma rota,
 * formulário ou evento de navegador. A garantia de autenticação é mantida para que a fachada canônica possa exigir
 * reautenticação recente e fator forte.</p>
 *
 * @author Rodrigo Leitão
 * @since 2026-08-30
 */
public record TenantStorageAdministrationInvocationContext(
    HumanAuthorizationContext authorization,
    String correlationId,
    Instant occurredAt) {

  /**
   * Valida que a ação foi composta no contexto global e contém uma correlação sanitizada.
   *
   * @throws NullPointerException quando a autorização ou o instante não forem informados
   * @throws IllegalArgumentException quando o contexto não for global ou a correlação for inválida
   */
  public TenantStorageAdministrationInvocationContext {
    authorization = Objects.requireNonNull(authorization, "authorization must not be null");
    occurredAt = Objects.requireNonNull(occurredAt, "occurredAt must not be null");
    if (authorization.context().scope() != AccessScope.GLOBAL
        || authorization.membershipId() != null
        || correlationId == null || correlationId.isBlank() || correlationId.length() > 100) {
      throw new IllegalArgumentException("storage administration context is invalid");
    }
    correlationId = correlationId.strip();
  }

  @Override
  public String toString() {
    return "TenantStorageAdministrationInvocationContext[authorization=REDACTED, correlationId="
        + correlationId + ", occurredAt=" + occurredAt + "]";
  }
}
