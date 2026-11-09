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
import org.folio.entitlement.service.validator.adp.ApplicationAndDependencyDescriptorProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Checks that every consumed event has a producer in the application dependency tree
 * (including platform applications). Events are matched by exact string comparison.
 *
 * <p>A consumed event without a producer: optional - warns; non-optional - warns, or fails when
 * {@code application.validation.event-dependency.strict-event-validation} is {@code true}.
 * Produced events without consumers are ignored.
 */
@Order(EVENT_DEPENDENCY)
@Log4j2
@Component
public class EventDependencyValidator extends StageRequestValidator {

  private final ApplicationAndDependencyDescriptorProvider descriptorProvider;
  private final boolean strict;

  public EventDependencyValidator(ApplicationAndDependencyDescriptorProvider descriptorProvider,
    @Value("${application.validation.event-dependency.strict-event-validation:false}") boolean strict) {
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
    return request.getType() == EntitlementRequestType.ENTITLE;
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
      throw new RequestValidationException("Consumed events without producers found", params);
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
