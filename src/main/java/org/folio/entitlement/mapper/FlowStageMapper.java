package org.folio.entitlement.mapper;

import static org.mapstruct.InjectionStrategy.CONSTRUCTOR;

import org.folio.entitlement.domain.dto.FlowStage;
import org.folio.entitlement.domain.entity.FlowStageEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring", injectionStrategy = CONSTRUCTOR)
public interface FlowStageMapper {

  /**
   * Converts given dto to {@link FlowStageEntity} object.
   *
   * @param flowStage - stage dto as {@link FlowStage} object
   * @return converted {@link FlowStageEntity} object
   */
  @Mapping(target = "stageName", source = "name")
  FlowStageEntity map(FlowStage flowStage);

  /**
   * Converts given entity to {@link FlowStage} object.
   *
   * @param entity - stage entity as {@link FlowStageEntity} object
   * @return converted {@link FlowStage} object
   */
  @Mapping(target = "name", source = "stageName")
  FlowStage map(FlowStageEntity entity);
}
