package org.folio.entitlement.service;

import static org.folio.common.utils.CollectionUtils.mapItems;
import static org.folio.entitlement.domain.entity.key.EntitlementModuleEntity.SORT_BY_TENANT;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.folio.common.domain.model.OffsetRequest;
import org.folio.entitlement.domain.dto.Entitlement;
import org.folio.entitlement.domain.dto.Entitlements;
import org.folio.entitlement.domain.entity.key.EntitlementModuleEntity;
import org.folio.entitlement.domain.entity.key.EntitlementModuleKey;
import org.folio.entitlement.integration.folio.model.ModuleRequest;
import org.folio.entitlement.mapper.EntitlementModuleMapper;
import org.folio.entitlement.repository.EntitlementModuleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@RequiredArgsConstructor
public class EntitlementModuleService {

  private final EntitlementModuleRepository repository;
  private final EntitlementModuleMapper mapper;

  @Transactional(readOnly = true)
  public Entitlements getModuleEntitlements(String moduleId, Integer limit, Integer offset) {
    var page = repository.findAllByModuleId(moduleId, OffsetRequest.of(offset, limit, SORT_BY_TENANT));
    var entitlements = mapItems(page.getContent(), mapper::map);

    return new Entitlements()
      .totalRecords((int) page.getTotalElements())
      .entitlements(entitlements);
  }

  @Transactional(readOnly = true)
  public List<Entitlement> findAllModuleEntitlements(String moduleId, UUID tenantId) {
    return repository.findAllByModuleIdAndTenantId(moduleId, tenantId).stream()
      .map(mapper::map)
      .collect(Collectors.toList());
  }

  public void save(ModuleRequest moduleRequest) {
    var entity = mapper.map(moduleRequest);
    repository.save(entity);
  }

  public void saveAll(UUID tenantId, String applicationId, List<String> modules) {
    var entities = toEntities(tenantId, applicationId, modules);
    repository.saveAll(entities);
  }

  public void deleteModuleEntitlement(ModuleRequest moduleRequest) {
    var key = mapper.mapKey(moduleRequest);
    repository.deleteById(key);
  }

  public void deleteAll(UUID tenantId, String applicationId, List<String> modules) {
    var keys = toModuleKeys(tenantId, applicationId, modules);
    repository.deleteAllById(keys);
  }

  private List<EntitlementModuleKey> toModuleKeys(UUID tenantId, String applicationId, List<String> modules) {
    return mapItems(modules, module -> mapper.mapKey(module, tenantId, applicationId));
  }

  private List<EntitlementModuleEntity> toEntities(UUID tenantId, String applicationId, List<String> modules) {
    return mapItems(modules, module -> mapper.map(module, tenantId, applicationId));
  }
}
