package org.folio.entitlement.service.flow;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import org.folio.entitlement.utils.FlowUtils;
import org.folio.flow.api.NoOpStage;
import org.folio.flow.api.ParallelStage;
import org.folio.flow.api.Stage;
import org.folio.flow.api.StageContext;
import org.folio.test.types.UnitTest;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

@UnitTest
class FlowProviderUtilsTest {

  public static final String PARALLEL_STAGE_NAME = "test-par-stage";

  @Test
  void combineStages_positive_nullValue() {
    var result = FlowUtils.combineStages(PARALLEL_STAGE_NAME, null);
    assertThat(result).isEqualTo(NoOpStage.getInstance());
  }

  @Test
  void combineStages_positive_emptyCollection() {
    var result = FlowUtils.combineStages(PARALLEL_STAGE_NAME, emptyList());
    assertThat(result).isEqualTo(NoOpStage.getInstance());
  }

  @Test
  void combineStages_positive_listWithNullElement() {
    var result = FlowUtils.combineStages(PARALLEL_STAGE_NAME, singletonList(null));
    assertThat(result).isEqualTo(NoOpStage.getInstance());
  }

  @Test
  void combineStages_positive_nullStage() {
    var result = FlowUtils.combineStages(PARALLEL_STAGE_NAME, singletonList(null));
    assertThat(result).isEqualTo(NoOpStage.getInstance());
  }

  @Test
  void combineStages_positive_singleStage() {
    var stage = Mockito.<Stage<StageContext>>mock();
    var result = FlowUtils.combineStages(PARALLEL_STAGE_NAME, singletonList(stage));
    assertThat(result).isEqualTo(stage);
  }

  @Test
  void combineStages_positive_singleStageInList() {
    var stage = new TestStage();
    var result = FlowUtils.combineStages(PARALLEL_STAGE_NAME, singletonList(stage));
    assertThat(result).isEqualTo(stage);
  }

  @Test
  void combineStages_positive_complexCase() {
    var stage1 = Mockito.<Stage<StageContext>>mock();
    var stage2 = Mockito.<Stage<StageContext>>mock();
    var result = FlowUtils.combineStages(null, asList(stage1, stage2));

    assertThat(result).isInstanceOf(ParallelStage.class);
    assertThat(result.getId()).startsWith("par-stage-");
    assertThat(((ParallelStage) result).getStages()).hasSize(2);

    verify(stage1).getId();
    verify(stage2).getId();
  }

  private static final class TestStage implements Stage<StageContext> {

    @Override
    public void execute(StageContext stageContext) {
      // used for unit testing
    }
  }
}
