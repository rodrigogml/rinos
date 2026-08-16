package br.com.rinos.app.backend.module.account.repository;
import java.util.*; import org.springframework.data.jpa.repository.JpaRepository; import br.com.rinos.app.backend.module.account.entity.AccountCreationIntentEntity;
public interface AccountCreationIntentRepository extends JpaRepository<AccountCreationIntentEntity,Long>{ Optional<AccountCreationIntentEntity> findByCreatorUserIdAndIdempotencyKey(Long creatorUserId,UUID key); Optional<AccountCreationIntentEntity> findByProtocolId(UUID protocolId); }
