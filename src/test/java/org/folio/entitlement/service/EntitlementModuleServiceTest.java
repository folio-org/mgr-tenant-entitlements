package org.folio.entitlement.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.common.utils.OkapiHeaders.MODULE_ID;
import static org.folio.entitlement.support.TestConstants.APPLICATION_ID;
import static org.folio.entitlement.support.TestConstants.TENANT_ID;
import static org.folio.entitlement.support.TestConstants.TENANT_NAME;
import static org.folio.entitlement.support.TestValues.entitlement;
import static org.folio.entitlement.support.TestValues.entitlementModuleEntity;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;
import org.folio.entitlement.domain.dto.Entitlement;
import org.folio.entitlement.domain.entity.key.EntitlementModuleEntity;
import org.folio.entitlement.domain.entity.key.EntitlementModuleKey;
import org.folio.entitlement.integration.folio.model.ModuleRequest;
import org.folio.entitlement.mapper.EntitlementModuleMapper;
import org.folio.entitlement.repository.EntitlementModuleRepository;
import org.folio.test.types.UnitTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class EntitlementModuleServiceTest {

  private static final EntitlementModuleKey MODULE_KEY = EntitlementModuleKey.of(MODULE_ID, TENANT_ID, APPLICATION_ID);
  private static final ModuleRequest MODULE_REQUEST = ModuleRequest.builder()
    .moduleId(MODULE_ID)
    .location("loc")
    .tenantId(TENANT_ID)
    .tenantName(TENANT_NAME)
    .applicationId(APPLICATION_ID)
    .build();

  @Mock private EntitlementModuleRepository repository;
  @Mock private EntitlementModuleMapper mapper;
  @Mock private ModuleEntitlementsCacheProvider cacheProvider;

  @InjectMocks private EntitlementModuleService service;

  private static List<Entitlement> threeEntitlements() {
    return List.of(
      new Entitlement("app-1.0.0", UUID.fromString("00000000-0000-0000-0000-000000000001")),
      new Entitlement("app-1.0.0", UUID.fromString("00000000-0000-0000-0000-000000000002")),
      new Entitlement("app-1.0.0", UUID.fromString("00000000-0000-0000-0000-000000000003")));
  }

  @Test
  void getModuleEntitlements_returnsFirstPage() {
    when(cacheProvider.getByModuleId(MODULE_ID)).thenReturn(threeEntitlements());

    var actual = service.getModuleEntitlements(MODULE_ID, 2, 0);

    assertThat(actual.getTotalRecords()).isEqualTo(3);
    assertThat(actual.getEntitlements()).hasSize(2);
  }

  @Test
  void getModuleEntitlements_returnsSecondPartialPage() {
    when(cacheProvider.getByModuleId(MODULE_ID)).thenReturn(threeEntitlements());

    var actual = service.getModuleEntitlements(MODULE_ID, 2, 2);

    assertThat(actual.getTotalRecords()).isEqualTo(3);
    assertThat(actual.getEntitlements()).hasSize(1);
  }

  @Test
  void getModuleEntitlements_offsetBeyondSize_returnsEmptyPageWithTotal() {
    when(cacheProvider.getByModuleId(MODULE_ID)).thenReturn(threeEntitlements());

    var actual = service.getModuleEntitlements(MODULE_ID, 10, 50);

    assertThat(actual.getTotalRecords()).isEqualTo(3);
    assertThat(actual.getEntitlements()).isEmpty();
  }

  @Test
  void getModuleEntitlements_emptyModule_returnsEmpty() {
    when(cacheProvider.getByModuleId(MODULE_ID)).thenReturn(List.of());

    var actual = service.getModuleEntitlements(MODULE_ID, 10, 0);

    assertThat(actual.getTotalRecords()).isZero();
    assertThat(actual.getEntitlements()).isEmpty();
  }

  @Test
  void findModuleEntitlement_positive() {
    when(repository.findAllByModuleIdAndTenantId(any(), any())).thenReturn(List.of(entitlementModuleEntity()));
    when(mapper.map(any(EntitlementModuleEntity.class))).thenReturn(entitlement());

    var actual = service.findAllModuleEntitlements(MODULE_ID, TENANT_ID);
    assertThat(actual).isNotNull();
  }

  @Test
  void save_positive() {
    when(mapper.map(any(ModuleRequest.class))).thenReturn(entitlementModuleEntity());

    service.save(MODULE_REQUEST);

    verify(repository).save(entitlementModuleEntity());
  }

  @Test
  void saveAll_positive() {
    when(mapper.map(anyString(), any(UUID.class), anyString())).thenReturn(entitlementModuleEntity());

    service.saveAll(TENANT_ID, APPLICATION_ID, List.of(MODULE_ID));

    verify(repository).saveAll(List.of(entitlementModuleEntity()));
  }

  @Test
  void deleteModuleEntitlement_positive() {
    when(mapper.mapKey(any(ModuleRequest.class))).thenReturn(MODULE_KEY);

    service.deleteModuleEntitlement(MODULE_REQUEST);

    verify(repository).deleteById(MODULE_KEY);
  }

  @Test
  void deleteAll_positive() {
    when(mapper.mapKey(anyString(), any(UUID.class), anyString())).thenReturn(MODULE_KEY);

    service.deleteAll(TENANT_ID, APPLICATION_ID, List.of(MODULE_ID));

    verify(repository).deleteAllById(List.of(MODULE_KEY));
  }
}
