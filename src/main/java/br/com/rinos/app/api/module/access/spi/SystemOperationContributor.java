package br.com.rinos.app.api.module.access.spi;

import java.util.Collection;

import br.com.rinos.app.api.module.access.vo.SystemOperationDescriptor;

/** Publica operações autônomas sem criar usuário ou membership técnico. */
public interface SystemOperationContributor {
  Collection<SystemOperationDescriptor> systemOperations();
}
