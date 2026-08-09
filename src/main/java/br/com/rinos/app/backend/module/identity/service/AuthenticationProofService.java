package br.com.rinos.app.backend.module.identity.service;

import java.security.MessageDigest;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.rinos.app.backend.module.identity.entity.AuthenticationFlowEntity;
import br.com.rinos.app.backend.module.identity.entity.AuthenticationProofEntity;
import br.com.rinos.app.backend.module.identity.enums.AuthenticationFlowPurposeEnum;
import br.com.rinos.app.backend.module.identity.enums.AuthenticationFlowStatusEnum;
import br.com.rinos.app.backend.module.identity.enums.AuthenticationOperationStatusEnum;
import br.com.rinos.app.backend.module.identity.enums.AuthenticationProofStatusEnum;
import br.com.rinos.app.backend.module.identity.enums.AuthenticationProofTypeEnum;
import br.com.rinos.app.backend.module.identity.enums.IdentityEventTypeEnum;
import br.com.rinos.app.backend.module.identity.enums.IdentityTransitionOriginEnum;
import br.com.rinos.app.backend.module.identity.repository.AuthenticationFlowRepository;
import br.com.rinos.app.backend.module.identity.repository.AuthenticationProofRepository;
import br.com.rinos.app.backend.module.identity.vo.AuthenticationCleanupResultVO;
import br.com.rinos.app.backend.module.identity.vo.AuthenticationProofInspectionVO;
import br.eng.rodrigogml.rfw.authentication.service.RFWOpaqueTokenService;

/**
 * Mantém o ciclo de provas protegidas sob a ordem de lock fluxo → prova.
 *
 * <p>O serviço recebe somente digests previamente produzidos pelo verificador específico do
 * método; valores brutos de OTP, recuperação ou aceite não pertencem a esta fronteira.
 *
 * @author Rodrigo Leitão
 * @since 2026-08-08
 */
@Service
@Lazy
public class AuthenticationProofService {

  private final AuthenticationFlowRepository flowRepository;
  private final AuthenticationProofRepository proofRepository;
  private final RFWOpaqueTokenService opaqueTokenService;
  private final IdentityAuditService auditService;

  public AuthenticationProofService(
      AuthenticationFlowRepository flowRepository,
      AuthenticationProofRepository proofRepository,
      RFWOpaqueTokenService opaqueTokenService,
      IdentityAuditService auditService) {
    this.flowRepository = flowRepository;
    this.proofRepository = proofRepository;
    this.opaqueTokenService = opaqueTokenService;
    this.auditService = auditService;
  }

  /** Substitui atomicamente a prova aberta do mesmo tipo e persiste somente o digest. */
  @Transactional
  public AuthenticationProofInspectionVO issue(
      String flowReference,
      AuthenticationFlowPurposeEnum expectedPurpose,
      AuthenticationProofTypeEnum type,
      byte[] proofDigest,
      String keyVersion,
      Instant issuedAt,
      Instant expiresAt) {
    Objects.requireNonNull(type, "type must not be null");
    Objects.requireNonNull(issuedAt, "issuedAt must not be null");
    Objects.requireNonNull(expiresAt, "expiresAt must not be null");
    AuthenticationFlowEntity flow = requireOpenFlow(flowReference, expectedPurpose, issuedAt);
    if (flow == null || expiresAt.isAfter(flow.getExpiresAt())) {
      return AuthenticationProofInspectionVO.rejected();
    }
    proofRepository.findByFlowIdAndTypeAndStatusForUpdate(
        flow.getId(),
        type,
        AuthenticationProofStatusEnum.OPEN)
        .ifPresent(proof -> proof.invalidate(issuedAt));
    AuthenticationProofEntity proof = proofRepository.saveAndFlush(new AuthenticationProofEntity(
        flow,
        type,
        proofDigest,
        keyVersion,
        issuedAt,
        expiresAt));
    auditService.record(
        flow.getUser(),
        null,
        flow.getCorrelationId(),
        IdentityEventTypeEnum.AUTHENTICATION_CHALLENGE_ISSUED,
        null,
        null,
        IdentityTransitionOriginEnum.SYSTEM,
        type.name(),
        issuedAt);
    return view(proof, AuthenticationOperationStatusEnum.OPEN);
  }

