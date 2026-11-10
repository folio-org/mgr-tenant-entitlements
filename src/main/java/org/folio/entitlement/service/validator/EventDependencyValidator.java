package org.folio.entitlement.service.validator;

import static java.util.stream.Collectors.toSet;
import static org.apache.commons.collections4.CollectionUtils.emptyIfNull;
import static org.apache.commons.collections4.CollectionUtils.isEmpty;
import static org.folio.entitlement.service.validator.EntitlementRequestValidator.Order.EVENT_DEPENDENCY;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import lombok.extern.log4j.Log4j2;
import org.folio.common.domain.model.ApplicationDescriptor;
import org.folio.common.domain.model.ProducesEvent;
import org.folio.entitlement.domain.dto.EntitlementRequestType;
import org.folio.entitlement.domain.model.CommonStageContext;
import org.folio.entitlement.domain.model.EntitlementRequest;
import org.folio.entitlement.exception.RequestValidationException;
import org.folio.entitlement.exception.RequestValidationException.Params;
import org.folio.entitlement.service.validator.adp.ApplicationDescriptorProvider;
import org.springframework.core.annotation.Order;

/**
 * Checks that every consumed event has a producer in the application dependency tree
 * (including platform applications). Events are matched by exact string comparison.
 *
 * <p>When a consumed event has no producer:
 * <ul>
 *   <li>optional consumer: a warning is logged and entitlement proceeds;</li>
 *   <li>non-optional consumer, strict mode off (default): a warning is logged and entitlement proceeds;</li>
 *   <li>non-optional consumer, strict mode on: entitlement fails with an error naming the missing events.</li>
 * </ul>
 * Strict mode is set by {@code application.validation.event-dependency.strict-event-validation}.
 * A produced event that nobody consumes is ignored.
 */
@Order(EVENT_DEPENDENCY)
@Log4j2
public class EventDependencyValidator extends StageRequestValidator {

  /**
   * The type of entitlement request this validator is responsible for.
   */
  private final EntitlementRequestType entitlementType;
  private final ApplicationDescriptorProvider descriptorProvider;
  private final boolean strict;

  public EventDependencyValidator(EntitlementRequestType entitlementType,
    ApplicationDescriptorProvider descriptorProvider, boolean strict) {
    this.entitlementType = entitlementType;
    this.descriptorProvider = descriptorProvider;
    this.strict = strict;
  }

  @Override
  public void execute(CommonStageContext context) {
    validateDescriptors(descriptorProvider.getDescriptors(context));
  }

  @Override
  public void validate(EntitlementRequest request) {
    validateDescriptors(descriptorProvider.getDescriptors(request));
  }

  @Override
  public boolean shouldValidate(EntitlementRequest request) {
    return request.getType() == entitlementType;
  }

  private void validateDescriptors(List<ApplicationDescriptor> descriptors) {
    if (isEmpty(descriptors)) {
      return;
    }

    var produced = findProducedEvents(descriptors);
    var appIdsByRequiredEvent = findConsumedEvents(descriptors, false);
    var appIdsByOptionalEvent = findConsumedEvents(descriptors, true);

    appIdsByOptionalEvent.keySet().removeIf(produced::contains);
    appIdsByRequiredEvent.keySet().removeIf(produced::contains);

    warn("Optional consumed event", appIdsByOptionalEvent);
    warn("Consumed event", appIdsByRequiredEvent);

    if (strict && !appIdsByRequiredEvent.isEmpty()) {
      var params = new Params();
      appIdsByRequiredEvent.forEach((event, appIds) -> params.add(event, String.join(", ", appIds)));
      var message = "Consumed events without producers found: " + String.join(", ", appIdsByRequiredEvent.keySet());
      throw new RequestValidationException(message, params);
    }
  }

  private static void warn(String description, Map<String, Set<String>> appIdsByEvent) {
    appIdsByEvent.forEach((event, appIds) -> log.warn(
      "{} has no producer: event = {}, applications = [{}]", description, event, String.join(", ", appIds)));
  }

  private static Set<String> findProducedEvents(List<ApplicationDescriptor> descriptors) {
    return descriptors.stream()
      .flatMap(app -> emptyIfNull(app.getModuleDescriptors()).stream())
      .flatMap(module -> emptyIfNull(module.getProducesEvents()).stream())
      .map(ProducesEvent::getEvent)
      .collect(toSet());
  }

  /**
   * Finds the consumed events declared by the applications' modules.
   *
   * @param descriptors - application descriptors to scan
   * @param optional - {@code true} to return only events consumed as optional, {@code false} for the required ones
   * @return map where the key is the consumed event name and the value is the ids of the applications consuming it
   */
  private static Map<String, Set<String>> findConsumedEvents(
    List<ApplicationDescriptor> descriptors, boolean optional) {
    var appIdsByEvent = new LinkedHashMap<String, Set<String>>();
    for (var app : descriptors) {
      for (var module : emptyIfNull(app.getModuleDescriptors())) {
        for (var consumed : emptyIfNull(module.getConsumesEvents())) {
          if (Boolean.TRUE.equals(consumed.getOptional()) == optional) {
            appIdsByEvent.computeIfAbsent(consumed.getEvent(), event -> new TreeSet<>()).add(app.getId());
          }
        }
      }
    }
    return appIdsByEvent;
  }
}
