package br.com.rinos.app.ui.config;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import com.vaadin.flow.component.UI;

import br.com.rinos.app.api.module.access.dto.AccessGroupSaveRequest;
import br.com.rinos.app.api.module.access.dto.AccessRuleSaveRequest;
import br.com.rinos.app.api.module.access.dto.AccessGroupSubjectChangeRequest;
import br.com.rinos.app.api.module.access.dto.AccessRecordDeactivateRequest;
import br.com.rinos.app.api.module.access.enums.AccessAdministrationOrigin;
import br.com.rinos.app.api.module.access.enums.AccessRuleEffect;
import br.com.rinos.app.api.module.access.facade.AccessAdministrationFacade;
import br.com.rinos.app.api.module.access.keys.AccessControlOperations;
import br.com.rinos.app.api.module.access.vo.AccessAdministrationMutationOutcome;
import br.com.rinos.app.api.module.access.vo.AccessAdministrationSnapshot;
import br.com.rinos.app.api.module.access.vo.AccessAdministrationCapabilities;
import br.com.rinos.app.api.module.access.vo.AccessSubjectItem;
import br.com.rinos.app.api.module.access.vo.AuthorizationWorkspaceContext;
import br.com.rinos.app.api.module.access.vo.AuthorizationDecision;
import br.com.rinos.app.api.module.access.vo.AccessKeyDescriptor;
import br.com.rinos.app.api.module.access.vo.AccessExplanation;
import br.com.rinos.app.api.module.access.vo.AccessAdministrationPreview;
import br.com.rinos.app.api.module.access.keys.AccessControlAccessKeys;
import br.com.rinos.app.api.module.access.keys.InitialModuleAccessKeys;
import br.com.rinos.app.api.module.access.vo.AuthorizationOperation;

/** Orquestra autenticação Spring, autorização efetiva e administração pública da ACL. */
@Component
@org.springframework.context.annotation.Lazy
public class SpringAccessAdministrationAdapter {

  private final SpringAuthorizationAdapter authorization;
  private final WorkspaceAuthorizationContextAdapter workspaces;
  private final AccessAdministrationFacade administration;

  public SpringAccessAdministrationAdapter(
      SpringAuthorizationAdapter authorization,
      WorkspaceAuthorizationContextAdapter workspaces,
      AccessAdministrationFacade administration) {
    this.authorization = authorization;
    this.workspaces = workspaces;
    this.administration = administration;
  }

  public AccessAdministrationSnapshot inspect(UI ui) {
    AuthorizationWorkspaceContext workspace = workspaces.require(ui);
    var scope = workspace.context().scope();
    AuthorizationDecision views = authorization.decide(
        workspace, AccessControlOperations.inspectViews(scope));
    AuthorizationDecision management = authorization.decide(
        workspace, AccessControlOperations.inspectManagement(scope));
    AuthorizationDecision explanation = authorization.decide(
        workspace, AccessControlOperations.inspectExplanation(scope));
    AccessAdministrationCapabilities capabilities = new AccessAdministrationCapabilities(
        capability(views, scope == br.com.rinos.app.api.module.access.enums.AccessScope.GLOBAL
            ? AccessControlAccessKeys.GLOBAL_CATALOG_VIEW : AccessControlAccessKeys.TENANT_CATALOG_VIEW),
        capability(views, scope == br.com.rinos.app.api.module.access.enums.AccessScope.GLOBAL
            ? AccessControlAccessKeys.GLOBAL_GROUP_VIEW : AccessControlAccessKeys.TENANT_GROUP_VIEW),
        capability(views, scope == br.com.rinos.app.api.module.access.enums.AccessScope.GLOBAL
            ? AccessControlAccessKeys.GLOBAL_RULE_VIEW : AccessControlAccessKeys.TENANT_RULE_VIEW),
        capability(management, scope == br.com.rinos.app.api.module.access.enums.AccessScope.GLOBAL
            ? AccessControlAccessKeys.GLOBAL_GROUP_MANAGE : AccessControlAccessKeys.TENANT_GROUP_MANAGE),
        capability(management, scope == br.com.rinos.app.api.module.access.enums.AccessScope.GLOBAL
            ? AccessControlAccessKeys.GLOBAL_RULE_MANAGE : AccessControlAccessKeys.TENANT_RULE_MANAGE),
        capability(explanation, scope == br.com.rinos.app.api.module.access.enums.AccessScope.GLOBAL
            ? AccessControlAccessKeys.GLOBAL_EXPLAIN : AccessControlAccessKeys.TENANT_EXPLAIN));
    if (!capabilities.anyView()) {
      throw new org.springframework.security.access.AccessDeniedException("ACL_ACCESS_DENIED");
    }
    return administration.inspect(workspace.context(), capabilities);
  }

