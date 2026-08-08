package br.com.rinos.app.backend.module.identity.vo;

import java.util.Arrays;
import java.util.Objects;

/**
 * Transporte efêmero do material público WebAuthn já validado pelo adapter.
 *
 * @author Rodrigo Leitão
 */
public final class PasskeyCredentialRegistrationVO {
  private final String credentialType;
  private final byte[] credentialId;
  private final byte[] publicKey;
  private final long signatureCount;
  private final boolean uvInitialized;
  private final boolean backupEligible;
  private final boolean backupState;
  private final String transports;
  private final byte[] attestationObject;
  private final byte[] attestationClientDataJson;
  private final String label;

  public PasskeyCredentialRegistrationVO(String credentialType, byte[] credentialId,
      byte[] publicKey, long signatureCount, boolean uvInitialized, boolean backupEligible,
      boolean backupState, String transports, byte[] attestationObject,
      byte[] attestationClientDataJson, String label) {
    this.credentialType = Objects.requireNonNull(credentialType);
    this.credentialId = copy(credentialId);
    this.publicKey = copy(publicKey);
    this.signatureCount = signatureCount;
    this.uvInitialized = uvInitialized;
    this.backupEligible = backupEligible;
    this.backupState = backupState;
    this.transports = transports;
    this.attestationObject = copy(attestationObject);
    this.attestationClientDataJson = copy(attestationClientDataJson);
    this.label = Objects.requireNonNull(label);
  }
  public String credentialType() { return credentialType; }
  public byte[] credentialId() { return copy(credentialId); }
  public byte[] publicKey() { return copy(publicKey); }
  public long signatureCount() { return signatureCount; }
  public boolean uvInitialized() { return uvInitialized; }
  public boolean backupEligible() { return backupEligible; }
  public boolean backupState() { return backupState; }
  public String transports() { return transports; }
  public byte[] attestationObject() { return copy(attestationObject); }
  public byte[] attestationClientDataJson() { return copy(attestationClientDataJson); }
  public String label() { return label; }
  @Override public String toString() { return "PasskeyCredentialRegistrationVO[material=REDACTED, label=" + label + "]"; }
  private static byte[] copy(byte[] value) { return Arrays.copyOf(Objects.requireNonNull(value), value.length); }
}
