package org.folio.entitlement.mapper;

import static org.mapstruct.InjectionStrategy.CONSTRUCTOR;

import java.util.UUID;
import org.folio.common.domain.model.Dependency;
import org.folio.entitlement.domain.entity.ApplicationDependencyEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring", injectionStrategy = CONSTRUCTOR)
public interface ApplicationDependencyMapper {

  @Mapping(target = "tenantId", source = "tenantId")
  @Mapping(target = "applicationId", source = "applicationId")
  @Mapping(target = "applicationVersion", ignore = true)
  @Mapping(target = "applicationName", ignore = true)
  @Mapping(target = "parentName", source = "dependency.name")
  @Mapping(target = "parentVersion", source = "dependency.version")
  ApplicationDependencyEntity map(UUID tenantId, String applicationId, Dependency dependency);
}