  /** Inspeciona a prova mais recente sem receber ou comparar seu conteúdo. */
  @Transactional
  public AuthenticationProofInspectionVO inspect(
      String flowReference,
      AuthenticationFlowPurposeEnum expectedPurpose,
      AuthenticationProofTypeEnum type,
      Instant occurredAt) {
    AuthenticationFlowEntity flow = requireOpenFlow(flowReference, expectedPurpose, occurredAt);
    if (flow == null || type == null) {
      return AuthenticationProofInspectionVO.rejected();
    }
    AuthenticationProofEntity proof = proofRepository
        .findFirstByFlowIdAndTypeOrderByIssuedAtDesc(flow.getId(), type)
        .orElse(null);
    return classify(proof, occurredAt);
  }

  /** Compara em tempo constante e consome uma prova aberta exatamente uma vez. */
  @Transactional
  public AuthenticationProofInspectionVO consume(
      String flowReference,
      AuthenticationFlowPurposeEnum expectedPurpose,
      AuthenticationProofTypeEnum type,
      byte[] candidateDigest,
      Instant occurredAt) {
    Objects.requireNonNull(candidateDigest, "candidateDigest must not be null");
    AuthenticationFlowEntity flow = requireOpenFlow(flowReference, expectedPurpose, occurredAt);
    if (flow == null || type == null) {
      return AuthenticationProofInspectionVO.rejected();
    }
    AuthenticationProofEntity proof = proofRepository
        .findFirstByFlowIdAndTypeOrderByIssuedAtDesc(flow.getId(), type)
        .orElse(null);
    AuthenticationProofInspectionVO current = classify(proof, occurredAt);
    if (current.status() != AuthenticationOperationStatusEnum.OPEN) {
      return current;
    }
    if (!MessageDigest.isEqual(candidateDigest, proof.getProofDigest())) {
      proof.registerAttempt();
      flow.registerFailure();
      auditService.record(
          flow.getUser(),
          null,
          flow.getCorrelationId(),
          IdentityEventTypeEnum.AUTHENTICATION_ATTEMPTED,
          null,
          null,
          IdentityTransitionOriginEnum.SELF_SERVICE,
          "PROOF_REJECTED",
          occurredAt);
      return AuthenticationProofInspectionVO.rejected();
    }
    proof.markUsed(occurredAt);
    auditService.record(
        flow.getUser(),
        null,
        flow.getCorrelationId(),
        IdentityEventTypeEnum.AUTHENTICATION_CHALLENGE_CONSUMED,
        null,
        null,
        IdentityTransitionOriginEnum.SELF_SERVICE,
        type.name(),
        occurredAt);
    return view(proof, AuthenticationOperationStatusEnum.USED);
  }

  /**
   * Consome uma prova cujo requisito de domínio já foi validado na mesma transação.
   *
   * <p>Esta operação não compara material apresentado pelo usuário. Ela se destina a marcadores
   * de continuação, como o gate legal, cuja validade é comprovada pela reconsulta da autoridade
   * correspondente antes desta chamada.
   */
  @Transactional
  public AuthenticationProofInspectionVO consumeValidated(
      String flowReference,
      AuthenticationFlowPurposeEnum expectedPurpose,
      AuthenticationProofTypeEnum type,
      Instant occurredAt) {
    AuthenticationFlowEntity flow = requireOpenFlow(flowReference, expectedPurpose, occurredAt);
    if (flow == null || type == null) {
      return AuthenticationProofInspectionVO.rejected();
    }
    AuthenticationProofEntity proof = proofRepository
        .findFirstByFlowIdAndTypeOrderByIssuedAtDesc(flow.getId(), type)
        .orElse(null);
    AuthenticationProofInspectionVO current = classify(proof, occurredAt);
    if (current.status() != AuthenticationOperationStatusEnum.OPEN) {
      return current;
    }
    proof.markUsed(occurredAt);
    auditService.record(
        flow.getUser(),
        null,
        flow.getCorrelationId(),
        IdentityEventTypeEnum.AUTHENTICATION_CHALLENGE_CONSUMED,
        null,
        null,
        IdentityTransitionOriginEnum.SELF_SERVICE,
        type.name(),
        occurredAt);
    return view(proof, AuthenticationOperationStatusEnum.USED);
  }

