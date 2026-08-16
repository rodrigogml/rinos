package br.com.rinos.app.api.module.access.contributor;

import java.util.Collection;
import java.util.List;

import br.com.rinos.app.api.module.access.vo.AccessCategoryDescriptor;
import br.com.rinos.app.api.module.access.vo.AccessKeyDescriptor;

/** Publicação modular de chaves e categorias canônicas. */
public interface AccessKeyContributor {

  String moduleCode();

  Collection<AccessKeyDescriptor> accessKeys();

  default Collection<AccessCategoryDescriptor> categories() {
    return List.of();
  }
}
