package br.com.rinos.app.api.facade;

import java.time.Instant;

import br.com.rinos.app.api.dto.AuthenticationFlowIssueRequestDTO;
import br.com.rinos.app.api.dto.AuthenticationProofIssueRequestDTO;
import br.com.rinos.app.api.enums.AuthenticationFlowPurposeEnum;
import br.com.rinos.app.api.enums.AuthenticationProofTypeEnum;
import br.com.rinos.app.api.vo.AuthenticationFlowResultVO;
import br.com.rinos.app.api.vo.AuthenticationProofResultVO;

/**
 * Fronteira interna para continuações e provas opacas, sem exposição da persistência.
 *
 * @author Rodrigo Leitão
 * @since 2026-08-08
 */
public interface AuthenticationFlowFacade {

  AuthenticationFlowResultVO issueFlow(AuthenticationFlowIssueRequestDTO request);

  AuthenticationFlowResultVO inspectFlow(
      String reference,
      AuthenticationFlowPurposeEnum purpose,
      Instant occurredAt);

  AuthenticationFlowResultVO consumeFlow(
      String reference,
      AuthenticationFlowPurposeEnum purpose,
      Instant occurredAt);

  AuthenticationFlowResultVO cancelFlow(
      String reference,
      AuthenticationFlowPurposeEnum purpose,
      Instant occurredAt);

  AuthenticationProofResultVO issueProof(AuthenticationProofIssueRequestDTO request);

  AuthenticationProofResultVO inspectProof(
      String reference,
      AuthenticationFlowPurposeEnum purpose,
      AuthenticationProofTypeEnum type,
      Instant occurredAt);

  AuthenticationProofResultVO consumeProof(
      String reference,
      AuthenticationFlowPurposeEnum purpose,
      AuthenticationProofTypeEnum type,
      byte[] candidateDigest,
      Instant occurredAt);

  AuthenticationProofResultVO cancelProof(
      String reference,
      AuthenticationFlowPurposeEnum purpose,
      AuthenticationProofTypeEnum type,
      Instant occurredAt);
}
