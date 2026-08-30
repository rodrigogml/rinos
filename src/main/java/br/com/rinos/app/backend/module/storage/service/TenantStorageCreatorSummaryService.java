package br.com.rinos.app.backend.module.storage.service;

import java.util.Objects;

import org.springframework.stereotype.Service;

import br.com.rinos.app.api.module.storage.enums.TenantStorageAvailabilityEnum;
import br.com.rinos.app.api.module.storage.enums.TenantStorageCreatorStatusEnum;
import br.com.rinos.app.api.module.storage.vo.TenantStorageCreatorSummaryVO;
import br.com.rinos.app.api.module.storage.vo.TenantStorageReadinessSnapshotVO;

/**
 * Reduz a fotografia interna de prontidão ao conjunto de estados não técnicos permitido ao criador da conta.
 *
 * @author Rodrigo Leitão
 * @since 2026-08-30
 */
@Service
public class TenantStorageCreatorSummaryService {

  /**
   * Converte uma fotografia interna em resumo público sem propagar motivo, versão ou localização.
   *
   * @param snapshot fotografia de prontidão, obrigatória
   * @return resumo público com um dos quatro estados previstos para o criador
   * @throws NullPointerException quando a fotografia não for informada
   */
  public TenantStorageCreatorSummaryVO summarize(TenantStorageReadinessSnapshotVO snapshot) {
    Objects.requireNonNull(snapshot, "snapshot must not be null");
    return new TenantStorageCreatorSummaryVO(statusFor(snapshot), snapshot.observedAt());
  }

  private static TenantStorageCreatorStatusEnum statusFor(TenantStorageReadinessSnapshotVO snapshot) {
    if (!snapshot.sourceAvailable() || !snapshot.tenantKnown()) {
      return TenantStorageCreatorStatusEnum.ATTENTION;
    }
    if (snapshot.ready()) {
      return TenantStorageCreatorStatusEnum.READY;
    }
    return switch (snapshot.availability()) {
      case WAITING -> TenantStorageCreatorStatusEnum.WAITING;
      case MIGRATING -> TenantStorageCreatorStatusEnum.PREPARING;
      case READY, ATTENTION, INACTIVE -> TenantStorageCreatorStatusEnum.ATTENTION;
    };
  }
}
