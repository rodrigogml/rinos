package br.com.rinos.app.backend.module.account.repository;
import java.util.*; import org.springframework.data.jpa.repository.JpaRepository; import br.com.rinos.app.backend.module.account.entity.TenantEntity;
public interface TenantRepository extends JpaRepository<TenantEntity,Long>{ Optional<TenantEntity> findByPublicId(UUID publicId); }