  public List<AccessSubjectItem> searchSubjects(UI ui, String query, int limit) {
    AuthorizationWorkspaceContext workspace = workspaces.require(ui);
    if (!authorization.decide(
        workspace, AccessControlOperations.viewGroup(workspace.context().scope())).allowed()) {
      authorization.require(workspace, AccessControlOperations.viewRule(workspace.context().scope()));
    }
    return administration.searchSubjects(workspace.context(), query, limit);
  }

  public AccessAdministrationMutationOutcome saveGroup(
      UI ui, long expectedRevision, Long groupId, String name, String description,
      String reason) {
    AuthorizationWorkspaceContext workspace = workspaces.require(ui);
    authorization.require(workspace, AccessControlOperations.manageGroup(workspace.context().scope()));
    return administration.saveGroup(new AccessGroupSaveRequest(
        workspace.context(), expectedRevision, groupId, name, description, currentUserId(),
        reason, correlationId()));
  }

  public AccessAdministrationMutationOutcome saveRule(
      UI ui, long expectedRevision, AccessAdministrationOrigin origin, long originId,
      String accessKeyInternalReference, AccessRuleEffect effect, Instant validFrom,
      Instant validUntil, String reason) {
    AuthorizationWorkspaceContext workspace = workspaces.require(ui);
    authorization.require(workspace, AccessControlOperations.manageRule(workspace.context().scope()));
    return administration.saveRule(new AccessRuleSaveRequest(
        workspace.context(), expectedRevision, origin, originId, accessKeyInternalReference,
        effect, validFrom, validUntil, currentUserId(), reason, correlationId()));
  }

  public AccessAdministrationMutationOutcome changeGroupSubject(
      UI ui, long expectedRevision, boolean assign, Long groupSubjectId, Long groupId,
      Long subjectId, Instant validFrom, Instant validUntil, String reason) {
    AuthorizationWorkspaceContext workspace = workspaces.require(ui);
    authorization.require(workspace, AccessControlOperations.manageGroup(workspace.context().scope()));
    return administration.changeGroupSubject(new AccessGroupSubjectChangeRequest(
        workspace.context(), expectedRevision, assign, groupSubjectId, groupId, subjectId,
        validFrom, validUntil, currentUserId(), reason, correlationId()));
  }

  public AccessAdministrationMutationOutcome deactivateGroup(
      UI ui, long expectedRevision, long groupId, String reason) {
    AuthorizationWorkspaceContext workspace = workspaces.require(ui);
    authorization.require(workspace, AccessControlOperations.manageGroup(workspace.context().scope()));
    return administration.deactivateGroup(new AccessRecordDeactivateRequest(
        workspace.context(), expectedRevision, groupId, currentUserId(), reason, correlationId()));
  }

  public AccessAdministrationMutationOutcome deactivateRule(
      UI ui, long expectedRevision, long ruleId, String reason) {
    AuthorizationWorkspaceContext workspace = workspaces.require(ui);
    authorization.require(workspace, AccessControlOperations.manageRule(workspace.context().scope()));
    return administration.deactivateRule(new AccessRecordDeactivateRequest(
        workspace.context(), expectedRevision, ruleId, currentUserId(), reason, correlationId()));
  }

  public AccessExplanation explainSubject(
      UI ui, AccessSubjectItem subject, List<String> accessKeyInternalReferences) {
    AuthorizationWorkspaceContext workspace = workspaces.require(ui);
    if (subject == null || accessKeyInternalReferences == null
        || accessKeyInternalReferences.isEmpty()) {
      throw new IllegalArgumentException("access explanation selection is incomplete");
    }
    java.util.Set<AccessKeyDescriptor> descriptors = accessKeyInternalReferences.stream()
        .map(reference -> descriptor(workspace, reference))
        .collect(java.util.stream.Collectors.toUnmodifiableSet());
    return authorization.explain(workspace, subject.identityId(),
        workspace.context().scope()
            == br.com.rinos.app.api.module.access.enums.AccessScope.TENANT
                ? subject.subjectId() : null,
        new AuthorizationOperation("access.effective.explain", descriptors, false));
  }

