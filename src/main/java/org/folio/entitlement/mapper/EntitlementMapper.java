package org.folio.entitlement.mapper;

import static java.util.stream.Collectors.toList;
import static org.folio.common.utils.CollectionUtils.toStream;
import static org.mapstruct.InjectionStrategy.CONSTRUCTOR;

import java.util.List;
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
  EntitlementEntity map(UUID tenantId, String applicationId);

  @Mapping(target = "modules", ignore = true)
  Entitlement map(EntitlementEntity entity);

  @Mapping(target = "applicationName", ignore = true)
  @Mapping(target = "applicationVersion", ignore = true)
  EntitlementEntity map(Entitlement entitlement);

  default Entitlement mapWithModules(EntitlementEntity entity, List<EntitlementModuleEntity> modules) {
    var entitlementEntity = map(entity);
    var modulesList = toStream(modules)
      .map(EntitlementModuleEntity::getModuleId)
      .collect(toList());
    entitlementEntity.setModules(modulesList);

    return entitlementEntity;
  }
}
