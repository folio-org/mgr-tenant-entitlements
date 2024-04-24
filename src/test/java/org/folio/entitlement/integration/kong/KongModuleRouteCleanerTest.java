package org.folio.entitlement.integration.kong;

import static org.folio.entitlement.domain.dto.EntitlementType.REVOKE;
import static org.folio.entitlement.domain.model.CommonStageContext.PARAM_TENANT_NAME;
import static org.folio.entitlement.support.TestConstants.FLOW_STAGE_ID;
import static org.folio.entitlement.support.TestConstants.TENANT_NAME;
import static org.folio.entitlement.support.TestValues.moduleStageContext;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.List;
import java.util.Map;
import org.folio.common.domain.model.ModuleDescriptor;
import org.folio.entitlement.domain.model.EntitlementRequest;
import org.folio.entitlement.domain.model.ModuleStageContext;
import org.folio.entitlement.support.TestUtils;
import org.folio.entitlement.support.TestValues;
import org.folio.test.types.UnitTest;
import org.folio.tools.kong.service.KongGatewayService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class KongModuleRouteCleanerTest {

  @InjectMocks private KongModuleRouteCleaner kongModuleRouteCleaner;
  @Mock private KongGatewayService kongGatewayService;

  @AfterEach
  void tearDown() {
    TestUtils.verifyNoMoreInteractions(this);
  }

  @Test
  void execute_positive_purgeTrue() {
    var request = EntitlementRequest.builder().type(REVOKE).purge(true).build();
    var moduleDescriptor = new ModuleDescriptor().id("mod-foo-1.0.0");
    var stageContext = stageContext(request, moduleDescriptor);

    kongModuleRouteCleaner.execute(stageContext);

    verify(kongGatewayService).removeRoutes(TENANT_NAME, List.of(moduleDescriptor));
  }

  @Test
  void execute_positive_purgeFalse() {
    var request = EntitlementRequest.builder().type(REVOKE).purge(false).build();
    var moduleDescriptor = new ModuleDescriptor().id("mod-foo-1.0.0");
    var stageContext = stageContext(request, moduleDescriptor);

    kongModuleRouteCleaner.execute(stageContext);

    verifyNoInteractions(kongGatewayService);
  }

  private static ModuleStageContext stageContext(EntitlementRequest request, ModuleDescriptor desc) {
    var stageParameters = Map.of(PARAM_TENANT_NAME, TENANT_NAME);
    var flowParameters = TestValues.moduleFlowParameters(request, desc);
    return moduleStageContext(FLOW_STAGE_ID, flowParameters, stageParameters);
  }
}
