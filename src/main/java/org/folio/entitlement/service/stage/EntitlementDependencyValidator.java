package org.folio.entitlement.service.stage;

import static java.util.Collections.emptySet;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toMap;
import static org.apache.commons.collections4.CollectionUtils.isNotEmpty;
import static org.folio.common.utils.CollectionUtils.mapItems;
import static org.folio.entitlement.domain.dto.EntitlementType.ENTITLE;
import static org.folio.entitlement.domain.dto.EntitlementType.REVOKE;
import static org.folio.entitlement.domain.dto.ExecutionStatus.FINISHED;
import static org.folio.entitlement.service.stage.StageContextUtils.getApplicationDescriptor;
import static org.folio.entitlement.service.stage.StageContextUtils.getApplicationId;
import static org.folio.entitlement.service.stage.StageContextUtils.getEntitlementRequest;
import static org.folio.entitlement.utils.SemverUtils.applicationSatisfies;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
import org.folio.entitlement.domain.dto.ApplicationFlow;
import org.folio.entitlement.domain.dto.Entitlement;
import org.folio.entitlement.integration.am.model.Dependency;
import org.folio.entitlement.service.EntitlementCrudService;
import org.folio.entitlement.service.flow.EntitlementFlowService;
import org.folio.entitlement.utils.SemverUtils;
import org.folio.flow.api.StageContext;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class EntitlementDependencyValidator extends DatabaseLoggingStage {

  @Lazy private final EntitlementFlowService entitlementFlowService;
  private final EntitlementCrudService entitlementCrudService;

  @Override
  @Transactional
  public void execute(StageContext context) {
    var applicationId = getApplicationId(context);
    var entitlementRequest = getEntitlementRequest(context);
    var applicationDescriptor = getApplicationDescriptor(context);
    var tenantId = entitlementRequest.getTenantId();

    if (entitlementRequest.getType() == REVOKE) {
      var installedDependentApplications = findInstalledDependentApplications(applicationId, tenantId);
      if (isNotEmpty(installedDependentApplications)) {
        throw new IllegalStateException(
          "The following applications must be uninstalled first: " + installedDependentApplications);
      }
      return;
    }

    var notInstalledApplications = findNotEntitledAppDependencies(applicationDescriptor.getDependencies(), tenantId);
    if (isNotEmpty(notInstalledApplications)) {
      throw new IllegalStateException(
        "The following application dependencies must be installed first: "
          + notInstalledApplications.stream().map(Dependency::nameVersion).sorted().collect(joining(", ")));
    }
  }

  private Set<Dependency> findNotEntitledAppDependencies(List<Dependency> dependencies, UUID tenantId) {
    if (CollectionUtils.isEmpty(dependencies)) {
      return emptySet();
    }

    var dependencyByName = dependencies.stream().collect(toMap(Dependency::getName, identity()));

    var lastFlows = entitlementFlowService.findLastFlowsByAppNames(dependencyByName.keySet(), tenantId);

    var entitledApplicationIds = lastFlows.stream()
      .filter(satisfiesDependencyVersion(dependencyByName)
        .and(EntitlementDependencyValidator::isFinishedEntitlement))
      .map(ApplicationFlow::getApplicationId);

    return removeEntitled(dependencyByName, entitledApplicationIds);
  }

  private List<String> findInstalledDependentApplications(String applicationId, UUID tenantId) {
    var installedEntitlements = entitlementCrudService.findInstalledDependentEntitlements(applicationId, tenantId);

    return mapItems(installedEntitlements, Entitlement::getApplicationId);
  }

  private static Predicate<ApplicationFlow> satisfiesDependencyVersion(Map<String, Dependency> dependencyByName) {
    return flow -> {
      var flowAppId = flow.getApplicationId();
      var dependencyVersionOrRange = dependencyByName.get(SemverUtils.getName(flowAppId)).getVersion();
      return applicationSatisfies(flowAppId, dependencyVersionOrRange);
    };
  }

  private static boolean isFinishedEntitlement(ApplicationFlow flow) {
    return flow.getStatus() == FINISHED && flow.getType() == ENTITLE;
  }

  private static Set<Dependency> removeEntitled(Map<String, Dependency> dependencyByName,
    Stream<String> entitledApplicationIds) {
    var deps = new HashMap<>(dependencyByName);

    entitledApplicationIds.forEach(removeDependencyByAppId(deps));

    return Set.copyOf(deps.values());
  }

  private static Consumer<String> removeDependencyByAppId(HashMap<String, Dependency> deps) {
    return appId -> {
      var name = SemverUtils.getName(appId);

      var dep = deps.get(name);
      if (dep != null && applicationSatisfies(appId, dep.getVersion())) {
        deps.remove(name);
      }
    };
  }
}
