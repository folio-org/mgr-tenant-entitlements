package org.folio.entitlement.utils;

import static java.lang.String.format;
import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static java.util.Collections.unmodifiableMap;
import static java.util.stream.Collectors.joining;
import static org.folio.common.utils.CollectionUtils.toStream;
import static org.folio.common.utils.Collectors.toLinkedHashMap;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.IterableUtils;
import org.folio.common.domain.model.ModuleDescriptor;
import org.folio.common.domain.model.error.Parameter;
import org.folio.entitlement.exception.RequestValidationException;
import org.folio.entitlement.integration.IntegrationException;
import org.folio.entitlement.integration.am.model.Module;
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
   * @param <T> - generic type for iterable element
   * @param <K> - generic type for {@link HashMap} key
   * @return - created {@link HashMap} object
   */
  public static <T, K> Map<K, T> toHashMap(Iterable<T> it, Function<T, K> keyMapper) {
    if (IterableUtils.isEmpty(it)) {
      return emptyMap();
    }

    var resultMap = new HashMap<K, T>();
    for (var value : it) {
      resultMap.put(keyMapper.apply(value), value);
    }

    return resultMap;
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

  /**
   * Creates a {@link HashMap} from given iterable using key and value mappers.
   *
   * @param it - iterable object to process
   * @param keyMapper - a mapping function to produce keys
   * @param <T> - generic type for iterable element
   * @param <K> - generic type for {@link HashMap} key
   * @return - created {@link HashMap} object
   */
  public static <T, K> Map<K, T> toUnmodifiableMap(Iterable<T> it, Function<T, K> keyMapper) {
    if (IterableUtils.isEmpty(it)) {
      return emptyMap();
    }

    var resultMap = new HashMap<K, T>();
    for (var value : it) {
      var key = Objects.requireNonNull(keyMapper.apply(value));
      resultMap.put(key, Objects.requireNonNull(value));
    }

    return unmodifiableMap(resultMap);
  }

  /**
   * Groups {@link Module} objects by name.
   *
   * @param modules - list with module definitions
   * @return grouped module objects by name.
   */
  public static Map<String, Module> groupModulesByNames(List<Module> modules) {
    return toStream(modules).collect(toLinkedHashMap(Module::getName));
  }

  /**
   * Checks if upgrade logic must be applied.
   *
   * @param moduleDesc - new {@link ModuleDescriptor} value
   * @param installedModuleDesc - installed {@link ModuleDescriptor} value
   * @return true if module must be upgraded, false otherwise
   */
  public static boolean isModuleUpdated(ModuleDescriptor moduleDesc, ModuleDescriptor installedModuleDesc) {
    return moduleDesc != null
      ? installedModuleDesc == null || !Objects.equals(moduleDesc.getId(), installedModuleDesc.getId())
      : installedModuleDesc != null;
  }

  /**
   * Checks if provided module for upgrade has the same version as installed (excludes deprecated modules).
   *
   * @param moduleDesc - new {@link ModuleDescriptor} value
   * @param installedModuleDesc - installed {@link ModuleDescriptor} value
   * @return true if module must be upgraded, false otherwise
   */
  public static boolean isModuleVersionChanged(ModuleDescriptor moduleDesc, ModuleDescriptor installedModuleDesc) {
    return moduleDesc != null
      && installedModuleDesc != null
      && Objects.equals(moduleDesc.getId(), installedModuleDesc.getId());
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
