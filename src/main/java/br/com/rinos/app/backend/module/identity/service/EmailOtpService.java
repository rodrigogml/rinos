package br.com.rinos.app.backend.module.identity.service;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import br.com.rinos.app.backend.module.identity.entity.AuthenticationProofEntity;
import br.com.rinos.app.backend.module.identity.entity.EmailFactorEntity;
import br.com.rinos.app.backend.module.identity.entity.UserEntity;
import br.com.rinos.app.backend.module.identity.enums.AuthenticationFlowPurposeEnum;
import br.com.rinos.app.backend.module.identity.enums.AuthenticationMethodEnum;
import br.com.rinos.app.backend.module.identity.enums.AuthenticationOperationStatusEnum;
import br.com.rinos.app.backend.module.identity.enums.AuthenticationProofStatusEnum;
import br.com.rinos.app.backend.module.identity.enums.AuthenticationProofTypeEnum;
import br.com.rinos.app.backend.module.identity.enums.EmailFactorStatusEnum;
import br.com.rinos.app.backend.module.identity.enums.EmailOtpVerificationStatusEnum;
import br.com.rinos.app.backend.module.identity.enums.UserStatusEnum;
import br.com.rinos.app.backend.module.identity.repository.AuthenticationProofRepository;
import br.com.rinos.app.backend.module.identity.repository.EmailFactorRepository;
import br.com.rinos.app.backend.module.identity.repository.UserRepository;
import br.com.rinos.app.backend.module.identity.vo.AuthenticationFlowSnapshotVO;
import br.com.rinos.app.backend.module.identity.vo.AuthenticationProofInspectionVO;
import br.com.rinos.app.backend.module.identity.vo.EmailOtpEmissionDecisionVO;
import br.com.rinos.app.backend.module.identity.vo.IssuedEmailOtpVO;
import br.com.rinos.app.backend.module.identity.vo.ProtectedAuthenticationKeyVO;
import br.com.rinos.app.config.AuthenticationMfaPropertiesConfig;
import br.eng.rodrigogml.rfw.authentication.service.RFWOneTimeCodeService;

/**
 * Emite e consome OTP por e-mail vinculado a um fluxo, ao fator e ao endereço atual.
 *
 * <p>A ordem de lock é usuário → fluxo → fator/prova. O código em claro existe somente no
 * resultado transitório destinado ao dispatcher pós-commit; o banco recebe apenas MAC versionado.
 *
 * @author Rodrigo Leitão
 * @since 2026-08-09
 */
@Service
@Lazy
public class EmailOtpService {

  private static final AuthenticationProofTypeEnum PROOF_TYPE =
      AuthenticationProofTypeEnum.EMAIL_OTP;

  private final AuthenticationFlowService flowService;
  private final AuthenticationProofService proofService;
  private final AuthenticationProofRepository proofRepository;
  private final UserRepository userRepository;
  private final EmailFactorRepository factorRepository;
  private final RFWOneTimeCodeService oneTimeCodeService;
  private final AuthenticationKeyringMacService keyringMacService;
  private final EmailPrivacyService emailPrivacyService;
  private final AuthenticationMfaPropertiesConfig properties;

  /** Cria o protocolo especializado sobre as autoridades globais existentes. */
  public EmailOtpService(
      AuthenticationFlowService flowService,
      AuthenticationProofService proofService,
      AuthenticationProofRepository proofRepository,
      UserRepository userRepository,
      EmailFactorRepository factorRepository,
      RFWOneTimeCodeService oneTimeCodeService,
      AuthenticationKeyringMacService keyringMacService,
      EmailPrivacyService emailPrivacyService,
      AuthenticationMfaPropertiesConfig properties) {
    this.flowService = flowService;
    this.proofService = proofService;
    this.proofRepository = proofRepository;
    this.userRepository = userRepository;
    this.factorRepository = factorRepository;
    this.oneTimeCodeService = oneTimeCodeService;
    this.keyringMacService = keyringMacService;
    this.emailPrivacyService = emailPrivacyService;
    this.properties = properties;
  }

  /**
   * Prepara a primeira emissão ou um reenvio, aplicando cooldown e janela por usuário.
   *
   * @param flowReference referência opaca do fluxo de login
   * @param resend {@code true} quando a referência já deve possuir emissão anterior
   * @param occurredAt instante UTC da solicitação
   * @return emissão efêmera, limitação com próximo instante ou rejeição neutra
   */
  @Transactional
  public EmailOtpEmissionDecisionVO issue(
      String flowReference,
      boolean resend,
      Instant occurredAt) {
    Objects.requireNonNull(occurredAt, "occurredAt must not be null");
    LockedContext context = lockContext(flowReference, occurredAt);
    if (context == null) {
      return EmailOtpEmissionDecisionVO.rejected();
    }
    AuthenticationProofEntity latest = proofRepository
        .findFirstByFlowIdAndTypeOrderByIssuedAtDesc(context.flow().flowId(), PROOF_TYPE)
        .orElse(null);
    if (resend && latest == null) {
      return EmailOtpEmissionDecisionVO.rejected();
    }
    Instant cooldown = latest == null
        ? null : latest.getIssuedAt().plus(properties.emailResendCooldown());
    if (cooldown != null && occurredAt.isBefore(cooldown)) {
      return EmailOtpEmissionDecisionVO.rateLimited(cooldown);
    }
    Instant cutoff = occurredAt.minus(properties.emailEmissionWindow());
    List<Instant> recent = proofRepository.findIssuedAtByUserIdAndTypeSince(
        context.user().getId(), PROOF_TYPE, cutoff);
    if (recent.size() >= properties.emailEmissionLimit()) {
      return EmailOtpEmissionDecisionVO.rateLimited(
          recent.getFirst().plus(properties.emailEmissionWindow()));
    }

    String code = oneTimeCodeService.generate();
    byte[] candidate = canonical(context.user().getNormalizedEmail(), code);
    ProtectedAuthenticationKeyVO protectedCode;
    try {
      protectedCode = keyringMacService.protect(domain(context), candidate);
    } finally {
      Arrays.fill(candidate, (byte) 0);
    }
    Instant expiresAt = earlier(
        occurredAt.plus(properties.challengeValidity()), context.flow().expiresAt());
    AuthenticationProofInspectionVO issued = proofService.issue(
        flowReference,
        AuthenticationFlowPurposeEnum.SIGN_IN,
        PROOF_TYPE,
        protectedCode.digest(),
        protectedCode.keyVersion(),
        occurredAt,
        expiresAt);
    if (issued.status() != AuthenticationOperationStatusEnum.OPEN) {
      return EmailOtpEmissionDecisionVO.rejected();
    }
    return EmailOtpEmissionDecisionVO.emitted(new IssuedEmailOtpVO(
        flowReference,
        context.user().getEmail(),
        emailPrivacyService.maskForPublicDisplay(context.user().getEmail()),
        code,
        protectedCode.digest(),
        expiresAt,
        occurredAt.plus(properties.emailResendCooldown()),
        context.flow().correlationId()));
  }

