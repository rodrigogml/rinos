package br.com.rinos.app.api.module.access.vo;

/** Capacidades já resolvidas para compor a central sem vazar seções não autorizadas. */
public record AccessAdministrationCapabilities(
    boolean catalogView,
    boolean groupView,
    boolean ruleView,
    boolean groupManage,
    boolean ruleManage,
    boolean explanationView) {

  public boolean anyView() {
    return catalogView || groupView || ruleView;
  }
}
