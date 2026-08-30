package br.com.rinos.app.backend.module.storage.component;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import javax.sql.DataSource;

import org.springframework.stereotype.Component;

import br.com.rinos.app.backend.module.storage.vo.TenantDatabaseCatalogVO;
import br.com.rinos.app.backend.module.storage.vo.TenantDatabaseMigrationEvidenceVO;
import br.com.rinos.app.backend.module.storage.vo.TenantDatabaseUpdateScriptVO;
import br.eng.rodrigogml.rfw.database.service.DatabaseVersionService;
import br.eng.rodrigogml.rfw.database.vo.DatabaseVersionVO;
import br.eng.rodrigogml.rfw.exception.RFWDatabaseUpdateErrorCategoryEnum;
import br.eng.rodrigogml.rfw.exception.RFWDatabaseUpdateException;

/**
 * Confirma que um schema tenant tem a estrutura, a versão e o histórico compatíveis com o catálogo atual.
 *
 * <p>A validação falha fechada: ausência do schema, marcador inválido, versão inesperada, evidência adulterada ou
 * lacuna posterior ao baseline impedem o uso do tenant. O resultado positivo não abre contexto funcional; ele apenas
 * fornece a fotografia validada ao worker responsável pela transição de prontidão.</p>
 *
 * @author Rodrigo Leitão
 * @since 2026-08-29
 */
@Component
public class TenantDatabaseStructureVerifier {

  private static final String BASELINE_KEY = "tenant.schema.baseline";

  private final DatabaseVersionService databaseVersionService;
  private final TenantDatabaseCatalogService catalogService;

  /**
   * Cria o verificador estrutural do tenant.
   *
   * @param databaseVersionService leitor RFW da view de versão do schema
   * @param catalogService leitor do catálogo tenant distribuído
   */
  public TenantDatabaseStructureVerifier(DatabaseVersionService databaseVersionService,
      TenantDatabaseCatalogService catalogService) {
    this.databaseVersionService = Objects.requireNonNull(databaseVersionService,
        "databaseVersionService must not be null");
    this.catalogService = Objects.requireNonNull(catalogService, "catalogService must not be null");
  }

  /**
   * Valida o tenant contra a versão esperada e as evidências de updates posteriores ao seu baseline de criação.
   *
   * @param tenantDataSource datasource do único tenant a conferir
   * @param expectedVersion versão exata registrada globalmente para o código atual
   * @param migrationEvidence evidências históricas do tenant, sem dados funcionais ou segredos
   * @return catálogo que foi efetivamente usado na decisão
   * @throws RFWDatabaseUpdateException quando o tenant estiver ausente, incompatível, adulterado ou com lacuna conhecida
   */
  public TenantDatabaseCatalogVO verify(DataSource tenantDataSource, String expectedVersion,
      Collection<TenantDatabaseMigrationEvidenceVO> migrationEvidence) {
    Objects.requireNonNull(tenantDataSource, "tenantDataSource must not be null");
    Objects.requireNonNull(migrationEvidence, "migrationEvidence must not be null");
    DatabaseVersionVO expected = version(expectedVersion, "A versão esperada do tenant é inválida.");
    TenantDatabaseCatalogVO catalog = catalogService.inspect();
    if (!catalog.targetVersion().equals(expected)) {
      throw incompatible("A versão esperada do tenant não corresponde ao catálogo distribuído.");
    }

    DatabaseVersionVO observed = databaseVersionService.readCurrentVersion(tenantDataSource);
    DatabaseVersionVO baseline = readBaseline(tenantDataSource);
    if (baseline.compareTo(observed) > 0 || !catalogContains(catalog, baseline)) {
      throw incompatible("O baseline estrutural do tenant é desconhecido ou incompatível.");
    }
    if (!observed.equals(expected)) {
      throw incompatible("A versão estrutural observada no tenant não é a versão esperada.");
    }

    verifyEvidence(catalog, baseline, observed, migrationEvidence);
    return catalog;
  }

  private DatabaseVersionVO readBaseline(DataSource tenantDataSource) {
    try (Connection connection = tenantDataSource.getConnection();
        PreparedStatement statement = connection.prepareStatement("""
            SELECT bootstrapValue
            FROM core_tenantBootstrap
            WHERE bootstrapKey = ?
            """)) {
      statement.setString(1, BASELINE_KEY);
      try (ResultSet result = statement.executeQuery()) {
        if (!result.next()) {
          throw incompatible("O tenant não possui um baseline estrutural válido.");
        }
        String baseline = result.getString(1);
        if (baseline == null || baseline.isBlank() || result.next()) {
          throw incompatible("O tenant não possui um baseline estrutural válido.");
        }
        return version(baseline, "O baseline estrutural do tenant é inválido.");
      }
    } catch (SQLException exception) {
      throw new RFWDatabaseUpdateException(RFWDatabaseUpdateErrorCategoryEnum.VERSION_CONSISTENCY,
          "Não foi possível validar o baseline estrutural do tenant.", exception);
    }
  }

  private void verifyEvidence(TenantDatabaseCatalogVO catalog, DatabaseVersionVO baseline,
      DatabaseVersionVO observed, Collection<TenantDatabaseMigrationEvidenceVO> evidence) {
    Map<DatabaseVersionVO, TenantDatabaseUpdateScriptVO> scriptsByVersion = new HashMap<>();
    for (TenantDatabaseUpdateScriptVO script : catalog.scripts()) {
      scriptsByVersion.put(script.version(), script);
    }

    Set<DatabaseVersionVO> evidencedVersions = new HashSet<>();
    for (TenantDatabaseMigrationEvidenceVO entry : evidence) {
      if (!evidencedVersions.add(entry.version())) {
        throw incompatible("O histórico de migração do tenant contém versões duplicadas.");
      }
      TenantDatabaseUpdateScriptVO expectedScript = scriptsByVersion.get(entry.version());
      if (expectedScript == null || !expectedScript.fileName().equals(entry.fileName())
          || !expectedScript.matchesHash(entry.contentHash())) {
        throw incompatible("O histórico de migração do tenant contém script desconhecido ou adulterado.");
      }
    }

    for (TenantDatabaseUpdateScriptVO script : catalog.scripts()) {
      if (script.version().compareTo(baseline) > 0 && script.version().compareTo(observed) <= 0
          && !evidencedVersions.contains(script.version())) {
        throw incompatible("O histórico de migração do tenant possui lacuna posterior ao baseline.");
      }
    }
  }

  private static boolean catalogContains(TenantDatabaseCatalogVO catalog, DatabaseVersionVO version) {
    return catalog.scripts().stream().anyMatch(script -> script.version().equals(version));
  }

  private static DatabaseVersionVO version(String value, String message) {
    try {
      return new DatabaseVersionVO(value == null ? null : value.trim());
    } catch (IllegalArgumentException | NullPointerException exception) {
      throw new RFWDatabaseUpdateException(RFWDatabaseUpdateErrorCategoryEnum.VERSION_CONSISTENCY, message, exception);
    }
  }

  private static RFWDatabaseUpdateException incompatible(String message) {
    return new RFWDatabaseUpdateException(RFWDatabaseUpdateErrorCategoryEnum.VERSION_CONSISTENCY, message);
  }
}
