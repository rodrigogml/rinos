package br.com.rinos.app.ui.module.access.view;

import static br.eng.rodrigogml.rfw.i18n.vaadin.RFWTr.tr;

import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.datetimepicker.DateTimePicker;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.treegrid.TreeGrid;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.FlexLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.tabs.Tab;
import com.vaadin.flow.component.tabs.Tabs;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.provider.hierarchy.TreeData;
import com.vaadin.flow.data.provider.hierarchy.TreeDataProvider;

import br.com.rinos.app.api.module.access.enums.AccessAdministrationOrigin;
import br.com.rinos.app.api.module.access.enums.AccessAdministrationState;
import br.com.rinos.app.api.module.access.enums.AccessRuleEffect;
import br.com.rinos.app.api.module.access.exception.AccessAdministrationConflictException;
import br.com.rinos.app.api.module.access.vo.AccessAdministrationSnapshot;
import br.com.rinos.app.api.module.access.vo.AccessCategoryItem;
import br.com.rinos.app.api.module.access.vo.AccessGroupItem;
import br.com.rinos.app.api.module.access.vo.AccessGroupSubjectItem;
import br.com.rinos.app.api.module.access.vo.AccessKeyItem;
import br.com.rinos.app.api.module.access.vo.AccessRuleItem;
import br.com.rinos.app.api.module.access.vo.AccessSubjectItem;
import br.com.rinos.app.api.module.access.vo.AccessAdministrationPreview;
import br.com.rinos.app.api.module.access.vo.AccessExplanation;
import br.com.rinos.app.api.module.access.vo.AuthorizationKeyResult;
import br.com.rinos.app.api.module.access.vo.AuthorizationRuleSource;
import br.com.rinos.app.ui.config.SpringAccessAdministrationAdapter;
import br.eng.rodrigogml.rfw.resource.icon.RFWIconEnum;
import br.eng.rodrigogml.rfw.ui.button.RFWButtonActionEnum;
import br.eng.rodrigogml.rfw.ui.gui.UIFactory;
import br.eng.rodrigogml.rfw.ui.icon.RFWVaadinIcons;
import br.eng.rodrigogml.rfw.ui.picker.RFWPicker;
import br.eng.rodrigogml.rfw.authentication.provider.RFWReauthenticationChallengeProvider;

/** Composição Rinos da central contextual usando exclusivamente primitives públicas do RFW/Vaadin. */
public class AccessCenterComponent extends VerticalLayout {

  private final SpringAccessAdministrationAdapter administration;
  private final AccessReauthenticationFlow reauthentication;
  private final Span contextLabel = new Span();
  private final TextField search = new TextField();
  private final TreeGrid<AccessCategoryItem> categoryTree = new TreeGrid<>();
  private final Grid<AccessGroupItem> groupGrid = new Grid<>();
  private final Grid<AccessSubjectItem> subjectGrid = new Grid<>();
  private final Grid<AccessKeyItem> catalogGrid = new Grid<>();
  private final VerticalLayout content = new VerticalLayout();
  private final Tab groupsTab = new Tab();
  private final Tab subjectsTab = new Tab();
  private final Tab catalogTab = new Tab();
  private AccessAdministrationSnapshot snapshot;
  private AccessCategoryItem selectedCategory;

  public AccessCenterComponent(
      SpringAccessAdministrationAdapter administration,
      RFWReauthenticationChallengeProvider reauthenticationProvider) {
    this.administration = administration;
    this.reauthentication = new AccessReauthenticationFlow(reauthenticationProvider);
    setSizeFull();
    setPadding(true);
    configureHeader();
    configureNavigation();
    configureGrids();
  }

  /** Reautoriza e recarrega a fotografia do contexto da UI exata. */
  public void load(UI ui) {
    try {
      snapshot = administration.inspect(ui);
      contextLabel.setText(snapshot.context().scope()
          == br.com.rinos.app.api.module.access.enums.AccessScope.GLOBAL
              ? tr("access.center.context.global")
              : tr("access.center.context.tenant", snapshot.context().tenantId()));
      setEnabled(true);
      configureCapabilities();
      refreshCategories();
      applyFilter();
    } catch (RuntimeException unavailable) {
      showPersistentError("access.center.error.unavailable");
      setEnabled(false);
    }
  }

  private void configureHeader() {
    H1 title = new H1(tr("access.center.title"));
    Paragraph description = new Paragraph(tr("access.center.description"));
    search.setLabel(tr("access.center.search.label"));
    search.setPlaceholder(tr("access.center.search.placeholder"));
    search.setClearButtonVisible(true);
    search.setId("access-center-search");
    search.setWidthFull();
    Button apply = UIFactory.createButton(RFWButtonActionEnum.FILTER);
    apply.addClickListener(event -> applyFilter());
    Button clear = UIFactory.createButton(RFWButtonActionEnum.FILTER_CLEAR);
    clear.addClickListener(event -> {
      search.clear();
      selectedCategory = null;
      categoryTree.deselectAll();
      applyFilter();
    });
    HorizontalLayout actions = new HorizontalLayout(search, apply, clear);
    actions.setWidthFull();
    actions.setAlignItems(FlexComponent.Alignment.END);
    add(title, description, contextLabel, actions);
  }

