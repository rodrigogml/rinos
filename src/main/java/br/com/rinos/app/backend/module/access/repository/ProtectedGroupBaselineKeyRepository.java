package br.com.rinos.app.backend.module.access.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import br.com.rinos.app.backend.module.access.entity.ProtectedGroupBaselineKeyEntity;
import br.com.rinos.app.backend.module.access.entity.ProtectedGroupBaselineKeyId;

/** Persistência das inclusões explícitas de chaves em baselines. */
public interface ProtectedGroupBaselineKeyRepository
    extends JpaRepository<ProtectedGroupBaselineKeyEntity, ProtectedGroupBaselineKeyId> {
  List<ProtectedGroupBaselineKeyEntity> findByIdBaselineId(Long baselineId);
}
