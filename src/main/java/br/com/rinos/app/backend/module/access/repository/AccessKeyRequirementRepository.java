package br.com.rinos.app.backend.module.access.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import br.com.rinos.app.backend.module.access.entity.AccessKeyRequirementEntity;

/** Persistência da rastreabilidade exata das chaves. */
public interface AccessKeyRequirementRepository
    extends JpaRepository<AccessKeyRequirementEntity, Long> {
  List<AccessKeyRequirementEntity> findByAccessKeyId(Long accessKeyId);
  void deleteByAccessKeyId(Long accessKeyId);
}
