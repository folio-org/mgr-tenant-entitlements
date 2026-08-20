package org.folio.entitlement.repository;

import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.UUID;
import org.folio.entitlement.domain.entity.AbstractFlowEntity;
import org.folio.entitlement.domain.entity.type.EntityExecutionStatus;
import org.folio.spring.cql.JpaCqlRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.NoRepositoryBean;
import org.springframework.data.repository.query.Param;

@NoRepositoryBean
public interface AbstractFlowRepository<T extends AbstractFlowEntity> extends JpaCqlRepository<T, UUID> {

  boolean existsAnyStageByFlowIdAndStatusExcluding(UUID flowId, EntityExecutionStatus status, UUID excludedStageId);

  /**
   * Compare-and-set on the flow status: the status check is a part of the statement, so a status set concurrently by
   * another writer cannot be overwritten. {@code finishedAt} is passed in because a bulk update bypasses
   * {@link org.hibernate.annotations.UpdateTimestamp}.
   */
  @Modifying
  @Query("UPDATE #{#entityName} e SET e.status = :status, e.finishedAt = :finishedAt "
    + "WHERE e.id = :id AND e.status IN :currentStatuses")
  int updateStatusIfCurrentIn(@Param("id") UUID id,
    @Param("status") EntityExecutionStatus status,
    @Param("currentStatuses") Collection<EntityExecutionStatus> currentStatuses,
    @Param("finishedAt") ZonedDateTime finishedAt);
}
