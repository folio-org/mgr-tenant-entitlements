package org.folio.entitlement.mapper;

import static org.mapstruct.InjectionStrategy.CONSTRUCTOR;

import java.util.UUID;
import org.folio.entitlement.domain.entity.ApplicationDependencyEntity;
import org.folio.entitlement.integration.am.model.Dependency;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring", injectionStrategy = CONSTRUCTOR)
public interface ApplicationDependencyMapper {

  @Mapping(target = "tenantId", source = "tenantId")
  @Mapping(target = "applicationId", source = "applicationId")
  @Mapping(target = "parentName", source = "dependency.name")
  @Mapping(target = "parentVersion", source = "dependency.version")
  ApplicationDependencyEntity map(UUID tenantId, String applicationId, Dependency dependency);
}