  private void configureNavigation() {
    categoryTree.addHierarchyColumn(item -> localized(item.nameI18nKey()))
        .setHeader(tr("access.center.categories"));
    categoryTree.setId("access-center-categories");
    categoryTree.setWidthFull();
    categoryTree.setMaxHeight("20rem");
    categoryTree.asSingleSelect().addValueChangeListener(event -> {
      selectedCategory = event.getValue();
      applyFilter();
    });

    groupsTab.setLabel(tr("access.center.tab.groups"));
    subjectsTab.setLabel(tr("access.center.tab.subjects"));
    catalogTab.setLabel(tr("access.center.tab.catalog"));
    Tabs tabs = new Tabs(groupsTab, subjectsTab, catalogTab);
    tabs.addSelectedChangeListener(event -> showSelectedTab(event.getSelectedTab()));
    content.setPadding(false);
    content.setSizeFull();
    VerticalLayout resultArea = new VerticalLayout(tabs, content);
    resultArea.setPadding(false);
    resultArea.setMinWidth("20rem");
    resultArea.setFlexGrow(1, content);
    FlexLayout workspace = new FlexLayout(categoryTree, resultArea);
    workspace.setWidthFull();
    workspace.setFlexWrap(FlexLayout.FlexWrap.WRAP);
    workspace.setAlignItems(FlexComponent.Alignment.START);
    workspace.setFlexGrow(1, resultArea);
    add(workspace);
  }

  private void configureGrids() {
    groupGrid.addColumn(AccessGroupItem::name).setHeader(tr("access.group.name"))
        .setAutoWidth(true).setFlexGrow(1);
    groupGrid.addColumn(item -> nullSafe(item.description()))
        .setHeader(tr("access.group.description")).setFlexGrow(2);
    groupGrid.addColumn(item -> stateText(item.state()))
        .setHeader(tr("access.common.state")).setAutoWidth(true);
    groupGrid.addColumn(AccessGroupItem::subjectCount)
        .setHeader(tr("access.group.participants")).setAutoWidth(true);
    prepareGrid(groupGrid);
    groupGrid.setId("access-center-groups");

    subjectGrid.addColumn(AccessSubjectItem::displayName)
        .setHeader(tr("access.subject.name")).setFlexGrow(1);
    subjectGrid.addColumn(item -> stateText(item.state()))
        .setHeader(tr("access.common.state")).setAutoWidth(true);
    prepareGrid(subjectGrid);
    subjectGrid.setId("access-center-subjects");

    catalogGrid.addColumn(item -> localized(item.nameI18nKey()))
        .setHeader(tr("access.key.name")).setFlexGrow(1);
    catalogGrid.addColumn(item -> localized(item.descriptionI18nKey()))
        .setHeader(tr("access.key.description")).setFlexGrow(2);
    catalogGrid.addColumn(item -> stateText(item.state()))
        .setHeader(tr("access.common.state")).setAutoWidth(true);
    prepareGrid(catalogGrid);
    catalogGrid.setId("access-center-catalog");
  }

  private void configureCapabilities() {
    groupsTab.setVisible(snapshot.capabilities().groupView());
    subjectsTab.setVisible(snapshot.capabilities().groupView() || snapshot.capabilities().ruleView());
    catalogTab.setVisible(snapshot.capabilities().catalogView());
    Tab initial = groupsTab.isVisible() ? groupsTab
        : subjectsTab.isVisible() ? subjectsTab : catalogTab;
    showSelectedTab(initial);
  }

  private void showSelectedTab(Tab tab) {
    content.removeAll();
    if (tab == groupsTab) content.add(groupPanel());
    else if (tab == subjectsTab) content.add(subjectPanel());
    else content.add(catalogGrid);
  }

  private Component groupPanel() {
    Button create = UIFactory.createButton(RFWButtonActionEnum.INSERT);
    create.setVisible(snapshot.capabilities().groupManage());
    create.addClickListener(event -> openGroupEditor(null));
    Button edit = UIFactory.createButton(RFWButtonActionEnum.EDIT);
    edit.setVisible(snapshot.capabilities().groupManage());
    edit.addClickListener(event -> groupGrid.asSingleSelect().getOptionalValue()
        .ifPresent(this::openGroupEditor));
    Button rules = UIFactory.createButton(
        "access.rule.edit", RFWVaadinIcons.create(RFWIconEnum.KEY_ALT));
    rules.setVisible(snapshot.capabilities().ruleManage());
    rules.addClickListener(event -> groupGrid.asSingleSelect().getOptionalValue()
        .ifPresent(group -> openRuleEditor(AccessAdministrationOrigin.GROUP, group.id(), null)));
    return new VerticalLayout(new HorizontalLayout(create, edit, rules), groupGrid);
  }

