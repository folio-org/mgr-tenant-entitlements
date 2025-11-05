package org.folio.entitlement.utils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import org.folio.flow.api.NoOpStage;
import org.folio.flow.api.ParallelStage;
import org.folio.flow.api.Stage;
import org.folio.flow.api.StageContext;
import org.folio.test.types.UnitTest;
import org.junit.jupiter.api.Test;

@UnitTest
class FlowUtilsTest {

  @Test
  void combineStages_shouldReturnNoOpStage_whenStagesListIsEmpty() {
    var result = FlowUtils.combineStages("test-stage", List.of());

    assertThat(result).isInstanceOf(NoOpStage.class);
  }

  @Test
  void combineStages_shouldReturnNoOpStage_whenStagesListIsNull() {
    var result = FlowUtils.combineStages("test-stage", null);

    assertThat(result).isInstanceOf(NoOpStage.class);
  }

  @Test
  void combineStages_shouldReturnNoOpStage_whenAllStagesAreNull() {
    List<Stage<StageContext>> stages = new java.util.ArrayList<>();
    stages.add(null);
    stages.add(null);
    stages.add(null);

    var result = FlowUtils.combineStages("test-stage", stages);

    assertThat(result).isInstanceOf(NoOpStage.class);
  }

  @Test
  void combineStages_shouldReturnSingleStage_whenOnlyOneStageProvided() {
    @SuppressWarnings("unchecked")
    Stage<StageContext> stage = mock(Stage.class);

    var result = FlowUtils.combineStages("test-stage", List.of(stage));

    assertThat(result).isSameAs(stage);
  }

  @Test
  void combineStages_shouldReturnParallelStage_whenMultipleStagesProvided() {
    @SuppressWarnings("unchecked")
    Stage<StageContext> stage1 = mock(Stage.class);
    @SuppressWarnings("unchecked")
    Stage<StageContext> stage2 = mock(Stage.class);

    var result = FlowUtils.combineStages("test-stage", List.of(stage1, stage2));

    assertThat(result).isInstanceOf(ParallelStage.class);
    assertThat(((ParallelStage) result).getStages()).hasSize(2);
  }

  @Test
  void combineStages_shouldFilterNullStages() {
    @SuppressWarnings("unchecked")
    Stage<StageContext> stage1 = mock(Stage.class);
    @SuppressWarnings("unchecked")
    Stage<StageContext> stage2 = mock(Stage.class);
    
    List<Stage<StageContext>> stages = new java.util.ArrayList<>();
    stages.add(stage1);
    stages.add(null);
    stages.add(stage2);

    var result = FlowUtils.combineStages("test-stage", stages);

    assertThat(result).isInstanceOf(ParallelStage.class);
    assertThat(((ParallelStage) result).getStages()).hasSize(2);
  }

  @Test
  void combineStagesWithExecutor_shouldUseCustomExecutor_whenMultipleStagesProvided() {
    @SuppressWarnings("unchecked")
    Stage<StageContext> stage1 = mock(Stage.class);
    @SuppressWarnings("unchecked")
    Stage<StageContext> stage2 = mock(Stage.class);
    var customExecutor = mock(Executor.class);

    var result = FlowUtils.combineStages("test-stage", List.of(stage1, stage2), customExecutor);

    assertThat(result).isInstanceOf(ParallelStage.class);
    var parallelStage = (ParallelStage) result;
    assertThat(parallelStage.getStages()).hasSize(2);
    assertThat(parallelStage.getCustomExecutor()).isSameAs(customExecutor);
  }

  @Test
  void combineStagesWithExecutor_shouldReturnNoOpStage_whenStagesListIsEmpty() {
    var customExecutor = mock(Executor.class);

    var result = FlowUtils.combineStages("test-stage", List.of(), customExecutor);

    assertThat(result).isInstanceOf(NoOpStage.class);
  }

  @Test
  void combineStagesWithExecutor_shouldReturnSingleStage_whenOnlyOneStageProvided() {
    @SuppressWarnings("unchecked")
    Stage<StageContext> stage = mock(Stage.class);
    var customExecutor = mock(Executor.class);

    var result = FlowUtils.combineStages("test-stage", List.of(stage), customExecutor);

    assertThat(result).isSameAs(stage);
  }

  @Test
  void combineStagesWithExecutor_shouldNotUseCustomExecutor_whenOnlyOneStageProvided() {
    @SuppressWarnings("unchecked")
    Stage<StageContext> stage = mock(Stage.class);
    var customExecutor = mock(Executor.class);

    var result = FlowUtils.combineStages("test-stage", List.of(stage), customExecutor);

    assertThat(result).isSameAs(stage)
      .isNotInstanceOf(ParallelStage.class);
  }

  @Test
  void combineStagesWithExecutor_shouldUseDefaultExecutor_whenCustomExecutorIsNull() {
    @SuppressWarnings("unchecked")
    Stage<StageContext> stage1 = mock(Stage.class);
    @SuppressWarnings("unchecked")
    Stage<StageContext> stage2 = mock(Stage.class);

    var result = FlowUtils.combineStages("test-stage", List.of(stage1, stage2), null);

    assertThat(result).isInstanceOf(ParallelStage.class);
    var parallelStage = (ParallelStage) result;
    assertThat(parallelStage.getStages()).hasSize(2);
    assertThat(parallelStage.getCustomExecutor()).isNull();
  }

  @Test
  void combineStagesWithExecutor_shouldVerifyExecutorInvocation() {
    var invocationCount = new AtomicInteger(0);
    Executor customExecutor = command -> {
      invocationCount.incrementAndGet();
      command.run();
    };

    @SuppressWarnings("unchecked")
    Stage<StageContext> stage1 = mock(Stage.class);
    @SuppressWarnings("unchecked")
    Stage<StageContext> stage2 = mock(Stage.class);

    var result = FlowUtils.combineStages("test-stage", List.of(stage1, stage2), customExecutor);

    assertThat(result).isInstanceOf(ParallelStage.class);
    var parallelStage = (ParallelStage) result;
    assertThat(parallelStage.getCustomExecutor()).isSameAs(customExecutor);
  }

  @Test
  void combineStagesWithExecutor_shouldFilterNullStages() {
    @SuppressWarnings("unchecked")
    Stage<StageContext> stage1 = mock(Stage.class);
    @SuppressWarnings("unchecked")
    Stage<StageContext> stage2 = mock(Stage.class);
    var customExecutor = mock(Executor.class);
    
    List<Stage<StageContext>> stages = new java.util.ArrayList<>();
    stages.add(stage1);
    stages.add(null);
    stages.add(stage2);
    stages.add(null);

    var result = FlowUtils.combineStages("test-stage", stages, customExecutor);

    assertThat(result).isInstanceOf(ParallelStage.class);
    var parallelStage = (ParallelStage) result;
    assertThat(parallelStage.getStages()).hasSize(2);
    assertThat(parallelStage.getCustomExecutor()).isSameAs(customExecutor);
  }

  @Test
  void combineStages_backwardCompatibility_shouldWorkWithoutExecutor() {
    @SuppressWarnings("unchecked")
    Stage<StageContext> stage1 = mock(Stage.class);
    @SuppressWarnings("unchecked")
    Stage<StageContext> stage2 = mock(Stage.class);

    var result = FlowUtils.combineStages("test-stage", List.of(stage1, stage2));

    assertThat(result).isInstanceOf(ParallelStage.class);
    var parallelStage = (ParallelStage) result;
    assertThat(parallelStage.getStages()).hasSize(2);
    assertThat(parallelStage.getCustomExecutor()).isNull();
  }
}
