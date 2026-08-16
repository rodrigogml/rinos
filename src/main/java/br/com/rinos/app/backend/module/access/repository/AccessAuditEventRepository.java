package br.com.rinos.app.backend.module.access.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import br.com.rinos.app.backend.module.access.entity.AccessAuditEventEntity;

/** Persistência append-only dos eventos administrativos minimizados. */
public interface AccessAuditEventRepository extends JpaRepository<AccessAuditEventEntity, Long> {
  List<AccessAuditEventEntity> findByCorrelationIdOrderByOccurredAtAsc(String correlationId);
}
