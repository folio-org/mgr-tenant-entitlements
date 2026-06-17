package org.folio.entitlement.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
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
import org.springframework.data.domain.Sort;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

@UnitTest
@SpringJUnitConfig({ModuleEntitlementsCacheConfiguration.class, ModuleEntitlementsCacheProvider.class})
class ModuleEntitlementsCacheProviderTest {

  private static final String MODULE_ID = "mod-foo-1.0.0";

  @MockitoBean private EntitlementModuleRepository repository;
  @MockitoBean private EntitlementModuleMapper mapper;
  @Autowired private ModuleEntitlementsCacheProvider provider;

  private static EntitlementModuleEntity entity() {
    var e = new EntitlementModuleEntity();
    e.setModuleId(MODULE_ID);
    e.setTenantId(UUID.fromString("00000000-0000-0000-0000-000000000001"));
    e.setApplicationId("app-1.0.0");
    return e;
  }

  private static Entitlement entitlement() {
    return new Entitlement("app-1.0.0", UUID.fromString("00000000-0000-0000-0000-000000000001"));
  }

  @Test
  void getByModuleId_returnsMappedUnmodifiableList() {
    when(repository.findAllByModuleId(eq(MODULE_ID), any(Sort.class))).thenReturn(List.of(entity()));
    when(mapper.map(any(EntitlementModuleEntity.class))).thenReturn(entitlement());

    var result = provider.getByModuleId(MODULE_ID);

    assertThat(result).containsExactly(entitlement());
    assertThatThrownBy(() -> result.add(entitlement())).isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void getByModuleId_secondCallIsServedFromCache() {
    when(repository.findAllByModuleId(eq(MODULE_ID), any(Sort.class))).thenReturn(List.of(entity()));
    when(mapper.map(any(EntitlementModuleEntity.class))).thenReturn(entitlement());

    provider.getByModuleId(MODULE_ID);
    provider.getByModuleId(MODULE_ID);

    verify(repository, times(1)).findAllByModuleId(eq(MODULE_ID), any(Sort.class));
  }
}
