package br.com.rinos.app.backend.module.account.repository;
import java.util.*; import org.springframework.data.jpa.repository.JpaRepository; import br.com.rinos.app.backend.module.account.entity.AccountEntity;
public interface AccountRepository extends JpaRepository<AccountEntity,Long>{ Optional<AccountEntity> findByPublicId(UUID publicId); Optional<AccountEntity> findByTenantId(Long tenantId); }