  private Component subjectPanel() {
    Button rules = UIFactory.createButton(
        "access.rule.direct.edit", RFWVaadinIcons.create(RFWIconEnum.KEY_ALT));
    rules.setVisible(snapshot.capabilities().ruleManage());
    rules.addClickListener(event -> subjectGrid.asSingleSelect().getOptionalValue()
        .ifPresent(subject -> openRuleEditor(
            snapshot.context().scope() == br.com.rinos.app.api.module.access.enums.AccessScope.GLOBAL
                ? AccessAdministrationOrigin.DIRECT_IDENTITY
                : AccessAdministrationOrigin.DIRECT_MEMBERSHIP,
            subject.subjectId(), null)));
    Button explain = UIFactory.createButton(
        "access.explanation.action", RFWVaadinIcons.create(RFWIconEnum.MAGNIFIER));
    explain.setVisible(snapshot.capabilities().explanationView());
    explain.addClickListener(event -> subjectGrid.asSingleSelect().getOptionalValue()
        .ifPresent(this::openExplanationKeySelection));
    return new VerticalLayout(new HorizontalLayout(rules, explain), subjectGrid);
  }

  private void openExplanationKeySelection(AccessSubjectItem subject) {
    Dialog dialog = new Dialog();
    dialog.setHeaderTitle(tr("access.explanation.select.title"));
    TextField filter = new TextField(tr("access.center.search.label"));
    filter.setClearButtonVisible(true);
    Grid<AccessKeyItem> keys = new Grid<>();
    keys.setSelectionMode(Grid.SelectionMode.MULTI);
    keys.addColumn(item -> localized(item.nameI18nKey()))
        .setHeader(tr("access.key.name")).setFlexGrow(1);
    keys.addColumn(item -> localized(item.descriptionI18nKey()))
        .setHeader(tr("access.key.description")).setFlexGrow(2);
    Runnable refresh = () -> keys.setItems(snapshot.keys().stream()
        .filter(item -> contains(localized(item.nameI18nKey()), filter.getValue())
            || contains(localized(item.descriptionI18nKey()), filter.getValue()))
        .toList());
    filter.addValueChangeListener(event -> refresh.run());
    refresh.run();
    keys.setHeight("22rem");
    Button cancel = UIFactory.createButton(RFWButtonActionEnum.CANCEL);
    cancel.addClickListener(event -> dialog.close());
    Button explain = UIFactory.createButton(RFWButtonActionEnum.CONFIRM);
    explain.addClickListener(event -> {
      List<String> selected = keys.getSelectedItems().stream()
          .map(AccessKeyItem::internalReference).toList();
      if (selected.isEmpty()) {
        dialog.add(UIFactory.createBanner(tr("access.explanation.select.required")));
        return;
      }
      reauthentication.execute(requireUi(), "explain-access", () -> {
        try {
          AccessExplanation result = administration.explainSubject(requireUi(), subject, selected);
          dialog.close();
          openExplanation(subject, result);
        } catch (RuntimeException unavailable) {
          dialog.add(UIFactory.createBanner(tr("access.explanation.unavailable")));
        }
      }, key -> dialog.add(UIFactory.createBanner(tr(key))));
    });
    dialog.add(new VerticalLayout(filter, keys));
    dialog.getFooter().add(cancel, explain);
    dialog.setWidth("min(70rem, 95vw)");
    dialog.open();
    filter.focus();
  }

