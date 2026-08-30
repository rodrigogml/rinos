package br.com.rinos.app.backend.module.storage.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import br.com.rinos.app.backend.module.storage.entity.StorageAuditEventEntity;

/** Persiste auditoria sanitizada de operações e consultas estruturais. */
public interface StorageAuditEventRepository extends JpaRepository<StorageAuditEventEntity, Long> {
}