  public AccessAdministrationPreview previewGroup(
      UI ui, long expectedRevision, Long groupId, String name, String description,
      String reason) {
    AuthorizationWorkspaceContext workspace = authorizedGroupWorkspace(ui);
    return administration.previewGroup(new AccessGroupSaveRequest(
        workspace.context(), expectedRevision, groupId, name, description, currentUserId(),
        reason, correlationId()));
  }

  public AccessAdministrationPreview previewRule(
      UI ui, long expectedRevision, AccessAdministrationOrigin origin, long originId,
      String accessKeyInternalReference, AccessRuleEffect effect, Instant validFrom,
      Instant validUntil, String reason) {
    AuthorizationWorkspaceContext workspace = authorizedRuleWorkspace(ui);
    return administration.previewRule(new AccessRuleSaveRequest(
        workspace.context(), expectedRevision, origin, originId, accessKeyInternalReference,
        effect, validFrom, validUntil, currentUserId(), reason, correlationId()));
  }

  public AccessAdministrationPreview previewGroupSubject(
      UI ui, long expectedRevision, boolean assign, Long groupSubjectId, Long groupId,
      Long subjectId, Instant validFrom, Instant validUntil, String reason) {
    AuthorizationWorkspaceContext workspace = authorizedGroupWorkspace(ui);
    return administration.previewGroupSubject(new AccessGroupSubjectChangeRequest(
        workspace.context(), expectedRevision, assign, groupSubjectId, groupId, subjectId,
        validFrom, validUntil, currentUserId(), reason, correlationId()));
  }

  public AccessAdministrationPreview previewGroupDeactivation(
      UI ui, long expectedRevision, long groupId, String reason) {
    AuthorizationWorkspaceContext workspace = authorizedGroupWorkspace(ui);
    return administration.previewGroupDeactivation(new AccessRecordDeactivateRequest(
        workspace.context(), expectedRevision, groupId, currentUserId(), reason, correlationId()));
  }

  public AccessAdministrationPreview previewRuleDeactivation(
      UI ui, long expectedRevision, long ruleId, String reason) {
    AuthorizationWorkspaceContext workspace = authorizedRuleWorkspace(ui);
    return administration.previewRuleDeactivation(new AccessRecordDeactivateRequest(
        workspace.context(), expectedRevision, ruleId, currentUserId(), reason, correlationId()));
  }

  private AuthorizationWorkspaceContext authorizedGroupWorkspace(UI ui) {
    AuthorizationWorkspaceContext workspace = workspaces.require(ui);
    authorization.require(workspace, AccessControlOperations.previewGroup(workspace.context().scope()));
    return workspace;
  }

  private AuthorizationWorkspaceContext authorizedRuleWorkspace(UI ui) {
    AuthorizationWorkspaceContext workspace = workspaces.require(ui);
    authorization.require(workspace, AccessControlOperations.previewRule(workspace.context().scope()));
    return workspace;
  }

  private static AccessKeyDescriptor descriptor(
      AuthorizationWorkspaceContext workspace, String reference) {
    return java.util.stream.Stream.concat(
            InitialModuleAccessKeys.ALL.stream(), AccessControlAccessKeys.ALL.stream())
        .filter(key -> key.scope() == workspace.context().scope() && key.code().equals(reference))
        .findFirst().orElseThrow(() -> new IllegalArgumentException("access key is unavailable"));
  }

  private static long currentUserId() {
    Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    if (!(principal instanceof RFWAuthenticatedPrincipalAdapter authenticated)) {
      throw new IllegalStateException("ACL_INVALID_AUTHENTICATION");
    }
    return authenticated.user().userId();
  }

  private static String correlationId() {
    return "ui-access-" + UUID.randomUUID();
  }

  private static boolean capability(
      AuthorizationDecision decision, AccessKeyDescriptor key) {
    boolean gatesAllowed = decision.structuralGates().stream().allMatch(gate -> gate.allowed())
        && decision.entitlementGates().stream().allMatch(gate -> gate.allowed())
        && decision.assuranceGates().stream().allMatch(gate -> gate.allowed());
    return gatesAllowed && decision.keyResults().stream()
        .anyMatch(result -> result.key().equals(key) && result.allowed());
  }
}