  private void openExplanation(AccessSubjectItem subject, AccessExplanation explanation) {
    Dialog dialog = new Dialog();
    dialog.setHeaderTitle(tr("access.explanation.title"));
    String summary = explanation.decision().allowed()
        ? tr("access.explanation.allowed") : tr("access.explanation.denied");
    var banner = UIFactory.createBanner(summary);
    Paragraph decisive = new Paragraph(tr("access.explanation.decisive",
        safeReasonText(explanation.decisiveCondition())));
    TextField filter = new TextField(tr("access.explanation.filter"));
    filter.setClearButtonVisible(true);
    Grid<ExplanationGate> gates = new Grid<>();
    gates.addColumn(ExplanationGate::category).setHeader(tr("access.explanation.gate.category"));
    gates.addColumn(ExplanationGate::state).setHeader(tr("access.common.state"));
    gates.addColumn(ExplanationGate::reason).setHeader(tr("access.explanation.reason"));
    List<ExplanationGate> gateItems = java.util.stream.Stream.of(
            explanation.decision().structuralGates().stream()
                .map(gate -> explanationGate("access.explanation.gate.structural", gate)),
            explanation.decision().entitlementGates().stream()
                .map(gate -> explanationGate("access.explanation.gate.plan", gate)),
            explanation.decision().assuranceGates().stream()
                .map(gate -> explanationGate("access.explanation.gate.authentication", gate)))
        .flatMap(stream -> stream).toList();
    gates.setItems(gateItems);
    gates.setAllRowsVisible(true);

    Grid<AuthorizationKeyResult> results = new Grid<>();
    results.addColumn(result -> keyName(result.key().code()))
        .setHeader(tr("access.key.name")).setFlexGrow(1);
    results.addColumn(this::keyResultState)
        .setHeader(tr("access.common.state")).setAutoWidth(true);
    results.addComponentColumn(result -> sourceSummary(result))
        .setHeader(tr("access.explanation.sources")).setFlexGrow(2);
    Runnable refresh = () -> results.setItems(explanation.decision().keyResults().stream()
        .filter(result -> contains(keyName(result.key().code()), filter.getValue()))
        .toList());
    filter.addValueChangeListener(event -> refresh.run());
    refresh.run();
    results.setHeight("24rem");
    String safeCopy = summary + " — " + safeReasonText(explanation.decisiveCondition());
    Button copy = UIFactory.createButton(
        "access.explanation.copy", RFWVaadinIcons.create(RFWIconEnum.CLIPBOARD));
    copy.addClickListener(event -> {
      requireUi().getPage().executeJs("navigator.clipboard.writeText($0)", safeCopy);
      UIFactory.showSuccessToast(tr("access.explanation.copied"));
    });
    Button close = UIFactory.createButton(RFWButtonActionEnum.CANCEL);
    close.addClickListener(event -> dialog.close());
    dialog.add(banner, decisive, new H2(tr("access.explanation.gates")), gates,
        new H2(tr("access.explanation.keys")), filter, results);
    dialog.getFooter().add(copy, close);
    dialog.setWidth("min(80rem, 96vw)");
    dialog.open();
    filter.focus();
  }

  private void refreshCategories() {
    TreeData<AccessCategoryItem> data = new TreeData<>();
    Map<String, AccessCategoryItem> byReference = new HashMap<>();
    snapshot.categories().forEach(item -> byReference.put(item.internalReference(), item));
    snapshot.categories().forEach(item -> data.addItem(
        item.parentInternalReference() == null ? null : byReference.get(item.parentInternalReference()),
        item));
    categoryTree.setDataProvider(new TreeDataProvider<>(data));
    categoryTree.expand(data.getRootItems());
  }

  private void applyFilter() {
    if (snapshot == null) return;
    String term = search.getValue().strip().toLowerCase(Locale.ROOT);
    groupGrid.setItems(snapshot.groups().stream()
        .filter(item -> contains(item.name(), term) || contains(item.description(), term))
        .toList());
    List<AccessSubjectItem> visibleSubjects = term.isEmpty()
        ? snapshot.subjects()
        : administration.searchSubjects(requireUi(), term, 200);
    subjectGrid.setItems(visibleSubjects);
    catalogGrid.setItems(snapshot.keys().stream()
        .filter(this::belongsToSelectedCategory)
        .filter(item -> contains(localized(item.nameI18nKey()), term)
            || contains(localized(item.descriptionI18nKey()), term))
        .sorted(Comparator.comparing(item -> localized(item.nameI18nKey())))
        .toList());
  }

  private boolean belongsToSelectedCategory(AccessKeyItem key) {
    if (selectedCategory == null) return true;
    String category = key.categoryInternalReference();
    while (category != null) {
      if (category.equals(selectedCategory.internalReference())) return true;
      String current = category;
      category = snapshot.categories().stream()
          .filter(item -> item.internalReference().equals(current))
          .map(AccessCategoryItem::parentInternalReference).findFirst().orElse(null);
    }
    return false;
  }

  private void openGroupEditor(AccessGroupItem group) {
    Dialog dialog = new Dialog();
    dialog.setHeaderTitle(group == null ? tr("access.group.create") : tr("access.group.edit"));
    TextField name = new TextField(tr("access.group.name"));
    name.setRequired(true);
    name.setMaxLength(160);
    TextArea description = new TextArea(tr("access.group.description"));
    description.setMaxLength(500);
    if (group != null) {
      name.setValue(group.name());
      description.setValue(nullSafe(group.description()));
    }
    FormLayout form = new FormLayout(name, description);
    form.setResponsiveSteps(
        new FormLayout.ResponsiveStep("0", 1), new FormLayout.ResponsiveStep("40em", 2));
    dialog.add(form);
    if (group != null) dialog.add(groupParticipants(group, dialog), groupMatrix(group));
    Button cancel = UIFactory.createButton(RFWButtonActionEnum.CANCEL);
    cancel.addClickListener(event -> dialog.close());
    Button save = UIFactory.createButton(RFWButtonActionEnum.SAVE);
    save.addClickListener(event -> {
      if (name.getValue().isBlank()) {
        name.setInvalid(true);
        name.setErrorMessage(tr("access.group.name.required"));
        name.focus();
        return;
      }
      requestPreview(
          () -> administration.previewGroup(requireUi(), snapshot.revision(),
              group == null ? null : group.id(), name.getValue(), description.getValue(),
              tr("access.audit.reason.ui")),
          () -> administration.saveGroup(requireUi(), snapshot.revision(),
              group == null ? null : group.id(), name.getValue(), description.getValue(),
              tr("access.audit.reason.ui")), dialog);
    });
    if (group != null) {
      Button deactivate = UIFactory.createButton(
          "access.group.deactivate", RFWVaadinIcons.create(RFWIconEnum.LOCK_CLOSED));
      deactivate.setEnabled(!group.protectedGroup()
          && group.state() == AccessAdministrationState.ACTIVE);
      deactivate.addClickListener(event -> {
        requestPreview(
            () -> administration.previewGroupDeactivation(requireUi(), snapshot.revision(),
                group.id(), tr("access.audit.reason.ui")),
            () -> administration.deactivateGroup(requireUi(), snapshot.revision(), group.id(),
                tr("access.audit.reason.ui")), dialog);
      });
      dialog.getFooter().add(deactivate);
    }
    dialog.getFooter().add(cancel, save);
    dialog.setWidth("min(60rem, 95vw)");
    dialog.open();
    name.focus();
  }

