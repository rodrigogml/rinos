package br.com.rinos.app.backend.module.access.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import br.com.rinos.app.backend.module.access.entity.AccessRuleHistoryEntity;

/** Escrita append-only e consulta cronológica do histórico de regras. */
public interface AccessRuleHistoryRepository
    extends JpaRepository<AccessRuleHistoryEntity, Long> {
  List<AccessRuleHistoryEntity> findByAccessRuleIdOrderByOccurredAtAsc(Long accessRuleId);
}
