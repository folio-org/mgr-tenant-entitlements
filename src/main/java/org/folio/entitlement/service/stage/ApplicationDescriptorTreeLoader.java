package org.folio.entitlement.service.stage;

import static java.lang.String.join;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.counting;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toMap;
import static org.apache.commons.collections4.CollectionUtils.isEmpty;
import static org.apache.commons.collections4.CollectionUtils.isNotEmpty;
import static org.folio.common.utils.CollectionUtils.mapItems;
import static org.folio.common.utils.CollectionUtils.toStream;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.common.domain.model.ApplicationDescriptor;
import org.folio.common.domain.model.Dependency;
import org.folio.common.utils.SemverUtils;
import org.folio.entitlement.domain.entity.EntitlementEntity;
import org.folio.entitlement.domain.model.CommonStageContext;
import org.folio.entitlement.domain.model.EntitlementRequest;
import org.folio.entitlement.exception.RequestValidationException;
import org.folio.entitlement.exception.RequestValidationException.Params;
import org.folio.entitlement.repository.EntitlementRepository;
import org.folio.entitlement.service.ApplicationManagerService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Log4j2
@Service
@RequiredArgsConstructor
public class ApplicationDescriptorTreeLoader extends DatabaseLoggingStage<CommonStageContext> {

  private final ApplicationManagerService applicationManagerService;
  private final EntitlementRepository entitlementRepository;

  @Override
  @Transactional(readOnly = true)
  public void execute(CommonStageContext context) {
    var allApplicationDescriptors = load(context.getEntitlementRequest());
    context.withApplicationDescriptors(allApplicationDescriptors);
  }

  /**
   * Loads application descriptors with all dependent descriptors.
   *
   * @param entitlementRequest - entitlement request to process
   * @return {@link List} with loaded {@link ApplicationDescriptor} object
   */
  public List<ApplicationDescriptor> load(EntitlementRequest entitlementRequest) {
    var tenantId = entitlementRequest.getTenantId();
    var token = entitlementRequest.getOkapiToken();
    var applicationIds = toStream(entitlementRequest.getApplications()).sorted().toList();
    var descriptors = applicationManagerService.getApplicationDescriptors(applicationIds, token);
    var allApplications = descriptors.stream().collect(toMap(ApplicationDescriptor::getName, identity()));

    for (var descriptor : descriptors) {
      loadInstalledAppDependencies(descriptor, allApplications, tenantId, token);
    }

    return List.copyOf(allApplications.values());
  }

  private void loadInstalledAppDependencies(ApplicationDescriptor descriptor,
    Map<String, ApplicationDescriptor> accumulator, UUID tenantId, String token) {
    if (isEmpty(descriptor.getDependencies())) {
      return;
    }

    log.debug("Loading dependencies for application: appId = {}, dependencies = {}", descriptor.getId(),
      descriptor.getDependencies());

    var dependenciesToBeLoaded = collectUnresolvedDependencies(descriptor, accumulator);
    log.debug("List of unresolved dependencies: {}", () -> mapItems(dependenciesToBeLoaded.values(),
      Dependency::nameVersion));

    var entitled = loadEntitlementsByDependencyNames(tenantId, dependenciesToBeLoaded.keySet());

    var dependencyIds = resolveDependenciesToEntitledApps(dependenciesToBeLoaded, entitled, descriptor);
    log.debug("Dependencies resolved to the following applications: {}", dependencyIds);

    var applicationIds = toStream(dependencyIds).sorted().toList();
    var dependencyApps = applicationManagerService.getApplicationDescriptors(applicationIds, token);
    for (var dependencyDescriptor : dependencyApps) {
      var dependencyId = dependencyDescriptor.getId();

      accumulator.put(dependencyId, dependencyDescriptor);
      log.debug("Dependency loaded: dependencyId = {}", dependencyId);

      loadInstalledAppDependencies(dependencyDescriptor, accumulator, tenantId, token);
    }
  }

  private static Set<String> resolveDependenciesToEntitledApps(Map<String, Dependency> dependenciesToBeLoaded,
    List<EntitlementEntity> entitled, ApplicationDescriptor descriptor) {
    var dependencyIds = new HashSet<String>();

    for (var entitlement : entitled) {
      var entitledAppName = entitlement.getApplicationName();
      var dependency = dependenciesToBeLoaded.get(entitledAppName);

      testEntitledAppMatchesWithDependency(entitlement, dependency, descriptor);

      dependencyIds.add(entitlement.getApplicationId());
    }

    return dependencyIds;
  }

  private static void testEntitledAppMatchesWithDependency(EntitlementEntity entitlement, Dependency dependency,
    ApplicationDescriptor descriptor) {
    var appVersion = entitlement.getApplicationVersion();
    var depVersion = dependency.getVersion();
    log.debug("Testing if entitled application version satisfies the dependency: application = {}, dependency = [{}],"
      + " entitledApp = {}", descriptor::getId, dependency::nameVersion, entitlement::getApplicationId);

    if (versionNotMatches(appVersion, depVersion)) {
      throw new RequestValidationException(
        "Version mismatch between installed application and dependency of application with id: " + descriptor.getId(),
        new Params().add("installedApplication", nameVersion(entitlement))
          .add("dependency", dependency.nameVersion()));
    }
  }

  private static boolean versionNotMatches(String version, String toBeMatchedWith) {
    var result = SemverUtils.satisfies(version, toBeMatchedWith);
    log.debug("Version matching result: matches = {}, version = {}, range = [{}]", result, version, toBeMatchedWith);

    return !result;
  }

  private static String nameVersion(EntitlementEntity entitlement) {
    return join(" ", entitlement.getApplicationName(), entitlement.getApplicationVersion());
  }

  private static Map<String, Dependency> collectUnresolvedDependencies(ApplicationDescriptor descriptor,
    Map<String, ApplicationDescriptor> accumulator) {
    var result = new HashMap<String, Dependency>();

    for (var dependency : descriptor.getDependencies()) {
      var depName = dependency.getName();
      var app = accumulator.get(depName);

      if (app == null) {
        result.put(depName, dependency);
      } else {
        var appVersion = app.getVersion();
        // test if already selected version of the application satisfies the dependency and fail if it doesn't
        if (versionNotMatches(appVersion, dependency.getVersion())) {
          throw new RequestValidationException("Dependency cannot be satisfied by the selected version of application",
            new Params().add("dependency", dependency.nameVersion()).add("application", app.nameVersion()));
        }
      }
    }

    return result;
  }

  private List<EntitlementEntity> loadEntitlementsByDependencyNames(UUID tenantId, Set<String> names) {
    var entitled = entitlementRepository.findByTenantIdAndApplicationNameIn(tenantId, List.copyOf(names));
    checkAllEntitlementsFound(names, entitled);

    return entitled;
  }

  private static void checkAllEntitlementsFound(Set<String> applicationNames, List<EntitlementEntity> entitled) {
    var appNameCount = entitled.stream().collect(groupingBy(EntitlementEntity::getApplicationName, counting()));

    var notFoundOrTooMany = new HashSet<>(applicationNames);

    appNameCount.forEach((appName, count) -> {
      if (count == 1) {
        notFoundOrTooMany.remove(appName);
      }
    });

    if (isNotEmpty(notFoundOrTooMany)) {
      throw new RequestValidationException(
        "Entitled application(s) is not found or too many application(s) found by the given name(s)",
        "applicationName(s)", join(", ", notFoundOrTooMany));
    }
  }
}
