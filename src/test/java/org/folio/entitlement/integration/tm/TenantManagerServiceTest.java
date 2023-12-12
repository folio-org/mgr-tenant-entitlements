package org.folio.entitlement.integration.tm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.folio.entitlement.domain.model.ResultList.asSinglePage;
import static org.folio.entitlement.domain.model.ResultList.empty;
import static org.folio.entitlement.support.TestConstants.OKAPI_TOKEN;
import static org.folio.entitlement.support.TestConstants.TENANT_ID;
import static org.folio.entitlement.support.TestConstants.TENANT_NAME;
import static org.folio.entitlement.support.TestValues.tenant;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import feign.FeignException.BadRequest;
import feign.FeignException.NotFound;
import jakarta.persistence.EntityNotFoundException;
import org.folio.entitlement.integration.IntegrationException;
import org.folio.test.types.UnitTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class TenantManagerServiceTest {

  @InjectMocks private TenantManagerService tenantManagerService;
  @Mock private TenantManagerClient tenantManagerClient;

  @AfterEach
  void tearDown() {
    verifyNoMoreInteractions(tenantManagerClient);
  }

  @Test
  void findTenant_positive() {
    when(tenantManagerClient.getTenantById(TENANT_ID, OKAPI_TOKEN)).thenReturn(tenant());
    var found = tenantManagerService.findTenant(TENANT_ID, OKAPI_TOKEN);
    assertThat(found).isEqualTo(tenant());
  }

  @Test
  void findTenant_negative_tenantNotFound() {
    when(tenantManagerClient.getTenantById(TENANT_ID, OKAPI_TOKEN)).thenThrow(NotFound.class);
    assertThatThrownBy(() -> tenantManagerService.findTenant(TENANT_ID, OKAPI_TOKEN))
      .isInstanceOf(EntityNotFoundException.class)
      .hasMessage("Tenant is not found: " + TENANT_ID);
  }

  @Test
  void findTenant_negative_badRequestError() {
    when(tenantManagerClient.getTenantById(TENANT_ID, OKAPI_TOKEN)).thenThrow(BadRequest.class);
    assertThatThrownBy(() -> tenantManagerService.findTenant(TENANT_ID, OKAPI_TOKEN))
      .isInstanceOf(IntegrationException.class)
      .hasMessage("Failed to retrieve tenant: " + TENANT_ID);
  }

  @Test
  void findTenantByName_positive() {
    when(tenantManagerClient.queryTenantsByName(TENANT_NAME, OKAPI_TOKEN)).thenReturn(asSinglePage(tenant()));
    var found = tenantManagerService.findTenantByName(TENANT_NAME, OKAPI_TOKEN);
    assertThat(found).isEqualTo(tenant());
  }

  @Test
  void findTenantByName_negative_tenantNotFound() {
    when(tenantManagerClient.queryTenantsByName(TENANT_NAME, OKAPI_TOKEN)).thenReturn(empty());
    assertThatThrownBy(() -> tenantManagerService.findTenantByName(TENANT_NAME, OKAPI_TOKEN))
      .isInstanceOf(EntityNotFoundException.class)
      .hasMessage("Tenant is not found by name: " + TENANT_NAME);
  }

  @Test
  void findTenantByName_negative_multipleTenantsFound() {
    when(tenantManagerClient.queryTenantsByName(TENANT_NAME, OKAPI_TOKEN)).thenReturn(asSinglePage(tenant(), tenant()));
    assertThatThrownBy(() -> tenantManagerService.findTenantByName(TENANT_NAME, OKAPI_TOKEN))
      .isInstanceOf(EntityNotFoundException.class)
      .hasMessage("Multiple tenants found by name: " + TENANT_NAME);
  }

  @Test
  void findTenantByName_negative_badRequestError() {
    when(tenantManagerClient.queryTenantsByName(TENANT_NAME, OKAPI_TOKEN)).thenThrow(BadRequest.class);
    assertThatThrownBy(() -> tenantManagerService.findTenantByName(TENANT_NAME, OKAPI_TOKEN))
      .isInstanceOf(IntegrationException.class)
      .hasMessage("Failed to query tenant by name: " + TENANT_NAME);
  }
}
