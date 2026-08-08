package br.com.rinos.app.backend.module.identity.service;

import java.time.Instant;
import java.util.Objects;

import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import br.com.rinos.app.backend.module.identity.vo.AuthenticationCleanupResultVO;
import br.com.rinos.app.config.AuthenticationRetentionPropertiesConfig;

/**
 * Expõe as retenções de autenticação como tarefas independentes do catálogo global.
 *
 * <p>Este coordenador não abre transação: cada serviço delegado preserva sua própria
 * fronteira, permitindo ao scheduler isolar falhas entre os tipos de artefato.
 *
 * @author Rodrigo Leitão
 * @since 2026-08-08
 */
@Service
@Lazy
public class AuthenticationArtifactCleanupService {

  private final AuthenticationFlowService flowService;
  private final AuthenticationProofService proofService;
  private final AuthSessionService sessionService;
  private final AuthenticationWindowService windowService;
  private final AuthenticationRetentionPropertiesConfig retentionProperties;

  /** Cria o coordenador sobre os serviços transacionais especializados. */
  public AuthenticationArtifactCleanupService(
      AuthenticationFlowService flowService,
      AuthenticationProofService proofService,
      AuthSessionService sessionService,
      AuthenticationWindowService windowService,
      AuthenticationRetentionPropertiesConfig retentionProperties) {
    this.flowService = flowService;
    this.proofService = proofService;
    this.sessionService = sessionService;
    this.windowService = windowService;
    this.retentionProperties = retentionProperties;
  }

  /** Expira e remove fluxos fora da retenção de artefatos temporários. */
  public AuthenticationCleanupResultVO cleanupFlows(Instant occurredAt) {
    return flowService.cleanup(occurredAt, temporaryCutoff(occurredAt));
  }

  /** Expira e remove provas fora da retenção de artefatos temporários. */
  public AuthenticationCleanupResultVO cleanupProofs(Instant occurredAt) {
    return proofService.cleanup(occurredAt, temporaryCutoff(occurredAt));
  }

  /** Expira e remove sessões conforme sua retenção exclusiva. */
  public AuthenticationCleanupResultVO cleanupSessions(Instant occurredAt) {
    return sessionService.cleanup(occurredAt);
  }

  /** Encerra e remove janelas antifraude conforme a política de abuso. */
  public AuthenticationCleanupResultVO cleanupWindows(Instant occurredAt) {
    return windowService.cleanup(occurredAt);
  }

  private Instant temporaryCutoff(Instant occurredAt) {
    Objects.requireNonNull(occurredAt, "occurredAt must not be null");
    return occurredAt.minus(retentionProperties.temporaryArtifacts());
  }
}
