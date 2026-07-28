package br.com.rinos.app.config;

import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.config.ConfigDataEnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySource;

/**
 * Restringe o bootstrap do Spring ao {@code application.properties} explícito na raiz de execução.
 *
 * <p>Argumentos, propriedades JVM, variáveis de ambiente, JSON do Spring, profiles e imports não participam da
 * configuração do Rinos. O arquivo também não pode interpolar valores de outra origem.</p>
 *
 * @author Rodrigo Leitão
 * @since 2026-07-27
 */
public final class ExclusiveConfigurationEnvironmentPostProcessor
    implements EnvironmentPostProcessor, Ordered {

  static final String BOOTSTRAP_PROPERTY_SOURCE = "rinosExclusiveConfigurationBootstrap";
  private static final String CONFIGURATION_PROPERTIES_SOURCE = "configurationProperties";
  private static final Set<String> FORBIDDEN_KEYS = Set.of(
      "spring.application.json",
      "spring.config.activate.on-profile",
      "spring.config.additional-location",
      "spring.config.import",
      "spring.config.location",
      "spring.config.name",
      "spring.config.on-not-found",
      "spring.profiles.active",
      "spring.profiles.include");

  private final Path configurationFile;

  /**
   * Cria o processador para o arquivo obrigatório na raiz de execução.
   */
  public ExclusiveConfigurationEnvironmentPostProcessor() {
    this(Path.of("application.properties"));
  }

  ExclusiveConfigurationEnvironmentPostProcessor(Path configurationFile) {
    this.configurationFile = configurationFile.toAbsolutePath().normalize();
  }

  /**
   * Executa depois das fontes externas padrão e antes do carregamento de Config Data.
   *
   * @return prioridade imediatamente anterior ao carregador oficial do Spring Boot
   */
  @Override
  public int getOrder() {
    return ConfigDataEnvironmentPostProcessor.ORDER - 1;
  }

  /**
   * Remove fontes externas e fixa o único arquivo aceito pelo carregador oficial.
   *
   * @param environment ambiente ainda em preparação
   * @param application aplicação que será inicializada
   * @throws IllegalStateException quando o arquivo não existe ou tenta importar outra fonte
   */
  @Override
  public void postProcessEnvironment(ConfigurableEnvironment environment,
      SpringApplication application) {
    validateConfigurationFile();
    application.setAddCommandLineProperties(false);

    MutablePropertySources propertySources = environment.getPropertySources();
    List<String> namesToRemove = new ArrayList<>();
    for (PropertySource<?> propertySource : propertySources) {
      if (!CONFIGURATION_PROPERTIES_SOURCE.equals(propertySource.getName())) {
        namesToRemove.add(propertySource.getName());
      }
    }
    namesToRemove.forEach(propertySources::remove);

    Map<String, Object> bootstrap = Map.of(
        "spring.config.location", configurationFile.toUri().toString(),
        "spring.config.additional-location", "",
        "spring.config.import", "",
        "spring.profiles.active", "",
        "spring.profiles.include", "");
    propertySources.addFirst(new MapPropertySource(BOOTSTRAP_PROPERTY_SOURCE, bootstrap));
  }

  private void validateConfigurationFile() {
    if (!Files.isRegularFile(configurationFile)) {
      throw new IllegalStateException(
          "Arquivo obrigatório não encontrado: " + configurationFile);
    }
    try {
      String content = Files.readString(configurationFile, StandardCharsets.UTF_8);
      if (content.contains("${")) {
        throw new IllegalStateException(
            "application.properties não pode interpolar valores de outras fontes.");
      }
      Properties properties = new Properties();
      properties.load(new StringReader(content));
      for (String key : properties.stringPropertyNames()) {
        if (isForbiddenKey(key)) {
          throw new IllegalStateException(
              "application.properties contém chave de bootstrap proibida: " + key);
        }
      }
    } catch (IOException exception) {
      throw new IllegalStateException(
          "Não foi possível ler o arquivo obrigatório: " + configurationFile, exception);
    }
  }

  private static boolean isForbiddenKey(String key) {
    return FORBIDDEN_KEYS.contains(key) || key.startsWith("spring.profiles.group.");
  }
}
