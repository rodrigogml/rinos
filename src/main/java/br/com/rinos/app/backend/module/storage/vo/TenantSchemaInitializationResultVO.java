package br.com.rinos.app.backend.module.storage.vo;

/**
 * Resultado interno da observação e inicialização do schema de um tenant.
 *
 * @param createdNow indica que a chamada criou o schema físico antes de executar o init
 * @author Rodrigo Leitão
 * @since 2026-08-30
 */
public record TenantSchemaInitializationResultVO(boolean createdNow) {
}
