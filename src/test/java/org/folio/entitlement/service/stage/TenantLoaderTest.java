package org.folio.entitlement.service.stage;

import static java.util.Collections.emptyMap;
import static java.util.Collections.singletonMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.entitlement.service.flow.EntitlementFlowConstants.PARAM_REQUEST;
import static org.folio.entitlement.service.flow.EntitlementFlowConstants.PARAM_TENANT_NAME;
import static org.folio.entitlement.support.TestConstants.FLOW_STAGE_ID;
import static org.folio.entitlement.support.TestConstants.OKAPI_TOKEN;
import static org.folio.entitlement.support.TestConstants.TENANT_ID;
import static org.folio.entitlement.support.TestConstants.TENANT_NAME;
import static org.folio.entitlement.support.TestValues.tenant;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import org.folio.entitlement.domain.model.EntitlementRequest;
import org.folio.entitlement.integration.tm.TenantManagerService;
import org.folio.flow.api.StageContext;
import org.folio.test.types.UnitTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class TenantLoaderTest {

  @InjectMocks private TenantLoader tenantLoader;
  @Mock private TenantManagerService tenantManagerService;

  @AfterEach
  void tearDown() {
    verifyNoMoreInteractions(tenantManagerService);
  }

  @Test
  void execute_positive() {
    var entitlementRequest = EntitlementRequest.builder().tenantId(TENANT_ID).okapiToken(OKAPI_TOKEN).build();
    var stageContext = StageContext.of(FLOW_STAGE_ID, singletonMap(PARAM_REQUEST, entitlementRequest), emptyMap());
    when(tenantManagerService.findTenant(TENANT_ID, OKAPI_TOKEN)).thenReturn(tenant());

    tenantLoader.execute(stageContext);

    assertThat(stageContext.<String>get(PARAM_TENANT_NAME)).isEqualTo(TENANT_NAME);
  }
}
