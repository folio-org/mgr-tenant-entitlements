package org.folio.entitlement.integration.kong;

import static org.folio.entitlement.domain.dto.EntitlementType.REVOKE;
import static org.folio.entitlement.domain.model.CommonStageContext.PARAM_REQUEST;
import static org.folio.entitlement.domain.model.CommonStageContext.PARAM_TENANT_NAME;
import static org.folio.entitlement.integration.okapi.model.OkapiStageContext.PARAM_MODULE_DESCRIPTORS;
import static org.folio.entitlement.support.TestConstants.FLOW_STAGE_ID;
import static org.folio.entitlement.support.TestConstants.TENANT_NAME;
import static org.folio.entitlement.support.TestValues.applicationDescriptor;
import static org.folio.entitlement.support.TestValues.okapiStageContext;
import static org.mockito.Mockito.verify;

import java.util.List;
import java.util.Map;
import org.folio.common.domain.model.ModuleDescriptor;
import org.folio.entitlement.domain.model.EntitlementRequest;
import org.folio.entitlement.integration.am.model.ApplicationDescriptor;
import org.folio.entitlement.integration.okapi.model.OkapiStageContext;
import org.folio.entitlement.support.TestUtils;
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
class KongRouteCleanerTest {

  @InjectMocks private KongRouteCleaner kongRouteCleaner;
  @Mock private KongGatewayService kongGatewayService;

  @AfterEach
  void tearDown() {
    TestUtils.verifyNoMoreInteractions(this);
  }

  @Test
  void execute_positive_purgeTrue() {
    var request = EntitlementRequest.builder().type(REVOKE).purge(true).build();
    var moduleDescriptors = List.of(new ModuleDescriptor().id("mod-foo-1.0.0"));
    var applicationDescriptor = applicationDescriptor().moduleDescriptors(moduleDescriptors);
    var stageContext = stageContext(request, applicationDescriptor);

    kongRouteCleaner.execute(stageContext);

    verify(kongGatewayService).removeRoutes(TENANT_NAME, moduleDescriptors);
  }

  @Test
  void execute_positive_purgeFalse() {
    var request = EntitlementRequest.builder().type(REVOKE).purge(false).build();
    var moduleDescriptors = List.of(new ModuleDescriptor().id("mod-foo-1.0.0"));
    var applicationDescriptor = applicationDescriptor().moduleDescriptors(moduleDescriptors);
    var stageContext = stageContext(request, applicationDescriptor);

    kongRouteCleaner.execute(stageContext);

    verify(kongGatewayService).removeRoutes(TENANT_NAME, moduleDescriptors);
  }

  private static OkapiStageContext stageContext(EntitlementRequest request, ApplicationDescriptor desc) {
    var stageParameters = Map.of(PARAM_TENANT_NAME, TENANT_NAME);
    var flowParameters = Map.of(PARAM_REQUEST, request, PARAM_MODULE_DESCRIPTORS, desc.getModuleDescriptors());
    return okapiStageContext(FLOW_STAGE_ID, flowParameters, stageParameters);
  }
}
