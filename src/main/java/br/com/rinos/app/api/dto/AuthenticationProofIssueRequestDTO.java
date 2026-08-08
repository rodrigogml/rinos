package br.com.rinos.app.api.dto;

import java.time.Instant;
import java.util.Arrays;
import java.util.Objects;

import br.com.rinos.app.api.enums.AuthenticationFlowPurposeEnum;
import br.com.rinos.app.api.enums.AuthenticationProofTypeEnum;

/**
 * Transporta somente o digest protegido de uma prova; o valor bruto não cruza esta fronteira.
 *
 * @author Rodrigo Leitão
 * @since 2026-08-08
 */
public final class AuthenticationProofIssueRequestDTO {

  private final String flowReference;
  private final AuthenticationFlowPurposeEnum purpose;
  private final AuthenticationProofTypeEnum type;
  private final byte[] proofDigest;
  private final String keyVersion;
  private final Instant issuedAt;
  private final Instant expiresAt;

  public AuthenticationProofIssueRequestDTO(
      String flowReference,
      AuthenticationFlowPurposeEnum purpose,
      AuthenticationProofTypeEnum type,
      byte[] proofDigest,
      String keyVersion,
      Instant issuedAt,
      Instant expiresAt) {
    this.flowReference = flowReference;
    this.purpose = Objects.requireNonNull(purpose, "purpose must not be null");
    this.type = Objects.requireNonNull(type, "type must not be null");
    this.proofDigest = Arrays.copyOf(
        Objects.requireNonNull(proofDigest, "proofDigest must not be null"),
        proofDigest.length);
    this.keyVersion = keyVersion;
    this.issuedAt = Objects.requireNonNull(issuedAt, "issuedAt must not be null");
    this.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt must not be null");
  }

  public String getFlowReference() {
    return flowReference;
  }

  public AuthenticationFlowPurposeEnum getPurpose() {
    return purpose;
  }

  public AuthenticationProofTypeEnum getType() {
    return type;
  }

  public byte[] getProofDigest() {
    return Arrays.copyOf(proofDigest, proofDigest.length);
  }

  public String getKeyVersion() {
    return keyVersion;
  }

  public Instant getIssuedAt() {
    return issuedAt;
  }

  public Instant getExpiresAt() {
    return expiresAt;
  }

  @Override
  public String toString() {
    return "AuthenticationProofIssueRequestDTO[flowReference=REDACTED, purpose="
        + purpose + ", type=" + type + ", proofDigest=REDACTED, keyVersion="
        + keyVersion + ", issuedAt=" + issuedAt + ", expiresAt=" + expiresAt + "]";
  }
}
