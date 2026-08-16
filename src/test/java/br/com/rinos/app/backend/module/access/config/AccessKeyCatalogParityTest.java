package br.com.rinos.app.backend.module.access.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import br.com.rinos.app.api.module.access.enums.AccessKeyStatus;
import br.com.rinos.app.api.module.access.vo.AccessKeyDescriptor;
import br.com.rinos.app.api.module.access.vo.AccessKeyRequirement;
import br.com.rinos.app.backend.module.access.component.AccessControlKeyContributor;
import br.com.rinos.app.backend.module.access.service.AccessKeyRegistryService;

class AccessKeyCatalogParityTest {

  private static final Path SPECS = Path.of("docs", "specs");
  private static final Path CATALOG = SPECS.resolve(
      Path.of("access-control", "contracts", "access-key-catalog.md"));
  private static final Pattern REQUIREMENT_DEFINITION = Pattern.compile(
      "\\*\\*(FR-[A-Z0-9]+(?:-[A-Z0-9]+)+)\\*\\*\\s*:");
  private static final Pattern CATALOG_KEY_ROW = Pattern.compile(
      "^\\| `((?:global|tenant)\\.[^`]+\\.[^`]+)` \\|");
  private static final Pattern RANGE = Pattern.compile("^(.*-)(\\d+)\\.\\.(\\d+)$");
  private static final Pattern SELECTION = Pattern.compile("^(.*-)(\\d+(?:/\\d+)+)$");
  private static final Pattern NUMERIC_REQUIREMENT = Pattern.compile("^(.*-)(\\d+)$");

  @Test
  void runtimeCatalog_shouldMatchEveryDocumentedDescriptorAndExactRequirement() throws IOException {
    Map<String, String> requirementOwners = requirementOwners();
    Map<String, DocumentedKey> documented = documentedKeys(requirementOwners);

    try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
      context.register(
          InitialModuleAccessKeyConfiguration.class,
          AccessControlKeyContributor.class,
          AccessKeyRegistryService.class);
      context.refresh();
      AccessKeyRegistryService registry = context.getBean(AccessKeyRegistryService.class);

      Map<String, DocumentedKey> runtime = new LinkedHashMap<>();
      for (AccessKeyDescriptor descriptor : registry.accessKeys()) {
        assertThat(descriptor.status()).isEqualTo(AccessKeyStatus.ACTIVE);
        assertThat(descriptor.nameI18nKey())
            .isEqualTo("access.key." + descriptor.code() + ".name");
        assertThat(descriptor.descriptionI18nKey())
            .isEqualTo("access.key." + descriptor.code() + ".description");
        runtime.put(descriptor.code(), new DocumentedKey(
            descriptor.code(),
            descriptor.ownerModule(),
            descriptor.categoryCode(),
            descriptor.minimumAdministrative(),
            descriptor.sourceRequirements()));
      }

      assertThat(registry.categories()).hasSize(12);
      assertThat(runtime).hasSize(90).containsExactlyInAnyOrderEntriesOf(documented);
    }
  }

  private static Map<String, String> requirementOwners() throws IOException {
    Map<String, String> owners = new HashMap<>();
    try (Stream<Path> directories = Files.list(SPECS)) {
      for (Path directory : directories.filter(Files::isDirectory).toList()) {
        Path spec = directory.resolve("spec.md");
        if (!Files.isRegularFile(spec)) {
          continue;
        }
        Matcher matcher = REQUIREMENT_DEFINITION.matcher(Files.readString(spec));
        while (matcher.find()) {
          String previous = owners.putIfAbsent(matcher.group(1), directory.getFileName().toString());
          if (previous != null && !previous.equals(directory.getFileName().toString())) {
            throw new IllegalStateException("requirement has multiple owners: " + matcher.group(1));
          }
        }
      }
    }
    return Map.copyOf(owners);
  }

  private static Map<String, DocumentedKey> documentedKeys(
      Map<String, String> requirementOwners) throws IOException {
    Map<String, DocumentedKey> keys = new LinkedHashMap<>();
    for (String line : Files.readAllLines(CATALOG)) {
      if (!CATALOG_KEY_ROW.matcher(line).find()) {
        continue;
      }
      String[] columns = line.split("\\|", -1);
      String requirementsExpression = columns[5].strip();
      if (requirementsExpression.equals("nenhum")) {
        continue;
      }
      String code = columns[1].strip().replace("`", "");
      String owner = columns[3].strip();
      String category = columns[4].strip().replace("`", "");
      boolean minimum = columns.length > 7 && columns[6].strip().equals("sim");
      Set<AccessKeyRequirement> requirements = new LinkedHashSet<>();
      for (String expression : requirementsExpression.split(",")) {
        for (String requirementId : expand(expression.strip(), requirementOwners.keySet())) {
          String feature = requirementOwners.get(requirementId);
          if (feature == null) {
            throw new IllegalStateException("unknown requirement: " + requirementId);
          }
          requirements.add(new AccessKeyRequirement(feature, requirementId));
        }
      }
      DocumentedKey duplicate = keys.put(code, new DocumentedKey(
          code, owner, category, minimum, Set.copyOf(requirements)));
      if (duplicate != null) {
        throw new IllegalStateException("duplicated documented access key: " + code);
      }
    }
    return Map.copyOf(keys);
  }

  private static Collection<String> expand(String expression, Set<String> knownRequirements) {
    if (expression.endsWith("*")) {
      String prefix = expression.substring(0, expression.length() - 1);
      return sorted(knownRequirements.stream().filter(id -> id.startsWith(prefix)).toList(), expression);
    }
    Matcher range = RANGE.matcher(expression);
    if (range.matches()) {
      int first = Integer.parseInt(range.group(2));
      int last = Integer.parseInt(range.group(3));
      List<String> matches = new ArrayList<>();
      for (String requirement : knownRequirements) {
        Matcher numeric = NUMERIC_REQUIREMENT.matcher(requirement);
        if (numeric.matches() && numeric.group(1).equals(range.group(1))) {
          int number = Integer.parseInt(numeric.group(2));
          if (number >= first && number <= last) {
            matches.add(requirement);
          }
        }
      }
      return sorted(matches, expression);
    }
    Matcher selection = SELECTION.matcher(expression);
    if (selection.matches()) {
      List<String> selected = new ArrayList<>();
      for (String number : selection.group(2).split("/")) {
        selected.add(selection.group(1) + number);
      }
      return requireKnown(selected, knownRequirements, expression);
    }
    return requireKnown(List.of(expression), knownRequirements, expression);
  }

  private static List<String> sorted(List<String> values, String expression) {
    if (values.isEmpty()) {
      throw new IllegalStateException("requirement expression has no match: " + expression);
    }
    return values.stream().sorted().distinct().toList();
  }

  private static List<String> requireKnown(
      List<String> values, Set<String> knownRequirements, String expression) {
    if (!knownRequirements.containsAll(values)) {
      throw new IllegalStateException("requirement expression is unresolved: " + expression);
    }
    return values;
  }

  private record DocumentedKey(
      String code,
      String owner,
      String category,
      boolean minimum,
      Set<AccessKeyRequirement> requirements) {
  }
}
