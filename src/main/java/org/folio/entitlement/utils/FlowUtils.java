package org.folio.entitlement.utils;

import static org.folio.entitlement.domain.model.ModuleStageContext.ATTR_STAGE_NAME;

import java.util.List;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.collections4.CollectionUtils;
import org.folio.entitlement.domain.model.IdentifiableStageContext;
import org.folio.entitlement.domain.model.RetryInformation;
import org.folio.entitlement.service.RetryInformationService;
import org.folio.entitlement.service.stage.ThreadLocalModuleStageContext;
import org.folio.flow.api.NoOpStage;
import org.folio.flow.api.ParallelStage;
import org.folio.flow.api.Stage;
import org.folio.flow.api.StageContext;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
@Log4j2
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
  @SuppressWarnings("java:S1452")
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

  public static void addErrorInformation(String error, ThreadLocalModuleStageContext threadLocalModuleStageContext,
    RetryInformationService retryInformationService) {
    var context = threadLocalModuleStageContext.get();
    if (context != null) {
      var flowStageKey = getFlowStageKey(context, context.get(ATTR_STAGE_NAME));

      var retryInfo = retryInformationService.get(flowStageKey);
      if (retryInfo == null) {
        retryInfo = RetryInformation.builder().build();
        retryInformationService.set(flowStageKey, retryInfo);
      }
      retryInfo.addError(error).incrementRetriesCount();
    } else {
      log.warn("Cannot store error information for a Flow Stage - no module stage context provided.");
    }
  }

  public static String getFlowStageKey(IdentifiableStageContext context, String stageName) {
    return context.getCurrentFlowId() + "_" + stageName;
  }
}
