package br.com.rinos.app.backend.module.storage.component;

import java.io.IOException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import javax.sql.DataSource;

import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.jdbc.datasource.init.ScriptException;
import org.springframework.stereotype.Component;

import br.eng.rodrigogml.rfw.exception.RFWDatabaseUpdateErrorCategoryEnum;
import br.eng.rodrigogml.rfw.exception.RFWDatabaseUpdateException;

/**
 * Localiza e executa exclusivamente o catálogo de init de um novo schema tenant.
 *
 * <p>O init não utiliza o atualizador da RFW, pois ele cria um schema já na versão vigente. As atualizações
 * progressivas posteriores são responsabilidade exclusiva do orquestrador RFW e recebem apenas o catálogo
 * {@code db/tenant/update}.</p>
 *
 * @author Rodrigo Leitão
 * @since 2026-08-30
 */
@Component
public class TenantSchemaInitScriptComponent {
  private static final String TENANT_INIT_PATTERN = "classpath*:db/tenant/init/*.sql";

  private final ResourcePatternResolver resourcePatternResolver;

  /**
   * Cria o executor do catálogo de init distribuído com a aplicação.
   *
   * @param resourcePatternResolver resolvedor Spring de recursos empacotados
   */
  public TenantSchemaInitScriptComponent(ResourcePatternResolver resourcePatternResolver) {
    this.resourcePatternResolver = Objects.requireNonNull(resourcePatternResolver,
        "resourcePatternResolver must not be null");
  }

  /**
   * Executa todos os scripts de init em ordem lexical no datasource já selecionado para o tenant.
   *
   * @param tenantDataSource datasource que seleciona somente o schema recém-criado
   * @throws RFWDatabaseUpdateException quando o catálogo não for localizável ou um script falhar
   */
  public void execute(DataSource tenantDataSource) {
    Objects.requireNonNull(tenantDataSource, "tenantDataSource must not be null");
    List<Resource> scripts = loadScripts();
    try {
      new ResourceDatabasePopulator(scripts.toArray(Resource[]::new)).execute(tenantDataSource);
    } catch (ScriptException exception) {
      throw new RFWDatabaseUpdateException(RFWDatabaseUpdateErrorCategoryEnum.EXECUTION,
          "Não foi possível executar o init do schema tenant.", exception);
    }
  }

  private List<Resource> loadScripts() {
    try {
      List<Resource> scripts = Arrays.stream(resourcePatternResolver.getResources(TENANT_INIT_PATTERN))
          .sorted(Comparator.comparing(this::requiredFileName))
          .toList();
      if (scripts.isEmpty() || scripts.stream().map(this::requiredFileName).distinct().count() != scripts.size()) {
        throw new RFWDatabaseUpdateException(RFWDatabaseUpdateErrorCategoryEnum.SCRIPT_DISCOVERY,
            "O catálogo de init de tenant está ausente ou possui scripts ambíguos.");
      }
      return scripts;
    } catch (IOException exception) {
      throw new RFWDatabaseUpdateException(RFWDatabaseUpdateErrorCategoryEnum.SCRIPT_DISCOVERY,
          "Não foi possível localizar o catálogo de init de tenant.", exception);
    }
  }

  private String requiredFileName(Resource resource) {
    String fileName = resource.getFilename();
    if (fileName == null || fileName.isBlank()) {
      throw new RFWDatabaseUpdateException(RFWDatabaseUpdateErrorCategoryEnum.SCRIPT_DISCOVERY,
          "O catálogo de init de tenant contém um script sem nome válido.");
    }
    return fileName;
  }
}
