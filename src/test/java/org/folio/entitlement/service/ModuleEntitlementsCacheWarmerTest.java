package org.folio.entitlement.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.entitlement.configuration.cache.ModuleEntitlementsCacheConfiguration.MODULE_ENTITLEMENTS_CACHE;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;
import org.folio.entitlement.domain.dto.Entitlement;
import org.folio.entitlement.domain.entity.key.EntitlementModuleEntity;
import org.folio.entitlement.mapper.EntitlementModuleMapper;
import org.folio.entitlement.repository.EntitlementModuleRepository;
import org.folio.test.types.UnitTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.data.domain.Sort;

@UnitTest
@ExtendWith(MockitoExtension.class)
class ModuleEntitlementsCacheWarmerTest {

  @Mock private EntitlementModuleRepository repository;
  @Mock private EntitlementModuleMapper mapper;
  @Mock private CacheManager moduleEntitlementsCacheManager;
  @Mock private Cache cache;
  @InjectMocks private ModuleEntitlementsCacheWarmer warmer;

  private static EntitlementModuleEntity entity(String moduleId, UUID tenantId) {
    var e = new EntitlementModuleEntity();
    e.setModuleId(moduleId);
    e.setTenantId(tenantId);
    e.setApplicationId("app-1.0.0");
    return e;
  }

  @Test
  void warmUp_populatesOneEntryPerDistinctModule() {
    var t1 = UUID.fromString("00000000-0000-0000-0000-000000000001");
    var t2 = UUID.fromString("00000000-0000-0000-0000-000000000002");
    when(moduleEntitlementsCacheManager.getCache(MODULE_ENTITLEMENTS_CACHE)).thenReturn(cache);
    when(repository.findAll(any(Sort.class))).thenReturn(List.of(
      entity("mod-a-1.0.0", t1), entity("mod-a-1.0.0", t2), entity("mod-b-2.0.0", t1)));
    when(mapper.map(any(EntitlementModuleEntity.class))).thenReturn(new Entitlement("app-1.0.0", t1));

    warmer.warmUp();

    var keys = ArgumentCaptor.forClass(String.class);
    verify(cache, times(2)).put(keys.capture(), any());
    assertThat(keys.getAllValues()).containsExactlyInAnyOrder("mod-a-1.0.0", "mod-b-2.0.0");
  }
}
