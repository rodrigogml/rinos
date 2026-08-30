package br.com.rinos.app.api.module.storage.port;

import java.util.UUID;

import br.com.rinos.app.api.module.storage.vo.TenantStorageReadinessSnapshotVO;

/**
 * Consulta a prontidão estrutural de tenant sem conceder autorização ou abrir conexão funcional.
 *
 * @author Rodrigo Leitão
 * @since 2026-08-30
 */
public interface TenantStorageReadinessPort {

  /**
   * Inspeciona a fotografia atual do armazenamento do tenant solicitado.
   *
   * @param tenantPublicId identificador público do tenant, obrigatório
   * @return fotografia segura; falhas de leitura retornam uma fotografia não pronta
   * @throws NullPointerException quando o identificador não for informado
   */
  TenantStorageReadinessSnapshotVO inspect(UUID tenantPublicId);
}
