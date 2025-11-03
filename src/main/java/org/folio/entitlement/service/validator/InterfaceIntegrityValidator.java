package org.folio.entitlement.service.validator;

import static java.util.function.Predicate.not;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toMap;
import static java.util.stream.Collectors.toSet;
import static org.apache.commons.collections4.CollectionUtils.isEmpty;
import static org.apache.commons.collections4.CollectionUtils.isNotEmpty;
import static org.folio.entitlement.service.validator.EntitlementRequestValidator.Order.INTERFACE_INTEGRITY;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.common.domain.model.ApplicationDescriptor;
import org.folio.entitlement.domain.dto.EntitlementRequestType;
import org.folio.entitlement.domain.model.CommonStageContext;
import org.folio.entitlement.domain.model.EntitlementRequest;
import org.folio.entitlement.domain.model.InterfaceItem;
import org.folio.entitlement.exception.RequestValidationException;
import org.folio.entitlement.exception.RequestValidationException.Params;
import org.folio.entitlement.service.validator.adp.ApplicationDescriptorProvider;
import org.folio.entitlement.service.validator.icollector.ApplicationInterfaceCollector;
import org.folio.entitlement.service.validator.icollector.ApplicationInterfaceCollector.RequiredProvidedInterfaces;
import org.springframework.core.annotation.Order;

@Order(INTERFACE_INTEGRITY)
@Log4j2
@RequiredArgsConstructor
public class InterfaceIntegrityValidator extends StageRequestValidator {

  public static final StageRequestValidator NO_OP =
    new StageRequestValidator.NoOp(InterfaceIntegrityValidator.class.getSimpleName());

  /**
   * The type of entitlement request this validator is responsible for.
   */
  private final EntitlementRequestType entitlementType;
  private final ApplicationInterfaceCollector interfaceCollector;
  private final ApplicationDescriptorProvider applicationDescriptorProvider;

  @Override
  public void execute(CommonStageContext context) {
    var applicationDescriptors = applicationDescriptorProvider.getDescriptors(context);
    var tenantId = context.getEntitlementRequest().getTenantId();

    validateDescriptors(applicationDescriptors, tenantId);
  }

  @Override
  public void validate(EntitlementRequest request) {
    var applicationDescriptors = applicationDescriptorProvider.getDescriptors(request);
    var tenantId = request.getTenantId();

    validateDescriptors(applicationDescriptors, tenantId);
  }

  @Override
  public boolean shouldValidate(EntitlementRequest entitlementRequest) {
    return entitlementRequest.getType() == entitlementType;
  }

  private void validateDescriptors(List<ApplicationDescriptor> descriptors, UUID tenantId) {
    if (isEmpty(descriptors)) {
      throw new RequestValidationException("No application descriptors provided", "descriptors", null);
    }
    log.info("Validating dependencies between application descriptors: appIds = [{}], tenantId = {}",
      () -> descriptors.stream().map(ApplicationDescriptor::getId).collect(joining(", ")),
      () -> tenantId);

    var missingInterfaces = interfaceCollector.collectRequiredAndProvided(descriptors, tenantId)
      .flatMap(this::findMissingInterfaces)
      .collect(toSet());

    log.debug("Missing interfaces {}",
      () -> missingInterfaces.isEmpty()
        ? "not found"
        : "found: " + missingInterfaces.stream().map(InterfaceItem::toString).collect(joining(", ")));

    if (isNotEmpty(missingInterfaces)) {
      throw new RequestValidationException("Missing interfaces found for the applications",
        toParams(missingInterfaces));
    }
  }

  private Stream<InterfaceItem> findMissingInterfaces(RequiredProvidedInterfaces interfaces) {
    return interfaces.required().stream()
      .filter(not(foundIn(interfaces.provided())));
  }

  private static Predicate<? super InterfaceItem> foundIn(Map<String, Set<InterfaceItem>> provided) {
    return testing -> {
      var testingRef = testing.interfaceRef();

      var interfaceVariations = provided.get(testingRef.getId());

      return interfaceVariations != null
        && interfaceVariations.stream()
        .map(InterfaceItem::interfaceRef)
        .anyMatch(ref -> ref.isCompatible(testingRef));
    };
  }

  private static Params toParams(Set<InterfaceItem> missing) {
    assert isNotEmpty(missing);

    var missingPerApplication = missing.stream().collect(toMap(
      InterfaceItem::appId,
      InterfaceItem::interfaceRefAsString,
      (collectedInterfaces, newInterface) -> collectedInterfaces + "; " + newInterface
    ));

    Params result = new Params();
    missingPerApplication.forEach(result::add);

    return result;
  }
}
