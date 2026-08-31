package br.com.rinos.app.backend.module.account.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import br.com.rinos.app.backend.module.account.entity.AccountCreationIntentEntity;

/** Acessa intenções idempotentes persistidas do cadastro de conta. */
public interface AccountCreationIntentRepository extends JpaRepository<AccountCreationIntentEntity, Long> {

  /**
   * Localiza a intenção do criador para resolver replay ou conflito de idempotência.
   *
   * @param creatorUserId identidade que iniciou o pedido
   * @param key chave idempotente recebida na fronteira
   * @return intenção existente, quando houver
   */
  Optional<AccountCreationIntentEntity> findByCreatorUserIdAndIdempotencyKey(
      Long creatorUserId,
      UUID key);

  /**
   * Localiza uma intenção pelo protocolo público já aceito.
   *
   * @param protocolId protocolo opaco da criação
   * @return intenção existente, quando houver
   */
  Optional<AccountCreationIntentEntity> findByProtocolId(UUID protocolId);

  /**
   * Localiza a intenção que autoriza o despacho para uma conta específica.
   *
   * @param accountId identificador interno da conta
   * @return intenção existente, quando houver
   */
  Optional<AccountCreationIntentEntity> findByAccountId(Long accountId);
}
