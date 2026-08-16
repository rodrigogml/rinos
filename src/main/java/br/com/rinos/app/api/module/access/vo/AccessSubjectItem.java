package br.com.rinos.app.api.module.access.vo;

import br.com.rinos.app.api.module.access.enums.AccessAdministrationState;

/** Identidade ou associação apresentável no contexto autorizado. */
public record AccessSubjectItem(
    long subjectId,
    long identityId,
    String displayName,
    AccessAdministrationState state) {

  public AccessSubjectItem {
    if (subjectId <= 0 || identityId <= 0) {
      throw new IllegalArgumentException("subject and identity must be positive");
    }
  }

  @Override
  public String toString() {
    return "AccessSubjectItem[subjectId=REDACTED, identityId=REDACTED, displayName="
        + displayName + ", state=" + state + "]";
  }
}
