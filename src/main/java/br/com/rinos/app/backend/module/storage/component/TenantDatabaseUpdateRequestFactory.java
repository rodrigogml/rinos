package br.com.rinos.app.backend.module.storage.component;

import java.util.List;
import java.util.Objects;

import javax.sql.DataSource;

import org.springframework.stereotype.Component;

import br.eng.rodrigogml.rfw.database.config.DatabaseUpdatePropertiesConfig;
import br.eng.rodrigogml.rfw.database.vo.DatabaseUpdateRequestVO;

/**
 * Cria requisições explícitas de atualização exclusivamente para o catálogo de tenant.
 *
 * <p>O updater automático da RFW pertence somente ao schema global. Esta fábrica mantém a fronteira física dos
 * tenants, sem reutilizar a lista de locations configurada para o bootstrap global.</p>
 *
 * @author Rodrigo Leitão
 * @since 2026-08-29
 */
@Component
public class TenantDatabaseUpdateRequestFactory {

  /** Location único permitido para atualização de schemas tenant. */
  public static final String TENANT_UPDATE_LOCATION = "classpath:db/tenant/update/";

  private final DatabaseUpdatePropertiesConfig databaseUpdateProperties;

  /**
   * Usa somente o timeout fixo de lock já configurado para o updater, nunca suas locations globais.
   *
   * @param databaseUpdateProperties propriedades fixas da infraestrutura RFW
   */
  public TenantDatabaseUpdateRequestFactory(DatabaseUpdatePropertiesConfig databaseUpdateProperties) {
    this.databaseUpdateProperties = Objects.requireNonNull(databaseUpdateProperties,
        "databaseUpdateProperties must not be null");
  }

  /**
   * Monta a requisição isolada de atualização para o datasource de um único tenant.
   *
   * @param tenantDataSource datasource que já seleciona exatamente um schema tenant
   * @return requisição com somente {@value #TENANT_UPDATE_LOCATION}
   * @throws NullPointerException quando o datasource for nulo
   */
  public DatabaseUpdateRequestVO create(DataSource tenantDataSource) {
    return new DatabaseUpdateRequestVO(
        Objects.requireNonNull(tenantDataSource, "tenantDataSource must not be null"),
        List.of(TENANT_UPDATE_LOCATION),
        databaseUpdateProperties.getLockTimeout());
  }
}
