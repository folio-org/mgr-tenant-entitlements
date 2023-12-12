package org.folio.entitlement.integration.okapi;

import static java.util.Collections.emptyMap;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import org.folio.flow.api.Flow;
import org.folio.flow.api.StageContext;
import org.folio.test.types.UnitTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class OkapiModuleInstallerFlowProviderTest {

  @InjectMocks private OkapiModuleInstallerFlowProvider okapiModuleInstallerFlowProvider;
  @Mock private OkapiModulesInstaller moduleInstaller;
  @Mock private ModulesEventPublisherStage modulesEventPublisherStage;

  @Test
  void prepareFlow_positive() {
    var flowId = "flowId";
    var context = StageContext.of(flowId, emptyMap(), emptyMap());

    var result = okapiModuleInstallerFlowProvider.prepareFlow(context);
    var expectedFlowId = flowId + "/okapi-module-installer";
    var expectedFlow = Flow.builder()
      .id(expectedFlowId)
      .stage(moduleInstaller)
      .stage(modulesEventPublisherStage)
      .build();
    assertThat(result).isEqualTo(expectedFlow);
  }
}
