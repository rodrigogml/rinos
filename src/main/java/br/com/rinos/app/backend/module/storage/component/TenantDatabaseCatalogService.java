package br.com.rinos.app.backend.module.storage.component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.List;
import java.util.Objects;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import br.com.rinos.app.backend.module.storage.vo.TenantDatabaseCatalogVO;
import br.com.rinos.app.backend.module.storage.vo.TenantDatabaseUpdateScriptVO;
import br.eng.rodrigogml.rfw.database.config.DatabaseUpdatePropertiesConfig;
import br.eng.rodrigogml.rfw.database.service.DatabaseUpdateScriptDiscoveryService;
import br.eng.rodrigogml.rfw.database.service.DatabaseUpdateScriptValidationService;
import br.eng.rodrigogml.rfw.database.vo.DatabaseUpdateScriptVO;
import br.eng.rodrigogml.rfw.exception.RFWDatabaseUpdateErrorCategoryEnum;
import br.eng.rodrigogml.rfw.exception.RFWDatabaseUpdateException;

/**
 * Descobre, valida e evidencia o catálogo distribuído de atualizações estruturais dos tenants.
 *
 * <p>O catálogo é deliberadamente independente das locations globais. Todo script é validado antes de seu hash ser
 * comparado ao histórico, impedindo que conteúdo inválido seja aceito como evidência estrutural.</p>
 *
 * @author Rodrigo Leitão
 * @since 2026-08-29
 */
@Component
@ConditionalOnProperty(prefix = "rfw.database.update", name = "enabled", havingValue = "true")
public class TenantDatabaseCatalogService {

  private static final String HASH_ALGORITHM = "SHA-256";
  private static final Duration CATALOG_VALIDATION_TIMEOUT = Duration.ofSeconds(1);

  private final DatabaseUpdateScriptDiscoveryService scriptDiscoveryService;
  private final DatabaseUpdateScriptValidationService scriptValidationService;
  private final List<String> locations;

  /**
   * Cria o leitor do catálogo oficial de tenant.
   *
   * @param scriptDiscoveryService descoberta de scripts fornecida pela RFW
   * @param scriptValidationService validação sintática e de marco de versão fornecida pela RFW
   */
  @Autowired
  public TenantDatabaseCatalogService(DatabaseUpdateScriptDiscoveryService scriptDiscoveryService,
      DatabaseUpdateScriptValidationService scriptValidationService) {
    this(scriptDiscoveryService, scriptValidationService,
        List.of(TenantDatabaseUpdateRequestFactory.TENANT_UPDATE_LOCATION));
  }

  /**
   * Cria leitor para um catálogo controlado, usado pelos testes de compatibilidade estrutural.
   *
   * @param scriptDiscoveryService descoberta de scripts fornecida pela RFW
   * @param scriptValidationService validação sintática e de marco de versão fornecida pela RFW
   * @param locations locations exclusivas que compõem o catálogo controlado
   */
  TenantDatabaseCatalogService(DatabaseUpdateScriptDiscoveryService scriptDiscoveryService,
      DatabaseUpdateScriptValidationService scriptValidationService, List<String> locations) {
    this.scriptDiscoveryService = Objects.requireNonNull(scriptDiscoveryService,
        "scriptDiscoveryService must not be null");
    this.scriptValidationService = Objects.requireNonNull(scriptValidationService,
        "scriptValidationService must not be null");
    this.locations = List.copyOf(Objects.requireNonNull(locations, "locations must not be null"));
    if (this.locations.isEmpty() || this.locations.stream().anyMatch(location -> location == null || location.isBlank())) {
      throw new IllegalArgumentException("locations must contain only nonblank values");
    }
  }

  /**
   * Retorna a fotografia validada do catálogo de updates tenant distribuído com a aplicação.
   *
   * @return scripts ordenados, hashes e versão alvo inequívoca
   * @throws br.eng.rodrigogml.rfw.exception.RFWDatabaseUpdateException quando o catálogo estiver ausente, duplicado ou inválido
   */
  public TenantDatabaseCatalogVO inspect() {
    DatabaseUpdatePropertiesConfig properties = new DatabaseUpdatePropertiesConfig();
    properties.setEnabled(true);
    properties.setLocations(locations);
    properties.setLockTimeout(CATALOG_VALIDATION_TIMEOUT);

    List<DatabaseUpdateScriptVO> scripts = scriptDiscoveryService.discover(properties);
    scriptValidationService.validateExecutableScripts(scripts);
    List<TenantDatabaseUpdateScriptVO> descriptors = scripts.stream()
        .map(this::toDescriptor)
        .toList();
    if (descriptors.isEmpty()) {
      throw new RFWDatabaseUpdateException(RFWDatabaseUpdateErrorCategoryEnum.SCRIPT_DISCOVERY,
          "O catálogo de atualização de tenant não contém scripts executáveis.");
    }
    return new TenantDatabaseCatalogVO(descriptors, descriptors.getLast().version());
  }

  private TenantDatabaseUpdateScriptVO toDescriptor(DatabaseUpdateScriptVO script) {
    String content = scriptValidationService.readContent(script);
    return new TenantDatabaseUpdateScriptVO(script.fileName(), script.version(), hash(content));
  }

  private static byte[] hash(String content) {
    try {
      return MessageDigest.getInstance(HASH_ALGORITHM).digest(content.getBytes(StandardCharsets.UTF_8));
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 is not available", exception);
    }
  }
}