  private Component groupMatrix(AccessGroupItem group) {
    Grid<AccessKeyItem> matrix = new Grid<>();
    matrix.addColumn(item -> localized(item.nameI18nKey()))
        .setHeader(tr("access.key.name")).setFlexGrow(1);
    matrix.addColumn(item -> localized(item.descriptionI18nKey()))
        .setHeader(tr("access.key.description")).setFlexGrow(2);
    matrix.addColumn(item -> ruleState(group.id(), item.internalReference()))
        .setHeader(tr("access.common.state")).setAutoWidth(true);
    matrix.setItems(snapshot.keys());
    matrix.setHeight("18rem");
    Button editRule = UIFactory.createButton(RFWButtonActionEnum.EDIT);
    editRule.addClickListener(event -> matrix.asSingleSelect().getOptionalValue()
        .ifPresent(key -> openRuleEditor(AccessAdministrationOrigin.GROUP, group.id(), key)));
    return new VerticalLayout(new H2(tr("access.group.matrix")), matrix, editRule);
  }

  private Component groupParticipants(AccessGroupItem group, Dialog groupDialog) {
    Grid<AccessGroupSubjectItem> members = new Grid<>();
    members.addColumn(item -> subjectName(item.subjectId()))
        .setHeader(tr("access.subject.name")).setFlexGrow(1);
    members.addColumn(item -> stateText(item.state()))
        .setHeader(tr("access.common.state")).setAutoWidth(true);
    members.setItems(snapshot.groupSubjects().stream()
        .filter(item -> item.groupId() == group.id()).toList());
    members.setHeight("14rem");

    Button add = UIFactory.createButton(RFWButtonActionEnum.INSERT_ITEM);
    add.addClickListener(event -> openSubjectPicker(group, groupDialog));
    Button end = UIFactory.createButton(RFWButtonActionEnum.DELETE_ITEM);
    end.addClickListener(event -> members.asSingleSelect().getOptionalValue().ifPresent(member -> {
      requestPreview(
          () -> administration.previewGroupSubject(requireUi(), snapshot.revision(), false,
              member.id(), null, null, null, null, tr("access.audit.reason.ui")),
          () -> administration.changeGroupSubject(requireUi(), snapshot.revision(), false,
              member.id(), null, null, null, null, tr("access.audit.reason.ui")), groupDialog);
    }));
    return new VerticalLayout(
        new H2(tr("access.group.participants")), new HorizontalLayout(add, end), members);
  }

  private void openSubjectPicker(AccessGroupItem group, Dialog groupDialog) {
    Dialog dialog = new Dialog();
    TextField filter = new TextField(tr("access.center.search.label"));
    RFWPicker<AccessSubjectItem> picker = new RFWPicker<>("access.group.participant.picker.title");
    picker.setFilterContent(filter);
    picker.setClearAction(filter::clear);
    picker.getGrid().addColumn(AccessSubjectItem::displayName)
        .setHeader(tr("access.subject.name"));
    picker.getGrid().addColumn(item -> stateText(item.state()))
        .setHeader(tr("access.common.state"));
    picker.setSearchProvider(() -> CompletableFuture.completedFuture(
        administration.searchSubjects(requireUi(), filter.getValue(), 100)));
    picker.addSelectionListener(event -> {
      dialog.close();
      requestPreview(
          () -> administration.previewGroupSubject(requireUi(), snapshot.revision(), true, null,
              group.id(), event.getItem().subjectId(), null, null,
              tr("access.audit.reason.ui")),
          () -> administration.changeGroupSubject(requireUi(), snapshot.revision(), true, null,
              group.id(), event.getItem().subjectId(), null, null,
              tr("access.audit.reason.ui")), groupDialog);
    });
    dialog.add(picker);
    dialog.setWidth("min(55rem, 95vw)");
    dialog.open();
  }

