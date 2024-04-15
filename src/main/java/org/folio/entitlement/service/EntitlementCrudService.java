package org.folio.entitlement.service;

import static org.apache.commons.lang3.BooleanUtils.isTrue;
import static org.folio.common.utils.CollectionUtils.mapItems;

import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.folio.common.domain.model.OffsetRequest;
import org.folio.entitlement.domain.dto.Entitlement;
import org.folio.entitlement.domain.entity.ApplicationDependencyEntity;
import org.folio.entitlement.domain.entity.EntitlementEntity;
import org.folio.entitlement.domain.entity.key.EntitlementKey;
import org.folio.entitlement.domain.entity.key.EntitlementModuleEntity;
import org.folio.entitlement.domain.model.EntitlementRequest;
import org.folio.entitlement.domain.model.ResultList;
import org.folio.entitlement.mapper.EntitlementMapper;
import org.folio.entitlement.repository.EntitlementModuleRepository;
import org.folio.entitlement.repository.EntitlementRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class EntitlementCrudService {

  private final EntitlementMapper entitlementMapper;
  private final ApplicationDependencyService dependencyService;
  private final EntitlementRepository entitlementRepository;
  private final EntitlementModuleRepository entitlementModuleRepository;

  /**
   * Retrieves all applications installed for the specified tenant.
   *
   * @param cqlQuery - the specific tenant
   * @param includeModules - include modules
   * @return a {@link ResultList} object with {@link EntitlementRequest} values
   */
  @Transactional(readOnly = true)
  public ResultList<Entitlement> findByQuery(String cqlQuery, Boolean includeModules, Integer limit, Integer offset) {
    var pageable = OffsetRequest.of(offset, limit);
    var appInstallEntities = entitlementRepository.findByCql(cqlQuery, pageable);
    var tenantEntitlements = mapItems(appInstallEntities.toList(),
      isTrue(includeModules)
        ? entity -> entitlementMapper.mapWithModules(entity, fetchEntitlementModules(entity))
        : entitlementMapper::map);
    return ResultList.of((int) appInstallEntities.getTotalElements(), tenantEntitlements);
  }

  /**
   * Retrieves entitlements by tenant identifier.
   *
   * @param tenantId - tenant identifier as {@link UUID}
   * @return tenant entitlements
   */
  @Transactional(readOnly = true)
  public List<Entitlement> findByTenantId(UUID tenantId) {
    var entitlementsByTenants = entitlementRepository.findByTenantId(tenantId);
    return mapItems(entitlementsByTenants, entitlementMapper::map);
  }

  @Transactional(readOnly = true)
  public List<Entitlement> findInstalledDependentEntitlements(String applicationId, UUID tenantId) {
    var dependencies = dependencyService.findAllByParentApplicationName(tenantId, applicationId);
    var dependentAppIds = mapItems(dependencies, ApplicationDependencyEntity::getApplicationId);

    var entitlements = entitlementRepository.findByTenantIdAndApplicationIdIn(tenantId, dependentAppIds);
    return mapItems(entitlements, entitlementMapper::map);
  }

  /**
   * Saves entitlement.
   *
   * @param entitlement - entitlement as {@link Entitlement}
   * @return {@link List} with saved to the database {@link Entitlement} objects
   */
  @Transactional
  public Entitlement save(Entitlement entitlement) {
    var entitlementEntity = entitlementMapper.map(entitlement);
    var savedEntity = entitlementRepository.save(entitlementEntity);
    return entitlementMapper.map(savedEntity);
  }

  /**
   * Deletes entitlement.
   *
   * @param entitlement - entitlement as {@link Entitlement} object
   */
  @Transactional
  public void delete(Entitlement entitlement) {
    var entitlementKey = EntitlementKey.of(entitlement.getTenantId(), entitlement.getApplicationId());
    var entitlementById = entitlementRepository.getReferenceById(entitlementKey);
    entitlementRepository.delete(entitlementById);
  }

  /**
   * Retrieves entitlements for {@link EntitlementRequest} value.
   *
   * @param request - entitlement request with tenant id and list of application identifiers
   * @return list of entitlements created for this request.
   */
  public List<Entitlement> getEntitlements(EntitlementRequest request) {
    var tenantId = request.getTenantId();
    var applicationIds = request.getApplications();
    var foundEntitlementEntities = entitlementRepository.findByTenantIdAndApplicationIdIn(tenantId, applicationIds);
    return mapItems(foundEntitlementEntities, entitlementMapper::map);
  }

  private List<EntitlementModuleEntity> fetchEntitlementModules(EntitlementEntity entitlementEntity) {
    return entitlementModuleRepository.findAllByApplicationIdAndTenantId(entitlementEntity.getApplicationId(),
      entitlementEntity.getTenantId());
  }
}
