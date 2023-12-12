package org.folio.entitlement.service.flow;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.folio.flow.api.NoOpStage;
import org.folio.flow.api.ParallelStage;
import org.folio.flow.api.Stage;
import org.folio.test.types.UnitTest;
import org.junit.jupiter.api.Test;

@UnitTest
class EntitlementFlowProviderUtilsTest {

  @Test
  void combineStages_positive_nullValue() {
    var result = EntitlementFlowProviderUtils.combineStages((Stage) null);
    assertThat(result).isEqualTo(NoOpStage.getInstance());
  }

  @Test
  void combineStages_positive_emptyCollection() {
    var result = EntitlementFlowProviderUtils.combineStages(emptyList());
    assertThat(result).isEqualTo(NoOpStage.getInstance());
  }

  @Test
  void combineStages_positive_listWithNullElement() {
    var result = EntitlementFlowProviderUtils.combineStages(singletonList(null));
    assertThat(result).isEqualTo(NoOpStage.getInstance());
  }

  @Test
  void combineStages_positive_nullStage() {
    var result = EntitlementFlowProviderUtils.combineStages((Stage) null);
    assertThat(result).isEqualTo(NoOpStage.getInstance());
  }

  @Test
  void combineStages_positive_singleStage() {
    var stage = mock(Stage.class);
    var result = EntitlementFlowProviderUtils.combineStages(stage);
    assertThat(result).isEqualTo(stage);
  }

  @Test
  void combineStages_positive_singleStageInList() {
    var stage = mock(Stage.class);
    var result = EntitlementFlowProviderUtils.combineStages(singletonList(stage));
    assertThat(result).isEqualTo(stage);
  }

  @Test
  void combineStages_positive_complexCase() {
    var stage1 = mock(Stage.class);
    var stage2 = mock(Stage.class);
    var result = EntitlementFlowProviderUtils.combineStages(null, stage1, stage2);

    assertThat(result).isInstanceOf(ParallelStage.class);
    assertThat(((ParallelStage) result).getStages()).hasSize(2);

    verify(stage1).getId();
    verify(stage2).getId();
  }
}
