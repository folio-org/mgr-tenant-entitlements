package org.folio.entitlement.mapper;

import static org.mapstruct.InjectionStrategy.CONSTRUCTOR;

import java.util.List;
import java.util.UUID;
import org.folio.entitlement.domain.dto.ApplicationFlow;
import org.folio.entitlement.domain.dto.EntitlementType;
import org.folio.entitlement.domain.entity.ApplicationFlowEntity;
import org.folio.entitlement.domain.entity.type.EntityExecutionStatus;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(
  componentModel = "spring",
  injectionStrategy = CONSTRUCTOR,
  imports = {EntityExecutionStatus.class}
)
public interface ApplicationFlowMapper {

  @Mapping(target = "stages", ignore = true)
  ApplicationFlow map(ApplicationFlowEntity entity);

  List<ApplicationFlow> map(List<ApplicationFlowEntity> entity);

  @Mapping(target = "startedAt", ignore = true)
  @Mapping(target = "finishedAt", ignore = true)
  @Mapping(target = "applicationName", ignore = true)
  @Mapping(target = "applicationVersion", ignore = true)
  @Mapping(target = "flowId", source = "flowId")
  @Mapping(target = "id", expression = "java(UUID.randomUUID())")
  @Mapping(target = "status", expression = "java(EntityExecutionStatus.QUEUED)")
  ApplicationFlowEntity mapWithStatusQueued(UUID tenantId, String applicationId, UUID flowId, EntitlementType type);
}
