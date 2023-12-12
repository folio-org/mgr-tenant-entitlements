package org.folio.entitlement.mapper;

import static org.mapstruct.InjectionStrategy.CONSTRUCTOR;

import java.util.UUID;
import org.folio.entitlement.domain.dto.Entitlement;
import org.folio.entitlement.domain.entity.key.EntitlementModuleEntity;
import org.folio.entitlement.domain.entity.key.EntitlementModuleKey;
import org.folio.entitlement.integration.folio.model.ModuleRequest;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring", injectionStrategy = CONSTRUCTOR)
public interface EntitlementModuleMapper {

  @Mapping(target = "modules", ignore = true)
  Entitlement map(EntitlementModuleEntity module);

  EntitlementModuleEntity map(String moduleId, UUID tenantId, String applicationId);

  EntitlementModuleEntity map(ModuleRequest moduleRequest);

  EntitlementModuleKey mapKey(ModuleRequest moduleRequest);

  EntitlementModuleKey mapKey(String moduleId, UUID tenantId, String applicationId);
}
