package org.folio.entitlement.mapper;

import static org.mapstruct.InjectionStrategy.CONSTRUCTOR;

import org.folio.common.domain.model.Dependency;
import org.folio.entitlement.domain.entity.PlatformApplicationDependencyEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring", injectionStrategy = CONSTRUCTOR)
public interface PlatformApplicationDependencyMapper {

  @Mapping(target = "applicationId", source = "applicationId")
  @Mapping(target = "applicationVersion", ignore = true)
  @Mapping(target = "applicationName", ignore = true)
  @Mapping(target = "parentName", source = "dependency.name")
  @Mapping(target = "parentVersion", source = "dependency.version")
  @Mapping(target = "optional", source = "dependency.optional", defaultValue = "false")
  PlatformApplicationDependencyEntity map(String applicationId, Dependency dependency);
}
