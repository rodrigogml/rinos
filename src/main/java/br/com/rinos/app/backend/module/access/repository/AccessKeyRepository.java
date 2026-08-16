package br.com.rinos.app.backend.module.access.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import br.com.rinos.app.backend.module.access.entity.AccessKeyEntity;

/** Persistência dos descriptors de chave publicados pelos módulos. */
public interface AccessKeyRepository extends JpaRepository<AccessKeyEntity, Long> {
  Optional<AccessKeyEntity> findByCode(String code);
  List<AccessKeyEntity> findAllByOrderByCodeAsc();
}
