package br.com.rinos.app.backend.module.access.service;

/** Resultado detalhado e seguro da continuidade em todas as fronteiras de vigencia conhecidas. */
public record AdministrativeContinuitySnapshot(
    boolean sourceAvailable,
    int minimumEligibleAdministrators) {

  public AdministrativeContinuitySnapshot {
    if (minimumEligibleAdministrators < 0) {
      throw new IllegalArgumentException("minimumEligibleAdministrators must not be negative");
    }
    if (!sourceAvailable && minimumEligibleAdministrators != 0) {
      throw new IllegalArgumentException("unavailable snapshot must not report administrators");
    }
  }

  public boolean allowed() {
    return sourceAvailable && minimumEligibleAdministrators > 0;
  }

  public static AdministrativeContinuitySnapshot available(int administrators) {
    return new AdministrativeContinuitySnapshot(true, administrators);
  }

  public static AdministrativeContinuitySnapshot unavailable() {
    return new AdministrativeContinuitySnapshot(false, 0);
  }
}
