package org.folio.entitlement.utils;

import static org.folio.entitlement.integration.folio.CommonStageContext.PARAM_REQUEST;

import java.util.List;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
import org.folio.entitlement.domain.model.EntitlementRequest;
import org.folio.flow.api.NoOpStage;
import org.folio.flow.api.ParallelStage;
import org.folio.flow.api.Stage;
import org.folio.flow.api.StageContext;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class FlowUtils {

  /**
   * Combines list of nullable stages and returns them as single or parallel stage.
   *
   * <p>It firstly filter list of stages to select only non-null values and then:</p>
   * <ul>
   *   <li>Creates a {@link ParallelStage} for multiple non-null stages</li>
   *   <li>Returns a single stage, if it's only one found</li>
   *   <li>Returns a {@link NoOpStage} instance if all stages is null</li>
   * </ul>
   *
   * @param stages - list of stages to analyze
   * @return created {@link Stage} object.
   */
  public static Stage<? extends StageContext> combineStages(String parallelStageName,
    List<? extends Stage<? extends StageContext>> stages) {
    if (CollectionUtils.isEmpty(stages)) {
      return NoOpStage.getInstance();
    }

    var nonNullStages = stages.stream()
      .filter(Objects::nonNull)
      .toList();

    var conditionalStagesSize = nonNullStages.size();
    if (conditionalStagesSize == 0) {
      return NoOpStage.getInstance();
    }

    return conditionalStagesSize == 1 ? nonNullStages.get(0) : ParallelStage.of(parallelStageName, nonNullStages);
  }

  public static EntitlementRequest getEntitlementRequest(StageContext context) {
    return context.getFlowParameter(PARAM_REQUEST);
  }
}
