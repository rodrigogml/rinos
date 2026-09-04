package br.com.rinos.app.backend.module.account.repository;
import org.springframework.data.jpa.repository.JpaRepository; import br.com.rinos.app.backend.module.account.entity.AccountAuditEventEntity;
public interface AccountAuditEventRepository extends JpaRepository<AccountAuditEventEntity,Long>{}