  private void openRuleEditor(
      AccessAdministrationOrigin origin, long originId, AccessKeyItem initialKey) {
    Dialog dialog = new Dialog();
    dialog.setHeaderTitle(tr("access.rule.editor.title"));
    TextField keyFilter = new TextField(tr("access.center.search.label"));
    RFWPicker<AccessKeyItem> picker = new RFWPicker<>("access.rule.key.picker.title");
    picker.setFilterContent(keyFilter);
    picker.setClearAction(keyFilter::clear);
    picker.getGrid().addColumn(item -> localized(item.nameI18nKey()))
        .setHeader(tr("access.key.name"));
    picker.getGrid().addColumn(item -> localized(item.descriptionI18nKey()))
        .setHeader(tr("access.key.description"));
    picker.setSearchProvider(() -> CompletableFuture.completedFuture(snapshot.keys().stream()
        .filter(item -> contains(localized(item.nameI18nKey()), keyFilter.getValue())
            || contains(localized(item.descriptionI18nKey()), keyFilter.getValue()))
        .limit(100).toList()));
    final AccessKeyItem[] selectedKey = {initialKey};
    Select<AccessRuleEffect> effect = new Select<>();
    effect.setLabel(tr("access.rule.effect"));
    effect.setItems(AccessRuleEffect.PERMITIR, AccessRuleEffect.BLOQUEAR);
    effect.setItemLabelGenerator(item -> item == AccessRuleEffect.PERMITIR
        ? tr("access.rule.effect.permit") : tr("access.rule.effect.block"));
    effect.setValue(AccessRuleEffect.PERMITIR);
    DateTimePicker validFrom = new DateTimePicker(tr("access.rule.validFrom"));
    DateTimePicker validUntil = new DateTimePicker(tr("access.rule.validUntil"));
    TextArea reason = new TextArea(tr("access.rule.reason"));
    reason.setRequired(true);
    picker.addSelectionListener(event -> {
      selectedKey[0] = event.getItem();
      populateExistingRule(origin, originId, event.getItem().internalReference(),
          effect, validFrom, validUntil);
    });
    if (initialKey != null) {
      populateExistingRule(origin, originId, initialKey.internalReference(),
          effect, validFrom, validUntil);
    }
    FormLayout form = new FormLayout(effect, validFrom, validUntil, reason);
    form.setResponsiveSteps(
        new FormLayout.ResponsiveStep("0", 1), new FormLayout.ResponsiveStep("40em", 2));
    dialog.add(picker, form);
    Button cancel = UIFactory.createButton(RFWButtonActionEnum.CANCEL);
    cancel.addClickListener(event -> dialog.close());
    Button save = UIFactory.createButton(RFWButtonActionEnum.SAVE);
    save.addClickListener(event -> {
      if (selectedKey[0] == null || reason.getValue().isBlank()) {
        dialog.add(UIFactory.createBanner(tr("access.rule.error.incomplete")));
        return;
      }
      java.time.Instant from = validFrom.getValue() == null ? null
          : validFrom.getValue().toInstant(ZoneOffset.UTC);
      java.time.Instant until = validUntil.getValue() == null ? null
          : validUntil.getValue().toInstant(ZoneOffset.UTC);
      requestPreview(
          () -> administration.previewRule(requireUi(), snapshot.revision(), origin, originId,
              selectedKey[0].internalReference(), effect.getValue(), from, until,
              reason.getValue()),
          () -> administration.saveRule(requireUi(), snapshot.revision(), origin, originId,
              selectedKey[0].internalReference(), effect.getValue(), from, until,
              reason.getValue()), dialog);
    });
    Button deactivate = UIFactory.createButton(
        "access.rule.deactivate", RFWVaadinIcons.create(RFWIconEnum.LOCK_CLOSED));
    deactivate.addClickListener(event -> {
      var existing = selectedKey[0] == null ? java.util.Optional.<AccessRuleItem>empty()
          : findRule(origin, originId, selectedKey[0].internalReference());
      if (existing.isEmpty() || reason.getValue().isBlank()) {
        dialog.add(UIFactory.createBanner(tr("access.rule.error.deactivate")));
        return;
      }
      requestPreview(
          () -> administration.previewRuleDeactivation(requireUi(), snapshot.revision(),
              existing.get().id(), reason.getValue()),
          () -> administration.deactivateRule(requireUi(), snapshot.revision(), existing.get().id(),
              reason.getValue()), dialog);
    });
    dialog.getFooter().add(deactivate, cancel, save);
    dialog.setWidth("min(70rem, 95vw)");
    dialog.open();
  }

  private String ruleState(long groupId, String keyReference) {
    return snapshot.rules().stream()
        .filter(rule -> rule.origin() == AccessAdministrationOrigin.GROUP
            && rule.originId() == groupId
            && rule.accessKeyInternalReference().equals(keyReference))
        .findFirst().map(rule -> stateText(rule.state()))
        .orElse(tr("access.state.absent"));
  }

