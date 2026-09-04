package br.com.rinos.app.api.module.access.exception;

/** Indica que o contexto mudou depois da fotografia aberta pelo editor. */
public class AccessAdministrationConflictException extends RuntimeException {
  public AccessAdministrationConflictException() {
    super("ACL_CONTEXT_REVISION_CONFLICT");
  }
}
