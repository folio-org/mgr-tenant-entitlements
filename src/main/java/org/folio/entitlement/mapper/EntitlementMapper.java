package org.folio.entitlement.mapper;

import static org.mapstruct.InjectionStrategy.CONSTRUCTOR;

import java.util.UUID;
import org.folio.entitlement.domain.dto.Entitlement;
import org.folio.entitlement.domain.entity.EntitlementEntity;
import org.folio.entitlement.domain.entity.key.EntitlementModuleEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring", injectionStrategy = CONSTRUCTOR)
public interface EntitlementMapper {

  @Mapping(target = "applicationName", ignore = true)
  @Mapping(target = "applicationVersion", ignore = true)
  @Mapping(target = "modules", ignore = true)
  EntitlementEntity map(UUID tenantId, String applicationId);

  @Mapping(target = "modules", ignore = true)
  Entitlement map(EntitlementEntity entity);

  @Mapping(target = "applicationName", ignore = true)
  @Mapping(target = "applicationVersion", ignore = true)
  @Mapping(target = "modules", ignore = true)
  EntitlementEntity map(Entitlement entitlement);

  Entitlement mapWithModules(EntitlementEntity entity);

  default String mapModule(EntitlementModuleEntity module) {
    return module.getModuleId();
  }
}