  private void populateExistingRule(
      AccessAdministrationOrigin origin, long originId, String keyReference,
      Select<AccessRuleEffect> effect, DateTimePicker validFrom, DateTimePicker validUntil) {
    findRule(origin, originId, keyReference).ifPresent(rule -> {
          effect.setValue(rule.effect());
          validFrom.setValue(rule.validFrom() == null ? null
              : java.time.LocalDateTime.ofInstant(rule.validFrom(), ZoneOffset.UTC));
          validUntil.setValue(rule.validUntil() == null ? null
              : java.time.LocalDateTime.ofInstant(rule.validUntil(), ZoneOffset.UTC));
        });
  }

  private java.util.Optional<AccessRuleItem> findRule(
      AccessAdministrationOrigin origin, long originId, String keyReference) {
    return snapshot.rules().stream()
        .filter(rule -> rule.origin() == origin && rule.originId() == originId
            && rule.accessKeyInternalReference().equals(keyReference))
        .findFirst();
  }

  private String subjectName(long subjectId) {
    return snapshot.subjects().stream()
        .filter(subject -> subject.subjectId() == subjectId)
        .map(AccessSubjectItem::displayName)
        .findFirst().orElse(tr("access.subject.unavailable"));
  }

  private void requestPreview(
      Supplier<AccessAdministrationPreview> supplier, Runnable confirmation, Dialog editor) {
    try {
      openPreview(supplier.get(), confirmation, editor);
    } catch (AccessAdministrationConflictException conflict) {
      editor.add(UIFactory.createBanner(tr("access.center.error.conflict")));
    } catch (RuntimeException rejected) {
      editor.add(UIFactory.createBanner(tr("access.center.error.rejected")));
    }
  }

  private void openPreview(
      AccessAdministrationPreview preview, Runnable confirmation, Dialog editor) {
    Dialog dialog = new Dialog();
    dialog.setCloseOnOutsideClick(false);
    dialog.setHeaderTitle(tr("access.preview.title"));
    H2 focusTitle = new H2(tr("access.preview.heading"));
    focusTitle.getElement().setAttribute("tabindex", "-1");
    Paragraph change = new Paragraph(tr("access.preview.change",
        changeText(preview.proposedChangeCode())));
    Paragraph administrators = new Paragraph(tr("access.preview.administrators",
        preview.eligibleAdministratorsBefore(), preview.eligibleAdministratorsAfter()));
    Paragraph baseline = new Paragraph(preview.protectedBaselineAffected()
        ? tr("access.preview.baseline.affected") : tr("access.preview.baseline.unaffected"));
    VerticalLayout body = new VerticalLayout(focusTitle, change, administrators, baseline);
    body.setPadding(false);
    if (!preview.confirmationAllowed()) {
      body.add(UIFactory.createBanner(safeReasonText(preview.safeReasonCode())));
    }
    Button cancel = UIFactory.createButton(RFWButtonActionEnum.CANCEL);
    cancel.addClickListener(event -> dialog.close());
    Button confirm = UIFactory.createButton(RFWButtonActionEnum.CONFIRM);
    confirm.setEnabled(preview.confirmationAllowed());
    confirm.addClickListener(event -> reauthentication.execute(requireUi(), "manage-access", () -> {
      try {
        confirmation.run();
        dialog.close();
        editor.close();
        reloadAfterMutation();
      } catch (AccessAdministrationConflictException conflict) {
        dialog.close();
        editor.close();
        load(requireUi());
        showPersistentError("access.center.error.conflict");
      } catch (RuntimeException rejected) {
        body.add(UIFactory.createBanner(tr("access.center.error.rejected")));
      }
    }, key -> body.add(UIFactory.createBanner(tr(key)))));
    dialog.add(body);
    dialog.getFooter().add(cancel, confirm);
    dialog.setWidth("min(42rem, 95vw)");
    dialog.open();
    focusTitle.getElement().callJsFunction("focus");
  }

  private ExplanationGate explanationGate(
      String categoryKey, br.com.rinos.app.api.module.access.vo.AuthorizationGateResult gate) {
    return new ExplanationGate(
        tr(categoryKey), gate.allowed() ? tr("access.state.allowed") : tr("access.state.blocked"),
        gate.allowed() ? tr("access.explanation.gate.satisfied")
            : safeReasonText(gate.safeReasonCode()));
  }

  private Component sourceSummary(AuthorizationKeyResult result) {
    VerticalLayout summary = new VerticalLayout();
    summary.setPadding(false);
    summary.setSpacing(false);
    if (result.missingPermit()) summary.add(new Span(tr("access.explanation.source.missing")));
    result.permitSources().forEach(source -> summary.add(sourceLine(source, true)));
    result.blockingSources().forEach(source -> summary.add(sourceLine(source, false)));
    result.ignoredSources().forEach(source -> summary.add(new Span(
        tr("access.explanation.source.ignored", sourceType(source), validity(source)))));
    return summary;
  }

  private Span sourceLine(AuthorizationRuleSource source, boolean permit) {
    return new Span(tr(permit ? "access.explanation.source.permit"
        : "access.explanation.source.block", sourceType(source), validity(source)));
  }

