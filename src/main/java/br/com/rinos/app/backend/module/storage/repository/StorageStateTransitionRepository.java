package br.com.rinos.app.backend.module.storage.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import br.com.rinos.app.backend.module.storage.entity.StorageStateTransitionEntity;

/** Persiste o histórico append-only de estados estruturais. */
public interface StorageStateTransitionRepository
    extends JpaRepository<StorageStateTransitionEntity, Long> {
}
