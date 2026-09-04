package br.com.rinos.app.backend.module.access.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;

import br.com.rinos.app.api.module.access.enums.AccessScope;
import br.com.rinos.app.api.module.access.spi.SystemOperationContributor;
import br.com.rinos.app.api.module.access.vo.SystemOperationDescriptor;

/** Registry fail-fast das origens sistêmicas publicadas pelos módulos. */
@Service
@org.springframework.context.annotation.Lazy
public class SystemOperationRegistryService {
  private final Map<SystemOperationKey, SystemOperationDescriptor> descriptors;

  public SystemOperationRegistryService(List<SystemOperationContributor> contributors) {
    Map<SystemOperationKey, SystemOperationDescriptor> values = new HashMap<>();
    contributors.stream().flatMap(contributor -> contributor.systemOperations().stream())
        .forEach(descriptor -> {
          SystemOperationKey key = new SystemOperationKey(
              descriptor.origin(), descriptor.operationCode(), descriptor.scope());
          if (values.putIfAbsent(key, descriptor) != null) {
            throw new IllegalStateException("duplicate system operation descriptor");
          }
        });
    descriptors = Map.copyOf(values);
  }

  public Optional<SystemOperationDescriptor> find(
      String origin, String operationCode, AccessScope scope) {
    return Optional.ofNullable(descriptors.get(new SystemOperationKey(origin, operationCode, scope)));
  }

  private record SystemOperationKey(String origin, String operationCode, AccessScope scope) {
  }
}
