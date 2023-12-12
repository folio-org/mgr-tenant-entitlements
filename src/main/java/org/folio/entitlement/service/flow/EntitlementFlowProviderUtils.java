package org.folio.entitlement.service.flow;

import static java.util.stream.Collectors.toList;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
import org.folio.flow.api.NoOpStage;
import org.folio.flow.api.ParallelStage;
import org.folio.flow.api.Stage;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class EntitlementFlowProviderUtils {

  /**
   * Combines list of nullable stages and returns them as single or parallel stage.
   *
   * <p>
   *
   * </p>
   *
   * @param stages - list of stages to analyze
   * @return created {@link Stage} object.
   * @see EntitlementFlowProviderUtils#combineStages(List) documentation
   */
  public static Stage combineStages(Stage... stages) {
    return combineStages(Arrays.asList(stages));
  }

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
  public static Stage combineStages(List<? extends Stage> stages) {
    if (CollectionUtils.isEmpty(stages)) {
      return NoOpStage.getInstance();
    }

    var nonNullStages = stages.stream()
      .filter(Objects::nonNull)
      .collect(toList());

    var conditionalStagesSize = nonNullStages.size();
    if (conditionalStagesSize == 0) {
      return NoOpStage.getInstance();
    }

    return conditionalStagesSize == 1 ? nonNullStages.get(0) : ParallelStage.of(nonNullStages);
  }
}
