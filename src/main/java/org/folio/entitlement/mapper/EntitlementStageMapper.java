package org.folio.entitlement.mapper;

import static org.mapstruct.InjectionStrategy.CONSTRUCTOR;

import org.folio.entitlement.domain.dto.EntitlementStage;
import org.folio.entitlement.domain.entity.EntitlementStageEntity;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring", injectionStrategy = CONSTRUCTOR)
public interface EntitlementStageMapper {

  /**
   * Converts given dto to {@link EntitlementStageEntity} object.
   *
   * @param entitlementStage - stage dto as {@link EntitlementStage} object
   * @return converted {@link EntitlementStageEntity} object
   */
  EntitlementStageEntity map(EntitlementStage entitlementStage);

  /**
   * Converts given entity to {@link EntitlementStage} object.
   *
   * @param entity - stage entity as {@link EntitlementStageEntity} object
   * @return converted {@link EntitlementStage} object
   */
  EntitlementStage map(EntitlementStageEntity entity);
}
