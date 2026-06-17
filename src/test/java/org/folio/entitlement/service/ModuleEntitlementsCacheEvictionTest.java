package org.folio.entitlement.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;
import org.folio.entitlement.configuration.cache.ModuleEntitlementsCacheConfiguration;
import org.folio.entitlement.domain.dto.Entitlement;
import org.folio.entitlement.domain.entity.key.EntitlementModuleEntity;
import org.folio.entitlement.mapper.EntitlementModuleMapper;
import org.folio.entitlement.repository.EntitlementModuleRepository;
import org.folio.test.types.UnitTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

@UnitTest
@SpringJUnitConfig({
  ModuleEntitlementsCacheConfiguration.class,
  ModuleEntitlementsCacheProvider.class,
  EntitlementModuleService.class
})
class ModuleEntitlementsCacheEvictionTest {

  private static final String MODULE_ID = "mod-foo-1.0.0";
  private static final UUID TENANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
  private static final String APPLICATION_ID = "app-1.0.0";

  @MockitoBean private EntitlementModuleRepository repository;
  @MockitoBean private EntitlementModuleMapper mapper;
  @Autowired private ModuleEntitlementsCacheProvider provider;
  @Autowired private EntitlementModuleService service;
  @Autowired private CacheManager cacheManager;

  @Test
  void writeAffectingModule_evictsOnlyThatModulesEntry() {
    var entity = new EntitlementModuleEntity();
    entity.setModuleId(MODULE_ID);
    entity.setTenantId(TENANT_ID);
    entity.setApplicationId(APPLICATION_ID);
    when(repository.findAllByModuleId(any(String.class), any(Sort.class))).thenReturn(List.of(entity));
    when(mapper.map(any(EntitlementModuleEntity.class))).thenReturn(new Entitlement(APPLICATION_ID, TENANT_ID));

    var otherModuleId = "mod-bar-2.0.0";
    provider.getByModuleId(MODULE_ID);
    provider.getByModuleId(otherModuleId);
    assertThat(cache().get(MODULE_ID)).as("module under test cached").isNotNull();
    assertThat(cache().get(otherModuleId)).as("other module cached").isNotNull();

    service.deleteModuleEntitlement(MODULE_ID, TENANT_ID, APPLICATION_ID);

    assertThat(cache().get(MODULE_ID)).as("affected module evicted").isNull();
    assertThat(cache().get(otherModuleId)).as("unrelated module retained").isNotNull();
  }

  private org.springframework.cache.Cache cache() {
    return cacheManager.getCache(ModuleEntitlementsCacheConfiguration.MODULE_ENTITLEMENTS_CACHE);
  }
}