  /** Invalida a prova aberta mais recente do tipo informado. */
  @Transactional
  public AuthenticationProofInspectionVO cancel(
      String flowReference,
      AuthenticationFlowPurposeEnum expectedPurpose,
      AuthenticationProofTypeEnum type,
      Instant occurredAt) {
    AuthenticationFlowEntity flow = requireOpenFlow(flowReference, expectedPurpose, occurredAt);
    if (flow == null || type == null) {
      return AuthenticationProofInspectionVO.rejected();
    }
    AuthenticationProofEntity proof = proofRepository
        .findFirstByFlowIdAndTypeOrderByIssuedAtDesc(flow.getId(), type)
        .orElse(null);
    AuthenticationProofInspectionVO current = classify(proof, occurredAt);
    if (current.status() != AuthenticationOperationStatusEnum.OPEN) {
      return current;
    }
    proof.invalidate(occurredAt);
    return view(proof, AuthenticationOperationStatusEnum.INVALIDATED);
  }

  /** Expira provas sob o lock de seus fluxos e remove histórico fora da retenção. */
  @Transactional
  public AuthenticationCleanupResultVO cleanup(Instant occurredAt, Instant retentionCutoff) {
    Objects.requireNonNull(occurredAt, "occurredAt must not be null");
    Objects.requireNonNull(retentionCutoff, "retentionCutoff must not be null");
    if (retentionCutoff.isAfter(occurredAt)) {
      throw new IllegalArgumentException("retentionCutoff must not be after occurredAt");
    }
    int expired = 0;
    List<Long> flowIds = proofRepository.findFlowIdsWithExpiredProofs(
        AuthenticationProofStatusEnum.OPEN,
        occurredAt);
    for (Long flowId : flowIds) {
      AuthenticationFlowEntity flow = flowRepository.findByIdForUpdate(flowId).orElse(null);
      if (flow == null) {
        continue;
      }
      List<AuthenticationProofEntity> proofs = proofRepository.findByFlowIdAndStatusForUpdate(
          flowId,
          AuthenticationProofStatusEnum.OPEN);
      for (AuthenticationProofEntity proof : proofs) {
        if (!occurredAt.isBefore(proof.getExpiresAt())) {
          proof.expire(occurredAt);
          expired++;
        }
      }
    }
    proofRepository.flush();
    int deleted = proofRepository.deleteTerminalBefore(
        AuthenticationProofStatusEnum.OPEN,
        retentionCutoff);
    return new AuthenticationCleanupResultVO(expired, deleted);
  }

  private AuthenticationFlowEntity requireOpenFlow(
      String reference,
      AuthenticationFlowPurposeEnum expectedPurpose,
      Instant occurredAt) {
    Objects.requireNonNull(occurredAt, "occurredAt must not be null");
    if (reference == null || reference.isBlank() || expectedPurpose == null) {
      return null;
    }
    AuthenticationFlowEntity flow = flowRepository
        .findByReferenceHashForUpdate(opaqueTokenService.hash(reference))
        .orElse(null);
    if (flow == null || flow.getPurpose() != expectedPurpose
        || flow.getStatus() != AuthenticationFlowStatusEnum.OPEN) {
      return null;
    }
    if (!occurredAt.isBefore(flow.getExpiresAt())) {
      flow.expire(occurredAt);
      return null;
    }
    return flow;
  }

  private AuthenticationProofInspectionVO classify(
      AuthenticationProofEntity proof,
      Instant occurredAt) {
    if (proof == null) {
      return AuthenticationProofInspectionVO.rejected();
    }
    if (proof.getStatus() == AuthenticationProofStatusEnum.OPEN
        && !occurredAt.isBefore(proof.getExpiresAt())) {
      proof.expire(occurredAt);
      return view(proof, AuthenticationOperationStatusEnum.EXPIRED);
    }
    return switch (proof.getStatus()) {
      case OPEN -> view(proof, AuthenticationOperationStatusEnum.OPEN);
      case USED -> view(proof, AuthenticationOperationStatusEnum.ALREADY_USED);
      case INVALIDATED -> view(proof, AuthenticationOperationStatusEnum.INVALIDATED);
      case EXPIRED -> view(proof, AuthenticationOperationStatusEnum.EXPIRED);
    };
  }

  private static AuthenticationProofInspectionVO view(
      AuthenticationProofEntity proof,
      AuthenticationOperationStatusEnum status) {
    return new AuthenticationProofInspectionVO(
        status,
        proof.getType(),
        proof.getAttemptCount(),
        proof.getExpiresAt());
  }
}