  private String sourceType(AuthorizationRuleSource source) {
    return tr("access.explanation.source.type."
        + source.type().name().toLowerCase(Locale.ROOT));
  }

  private String validity(AuthorizationRuleSource source) {
    String from = source.validFrom() == null ? tr("access.validity.unbounded.start")
        : source.validFrom().atZone(ZoneOffset.UTC).toLocalDateTime().toString();
    String until = source.validUntil() == null ? tr("access.validity.unbounded.end")
        : source.validUntil().atZone(ZoneOffset.UTC).toLocalDateTime().toString();
    return tr("access.validity.range", from, until);
  }

  private String keyName(String internalReference) {
    return snapshot.keys().stream()
        .filter(key -> key.internalReference().equals(internalReference))
        .map(key -> localized(key.nameI18nKey())).findFirst()
        .orElse(tr("access.key.translation.unavailable"));
  }

  private String keyResultState(AuthorizationKeyResult result) {
    if (!result.blockingSources().isEmpty()) return tr("access.state.blocked");
    if (result.missingPermit()) return tr("access.state.absent");
    return tr("access.state.allowed");
  }

  private static String safeReasonText(String code) {
    if (code == null) return tr("access.explanation.reason.unavailable");
    return switch (code) {
      case "ALL_STRUCTURAL_ENTITLEMENT_ASSURANCE_AND_KEYS_ALLOWED" ->
          tr("access.explanation.reason.allowed");
      case "ACL_KEY_BLOCKED" -> tr("access.explanation.reason.keyBlocked");
      case "ACL_KEY_MISSING", "ACL_DENIED" -> tr("access.explanation.reason.keyMissing");
      case "ACL_PLAN_REQUIRED", "ACL_PLAN_UNAVAILABLE", "ACL_PLAN_CONTEXT_INVALID" ->
          tr("access.explanation.reason.plan");
      case "ACL_ASSURANCE_REQUIRED", "ACL_ASSURANCE_UNAVAILABLE" ->
          tr("access.explanation.reason.authentication");
      case "ACL_IDENTITY_INACTIVE", "ACL_ASSOCIATION_INACTIVE" ->
          tr("access.explanation.reason.inactive");
      case "ACL_TENANT_UNAVAILABLE", "ACL_ASSOCIATION_UNAVAILABLE",
          "ACL_STRUCTURAL_UNAVAILABLE", "ACL_DECISION_UNAVAILABLE" ->
          tr("access.explanation.reason.context");
      case "ACL_CONTINUITY_WOULD_BE_LOST" -> tr("access.preview.reason.continuity");
      case "ACL_CONTINUITY_UNAVAILABLE" -> tr("access.preview.reason.unavailable");
      case "ACL_CONTEXT_CHANGED" -> tr("access.center.error.conflict");
      case "ACL_CHANGE_REJECTED" -> tr("access.center.error.rejected");
      default -> tr("access.explanation.reason.unavailable");
    };
  }

  private static String changeText(String code) {
    return switch (code) {
      case "ACCESS_GROUP_CREATE" -> tr("access.preview.change.groupCreate");
      case "ACCESS_GROUP_UPDATE" -> tr("access.preview.change.groupUpdate");
      case "ACCESS_GROUP_DEACTIVATE" -> tr("access.preview.change.groupDeactivate");
      case "ACCESS_GROUP_SUBJECT_ASSIGN" -> tr("access.preview.change.subjectAssign");
      case "ACCESS_GROUP_SUBJECT_END" -> tr("access.preview.change.subjectEnd");
      case "ACCESS_RULE_SAVE" -> tr("access.preview.change.ruleSave");
      case "ACCESS_RULE_DEACTIVATE" -> tr("access.preview.change.ruleDeactivate");
      default -> tr("access.preview.change.generic");
    };
  }

  private void reloadAfterMutation() {
    load(requireUi());
    UIFactory.showSuccessToast(tr("access.center.saved"));
  }

  private void showPersistentError(String key) {
    addComponentAtIndex(0, UIFactory.createBanner(tr(key)));
  }

  private UI requireUi() {
    return getUI().orElseGet(UI::getCurrent);
  }

  private static <T> void prepareGrid(Grid<T> grid) {
    grid.setWidthFull();
    grid.setMinHeight("20rem");
    grid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES);
  }

  private static boolean contains(String value, String term) {
    String safeTerm = term == null ? "" : term.strip().toLowerCase(Locale.ROOT);
    return safeTerm.isEmpty()
        || value != null && value.toLowerCase(Locale.ROOT).contains(safeTerm);
  }

  private static String localized(String key) {
    String value = tr(key);
    return value.equals(key) || value.equals("!" + key)
        ? tr("access.key.translation.unavailable") : value;
  }

  private static String stateText(AccessAdministrationState state) {
    return tr("access.state." + state.name().toLowerCase(Locale.ROOT));
  }

  private static String nullSafe(String value) {
    return value == null ? "" : value;
  }

  private record ExplanationGate(String category, String state, String reason) {
  }
}
