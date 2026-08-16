package br.com.rinos.app.backend.module.access.service;

import br.com.rinos.app.api.module.access.vo.AccessAdministrationPreview;

/** Sinal interno que garante rollback inclusive quando a simulacao seria valida. */
public final class AccessAdministrationPreviewRollback extends RuntimeException {
  private final AccessAdministrationPreview preview;

  public AccessAdministrationPreviewRollback(AccessAdministrationPreview preview) {
    super("access administration preview rollback", null, false, false);
    this.preview = preview;
  }

  public AccessAdministrationPreview preview() {
    return preview;
  }
}
