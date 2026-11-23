package org.folio.entitlement.service.stage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import org.folio.entitlement.domain.model.ModuleStageContext;
import org.folio.test.types.UnitTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class ModuleDatabaseLoggingStageTest {

  @Mock private ModuleStageContext context;

  @Test
  void stageNameSuffix_positive_dashFollowedByUncapitalizedSimpleName() {
    assertThat(ModuleDatabaseLoggingStage.stageNameSuffix(SampleStage.class)).isEqualTo("-sampleStage");
  }

  @Test
  void getStageName_positive_moduleIdFollowedByStageNameSuffix() {
    when(context.getModuleId()).thenReturn("mod-a-1.0.0");

    var result = new SampleStage().getStageName(context);

    assertThat(result).isEqualTo("mod-a-1.0.0-sampleStage");
  }

  private static final class SampleStage extends ModuleDatabaseLoggingStage {

    @Override
    public void execute(ModuleStageContext context) {
      // not used
    }
  }
}
