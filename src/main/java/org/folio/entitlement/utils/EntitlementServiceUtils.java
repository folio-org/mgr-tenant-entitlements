package org.folio.entitlement.utils;

import static java.lang.String.format;
import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static java.util.stream.Collectors.joining;
import static org.folio.common.utils.CollectionUtils.toStream;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.IterableUtils;
import org.folio.common.domain.model.error.Parameter;
import org.folio.entitlement.exception.RequestValidationException;
import org.folio.entitlement.integration.IntegrationException;
import org.folio.tools.kong.exception.KongIntegrationException;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class EntitlementServiceUtils {

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
