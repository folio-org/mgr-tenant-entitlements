package org.folio.entitlement.repository;

import java.util.UUID;
import org.folio.entitlement.domain.entity.AbstractFlowEntity;
import org.folio.entitlement.domain.entity.type.EntityExecutionStatus;
import org.folio.spring.cql.JpaCqlRepository;
import org.springframework.data.repository.NoRepositoryBean;

@NoRepositoryBean
public interface AbstractFlowRepository<T extends AbstractFlowEntity> extends JpaCqlRepository<T, UUID> {

  boolean existsAnyStageByFlowIdAndStatus(UUID flowId, EntityExecutionStatus status);
}
