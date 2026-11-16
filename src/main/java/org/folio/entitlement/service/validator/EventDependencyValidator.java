package org.folio.entitlement.service.validator;

import static java.util.stream.Collectors.toSet;
import static org.apache.commons.collections4.CollectionUtils.emptyIfNull;
import static org.folio.entitlement.service.validator.EntitlementRequestValidator.Order.EVENT_DEPENDENCY;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Supplier;
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
 * Checks that every event consumed by the requested applications is produced by some application. Events are matched
 * by exact string comparison.
 *
 * <p>Consumed events are the events declared as consumed by modules of the applications in the request.
 * Events consumed by already entitled applications are not checked.
 *
 * <p>Produced events are looked up in:
 * <ul>
 *   <li>ENTITLE and UPGRADE: the applications in the request and the tenant's already entitled applications;</li>
 *   <li>STATE: the applications in the request only.</li>
 * </ul>
 *
 * <p>When a consumed event has no producer:
 * <ul>
 *   <li>optional consumer: a warning is logged and the request proceeds;</li>
 *   <li>non-optional consumer, strict mode off (default): a warning is logged and the request proceeds;</li>
 *   <li>non-optional consumer, strict mode on: the request fails with an error naming the missing events.</li>
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
    var requestedApps = emptyIfNull(context.getApplicationDescriptors());
    validateDescriptors(requestedApps, () -> descriptorProvider.getDescriptors(context));
  }

  @Override
  public void validate(EntitlementRequest request) {
    var descriptors = emptyIfNull(descriptorProvider.getDescriptors(request));
    var requestedAppIds = Set.copyOf(emptyIfNull(request.getApplications()));
    var requestedApps = descriptors.stream().filter(app -> requestedAppIds.contains(app.getId())).toList();

    validateDescriptors(requestedApps, () -> List.copyOf(descriptors));
  }

  @Override
  public boolean shouldValidate(EntitlementRequest request) {
    return request.getType() == entitlementType;
  }

  private void validateDescriptors(Collection<ApplicationDescriptor> requestedApps,
    Supplier<List<ApplicationDescriptor>> allDescriptors) {
    var appIdsByRequiredEvent = findConsumedEvents(requestedApps, false);
    var appIdsByOptionalEvent = findConsumedEvents(requestedApps, true);
    if (appIdsByRequiredEvent.isEmpty() && appIdsByOptionalEvent.isEmpty()) {
      return;
    }

    var produced = findProducedEvents(emptyIfNull(allDescriptors.get()));
    appIdsByRequiredEvent.keySet().removeIf(produced::contains);
    appIdsByOptionalEvent.keySet().removeIf(produced::contains);

    warn("Optional consumed event", appIdsByOptionalEvent);
    warn("Consumed event", appIdsByRequiredEvent);

    if (strict && !appIdsByRequiredEvent.isEmpty()) {
      throw missingProducers(appIdsByRequiredEvent);
    }
  }

  private static RequestValidationException missingProducers(Map<String, Set<String>> appIdsByEvent) {
    var params = new Params();
    appIdsByEvent.forEach((event, appIds) -> params.add(event, String.join(", ", appIds)));
    var message = "Consumed events without producers found: " + String.join(", ", appIdsByEvent.keySet());
    return new RequestValidationException(message, params);
  }

  private static void warn(String description, Map<String, Set<String>> appIdsByEvent) {
    appIdsByEvent.forEach((event, appIds) -> log.warn(
      "{} has no producer: event = {}, applications = [{}]", description, event, String.join(", ", appIds)));
  }

  private static Set<String> findProducedEvents(Collection<ApplicationDescriptor> descriptors) {
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
    Collection<ApplicationDescriptor> descriptors, boolean optional) {
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
