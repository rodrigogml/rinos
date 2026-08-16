package br.com.rinos.app.backend.module.account.repository;
import org.springframework.data.jpa.repository.JpaRepository; import br.com.rinos.app.backend.module.account.entity.AccountOutboxEventEntity;
public interface AccountOutboxEventRepository extends JpaRepository<AccountOutboxEventEntity,Long>{}
