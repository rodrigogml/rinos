package br.com.rinos.app.backend.module.access.service;

/** Estado estrutural minimizado de uma associação e de sua conta. */
public record AccountMembershipAccessSnapshot(
    boolean sourceAvailable,
    boolean exists,
    Long identityId,
    Long tenantId,
    boolean membershipActive,
    boolean tenantOperational) {

  public AccountMembershipAccessSnapshot {
    if (!sourceAvailable && (exists || identityId != null || tenantId != null
        || membershipActive || tenantOperational)
        || exists && (identityId == null || identityId <= 0 || tenantId == null || tenantId <= 0)
        || !exists && (identityId != null || tenantId != null || membershipActive
            || tenantOperational)) {
      throw new IllegalArgumentException("membership access snapshot is inconsistent");
    }
  }

  public static AccountMembershipAccessSnapshot unavailable() {
    return new AccountMembershipAccessSnapshot(false, false, null, null, false, false);
  }

  public static AccountMembershipAccessSnapshot absent() {
    return new AccountMembershipAccessSnapshot(true, false, null, null, false, false);
  }

  public static AccountMembershipAccessSnapshot found(
      long identityId, long tenantId, boolean membershipActive, boolean tenantOperational) {
    return new AccountMembershipAccessSnapshot(
        true, true, identityId, tenantId, membershipActive, tenantOperational);
  }
}
