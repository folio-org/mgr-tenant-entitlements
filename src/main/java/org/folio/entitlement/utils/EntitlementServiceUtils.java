package org.folio.entitlement.utils;

import static java.lang.String.format;
import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static java.util.stream.Collectors.joining;
import static org.folio.common.utils.CollectionUtils.toStream;

import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.IterableUtils;
import org.folio.common.domain.model.error.Parameter;
import org.folio.entitlement.domain.dto.ApplicationFlow;
import org.folio.entitlement.domain.dto.ExecutionStatus;
import org.folio.entitlement.domain.dto.Flow;
import org.folio.entitlement.domain.dto.FlowStage;
import org.folio.entitlement.exception.RequestValidationException;
import org.folio.entitlement.integration.IntegrationException;
import org.folio.tools.kong.exception.KongIntegrationException;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class EntitlementServiceUtils {

  /**
   * Prepares aggregated entitlement result response for multiple application flows.
   *
   * @param applicationFlows - list of related application flows
   * @param entitlementStages - set of stages belonging to each application as map
   * @return prepared {@link ApplicationFlow} response object
   */
  public static Flow prepareFlowResponse(List<ApplicationFlow> applicationFlows,
    Map<UUID, List<FlowStage>> entitlementStages) {
    var firstApplicationFlow = applicationFlows.get(0);

    var startedAt = firstApplicationFlow.getStartedAt();
    var finishedAt = firstApplicationFlow.getFinishedAt();
    var applicationFlowStatuses = new LinkedHashSet<ExecutionStatus>();
    applicationFlowStatuses.add(firstApplicationFlow.getStatus());

    firstApplicationFlow.setStages(entitlementStages.get(firstApplicationFlow.getId()));

    for (var i = 1; i < applicationFlows.size(); i++) {
      var applicationFlow = applicationFlows.get(i);
      applicationFlow.setStages(entitlementStages.get(applicationFlow.getId()));
      if (applicationFlow.getStartedAt().compareTo(startedAt) < 0) {
        startedAt = applicationFlow.getStartedAt();
      }
      if (applicationFlow.getFinishedAt().compareTo(finishedAt) > 0) {
        finishedAt = applicationFlow.getFinishedAt();
      }
      applicationFlowStatuses.add(applicationFlow.getStatus());
    }

    return new Flow()
      .id(firstApplicationFlow.getFlowId())
      .type(firstApplicationFlow.getType())
      .status(getFinalFlowStatus(applicationFlowStatuses).orElse(null))
      .startedAt(startedAt)
      .finishedAt(finishedAt)
      .applicationFlows(applicationFlows);
  }

  /**
   * Returns the final flow status for set of collected {@link ExecutionStatus} objects from all application flows done
   * in that flow.
   *
   * <p>
   * Rules:
   *   <ul>
   *     <li>if one of applications is failed then the final flow status must be {@code FAILED}</li>
   *     <li>if one of applications is cancelled then the final flow status must be {@code CANCELLED}</li>
   *     <li>if one of applications is in progress then the final flow status must be {@code IN_PROGRESS}</li>
   *     <li>if one of applications is queued then the final flow status must be {@code QUEUED}</li>
   *     <li>if all applications finished successfully then the final status must be {@code FINISHED}</li>
   *   </ul>
   * </p>
   *
   * @param flowStatuses - set of flow statuses of individual application flows
   * @return final flow status as {@link ExecutionStatus}
   */
  public static Optional<ExecutionStatus> getFinalFlowStatus(Set<ExecutionStatus> flowStatuses) {
    return Arrays.stream(ExecutionStatus.values()).filter(flowStatuses::contains).findFirst();
  }

  public static String getErrorMessage(Exception exception) {
    return buildErrorMessageForParameters(exception, getErrors(exception), exception.getCause());
  }

  /**
   * Creates a {@link HashMap} from given iterable using key and value mappers.
   *
   * @param it - iterable object to process
   * @param keyMapper - a mapping function to produce keys
   * @param valueMapper - a mapping function to produce values
   * @param <T> - generic type for iterable element
   * @param <K> - generic type for {@link HashMap} key
   * @param <V> - generic type for {@link HashMap} value
   * @return - created {@link HashMap} object
   */
  public static <T, K, V> Map<K, V> toHashMap(Iterable<T> it, Function<T, K> keyMapper, Function<T, V> valueMapper) {
    if (IterableUtils.isEmpty(it)) {
      return emptyMap();
    }

    var resultMap = new HashMap<K, V>();
    for (var value : it) {
      resultMap.put(keyMapper.apply(value), valueMapper.apply(value));
    }

    return resultMap;
  }

  private static List<Parameter> getErrors(Throwable throwable) {
    if (throwable instanceof IntegrationException integrationException) {
      return integrationException.getErrors();
    }

    if (throwable instanceof KongIntegrationException kongIntegrationException) {
      return kongIntegrationException.getErrors();
    }

    if (throwable instanceof RequestValidationException requestValidationException) {
      return requestValidationException.getErrorParameters();
    }

    return emptyList();
  }

  private static String buildErrorMessageForParameters(Exception exception, List<Parameter> errors, Throwable cause) {
    if (CollectionUtils.isEmpty(errors)) {
      return cause == null ? exception.getMessage() : exception.getMessage() + ", cause: " + cause.getMessage();
    }

    var errorParameters = toStream(errors)
      .map(parameter -> format("{key: %s, value: %s}", parameter.getKey(), parameter.getValue()))
      .collect(joining(", ", "[", "]"));
    return exception.getMessage() + ", parameters: " + errorParameters;
  }
}
