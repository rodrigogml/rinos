package br.com.rinos.app.api.vo;

import br.com.rinos.app.api.enums.ExternalIdentityManagementStatusEnum;

/**
 * Resultado sem detalhes do proprietário ou da chave externa.
 *
 * @param status resultado público
 * @author Rodrigo Leitão
 * @since 2026-08-10
 */
public record ExternalIdentityManagementResultVO(
    ExternalIdentityManagementStatusEnum status) {
}
