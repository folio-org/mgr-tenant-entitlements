package org.folio.entitlement.mapper;

import static org.mapstruct.InjectionStrategy.CONSTRUCTOR;

import java.util.List;
import java.util.UUID;
import org.folio.entitlement.domain.dto.ApplicationFlow;
import org.folio.entitlement.domain.dto.EntitlementType;
import org.folio.entitlement.domain.entity.EntitlementFlowEntity;
import org.folio.entitlement.domain.entity.type.EntityExecutionStatus;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(
  componentModel = "spring",
  injectionStrategy = CONSTRUCTOR,
  imports = {EntityExecutionStatus.class}
)
public interface EntitlementFlowMapper {

  @Mapping(target = "stages", ignore = true)
  @Mapping(target = "id", source = "entitlementFlowId")
  ApplicationFlow map(EntitlementFlowEntity entity);

  List<ApplicationFlow> map(List<EntitlementFlowEntity> entity);

  @Mapping(target = "startedAt", ignore = true)
  @Mapping(target = "finishedAt", ignore = true)
  @Mapping(target = "applicationName", ignore = true)
  @Mapping(target = "applicationVersion", ignore = true)
  @Mapping(target = "flowId", source = "flowId")
  @Mapping(target = "status", expression = "java(EntityExecutionStatus.QUEUED)")
  @Mapping(target = "entitlementFlowId", expression = "java(UUID.randomUUID())")
  EntitlementFlowEntity mapWithStatusQueued(UUID tenantId, String applicationId, UUID flowId, EntitlementType type);
}