  /** Valida e consome um código uma única vez, registrando uso no fator confirmado. */
  @Transactional
  public EmailOtpVerificationStatusEnum verify(
      String flowReference,
      String code,
      Instant occurredAt) {
    Objects.requireNonNull(occurredAt, "occurredAt must not be null");
    LockedContext context = lockContext(flowReference, occurredAt);
    if (context == null || code == null || code.isBlank()) {
      return EmailOtpVerificationStatusEnum.REJECTED;
    }
    byte[] candidate = canonical(context.user().getNormalizedEmail(), code);
    AuthenticationProofInspectionVO result;
    try {
      result = proofService.consumeMac(
          flowReference,
          AuthenticationFlowPurposeEnum.SIGN_IN,
          PROOF_TYPE,
          domain(context),
          candidate,
          properties.maximumAttempts(),
          occurredAt);
    } finally {
      Arrays.fill(candidate, (byte) 0);
    }
    return switch (result.status()) {
      case USED -> {
        context.factor().recordUse(occurredAt);
        yield EmailOtpVerificationStatusEnum.USED;
      }
      case EXPIRED -> EmailOtpVerificationStatusEnum.EXPIRED;
      case INVALIDATED -> result.attemptCount() >= properties.maximumAttempts()
          ? EmailOtpVerificationStatusEnum.ATTEMPTS_EXHAUSTED
          : EmailOtpVerificationStatusEnum.STALE;
      case ALREADY_USED -> EmailOtpVerificationStatusEnum.STALE;
      default -> EmailOtpVerificationStatusEnum.REJECTED;
    };
  }

  /**
   * Compensa uma entrega recusada sem poder invalidar um reenvio concorrente posterior.
   *
   * <p>A nova transação é obrigatória porque a decisão SMTP ocorre no callback pós-commit.
   */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void invalidateFailedDelivery(
      String flowReference,
      byte[] expectedDigest,
      Instant occurredAt) {
    Objects.requireNonNull(occurredAt, "occurredAt must not be null");
    Long userId = flowService.resolveUserId(flowReference).orElse(null);
    if (userId == null || userRepository.findByIdForUpdate(userId).isEmpty()) {
      return;
    }
    proofService.cancelIfDigestMatches(
        flowReference,
        AuthenticationFlowPurposeEnum.SIGN_IN,
        PROOF_TYPE,
        expectedDigest,
        occurredAt);
  }

  private LockedContext lockContext(String flowReference, Instant occurredAt) {
    Long userId = flowService.resolveUserId(flowReference).orElse(null);
    if (userId == null) {
      return null;
    }
    UserEntity user = userRepository.findByIdForUpdate(userId).orElse(null);
    if (user == null || user.getStatus() != UserStatusEnum.ACTIVE) {
      return null;
    }
    AuthenticationFlowSnapshotVO flow = flowService.snapshot(
        flowReference, AuthenticationFlowPurposeEnum.SIGN_IN, occurredAt);
    if (flow.status() != AuthenticationOperationStatusEnum.OPEN
        || !Objects.equals(flow.userId(), user.getId())
        || !flow.permittedMethods().contains(AuthenticationMethodEnum.EMAIL_CODE)) {
      return null;
    }
    EmailFactorEntity factor = factorRepository.findByUserIdForUpdate(userId).orElse(null);
    if (factor == null || factor.getStatus() != EmailFactorStatusEnum.ACTIVE) {
      return null;
    }
    return new LockedContext(user, flow, factor);
  }

  private static byte[] canonical(String normalizedEmail, String code) {
    Objects.requireNonNull(normalizedEmail, "normalizedEmail must not be null");
    byte[] emailBytes = normalizedEmail.getBytes(StandardCharsets.UTF_8);
    byte[] codeBytes = Objects.requireNonNull(code, "code must not be null")
        .getBytes(StandardCharsets.US_ASCII);
    return ByteBuffer.allocate(Integer.BYTES + emailBytes.length + codeBytes.length)
        .putInt(emailBytes.length)
        .put(emailBytes)
        .put(codeBytes)
        .array();
  }

  private static String domain(LockedContext context) {
    return "email-otp:" + context.flow().flowId() + ":" + context.factor().getReference();
  }

  private static Instant earlier(Instant first, Instant second) {
    return first.isBefore(second) ? first : second;
  }

  private record LockedContext(
      UserEntity user,
      AuthenticationFlowSnapshotVO flow,
      EmailFactorEntity factor) {
  }
}
