package br.com.rinos.app.api.module.access.facade;

import br.com.rinos.app.api.module.access.dto.AccessGroupSaveRequest;
import br.com.rinos.app.api.module.access.dto.AccessRuleSaveRequest;
import br.com.rinos.app.api.module.access.dto.AccessGroupSubjectChangeRequest;
import br.com.rinos.app.api.module.access.dto.AccessRecordDeactivateRequest;
import br.com.rinos.app.api.module.access.vo.AccessAdministrationMutationOutcome;
import br.com.rinos.app.api.module.access.vo.AccessAdministrationSnapshot;
import br.com.rinos.app.api.module.access.vo.AuthorizationContext;
import br.com.rinos.app.api.module.access.vo.AccessAdministrationCapabilities;
import br.com.rinos.app.api.module.access.vo.AccessSubjectItem;
import br.com.rinos.app.api.module.access.vo.AccessAdministrationPreview;
import java.util.List;

/** Fronteira pública da central, sem entidades ou repositórios persistentes. */
public interface AccessAdministrationFacade {
  AccessAdministrationSnapshot inspect(
      AuthorizationContext context, AccessAdministrationCapabilities capabilities);
  AccessAdministrationMutationOutcome saveGroup(AccessGroupSaveRequest request);
  AccessAdministrationMutationOutcome saveRule(AccessRuleSaveRequest request);
  AccessAdministrationMutationOutcome changeGroupSubject(AccessGroupSubjectChangeRequest request);
  AccessAdministrationMutationOutcome deactivateGroup(AccessRecordDeactivateRequest request);
  AccessAdministrationMutationOutcome deactivateRule(AccessRecordDeactivateRequest request);
  AccessAdministrationPreview previewGroup(AccessGroupSaveRequest request);
  AccessAdministrationPreview previewRule(AccessRuleSaveRequest request);
  AccessAdministrationPreview previewGroupSubject(AccessGroupSubjectChangeRequest request);
  AccessAdministrationPreview previewGroupDeactivation(AccessRecordDeactivateRequest request);
  AccessAdministrationPreview previewRuleDeactivation(AccessRecordDeactivateRequest request);
  List<AccessSubjectItem> searchSubjects(AuthorizationContext context, String query, int limit);
}
