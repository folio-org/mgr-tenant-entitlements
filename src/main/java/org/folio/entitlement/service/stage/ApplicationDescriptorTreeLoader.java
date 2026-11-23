package org.folio.entitlement.service.stage;

import static java.lang.String.join;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.counting;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toMap;
import static java.util.stream.Collectors.toSet;
import static org.apache.commons.collections4.CollectionUtils.isEmpty;
import static org.apache.commons.collections4.CollectionUtils.isNotEmpty;
import static org.folio.common.utils.CollectionUtils.mapItems;
import static org.folio.entitlement.utils.EntitlementServiceUtils.filter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.common.domain.model.ApplicationDescriptor;
import org.folio.common.domain.model.Dependency;
import org.folio.common.utils.SemverUtils;
import org.folio.entitlement.domain.entity.EntitlementEntity;
import org.folio.entitlement.domain.entity.PlatformEntitlementEntity;
import org.folio.entitlement.domain.model.CommonStageContext;
import org.folio.entitlement.domain.model.EntitlementRequest;
import org.folio.entitlement.exception.RequestValidationException;
import org.folio.entitlement.exception.RequestValidationException.Params;
import org.folio.entitlement.repository.EntitlementRepository;
import org.folio.entitlement.repository.PlatformEntitlementRepository;
import org.folio.entitlement.service.ApplicationManagerService;
import org.folio.entitlement.service.validator.PlatformApplicationNotExistInRequestValidator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Log4j2
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class ApplicationDescriptorTreeLoader extends DatabaseLoggingStage<CommonStageContext> {

  private final ApplicationManagerService applicationManagerService;
  private final EntitlementRepository entitlementRepository;
  private final PlatformEntitlementRepository platformEntitlementRepository;
  private final PlatformApplicationNotExistInRequestValidator platformApplicationNotExistInRequestValidator;

  @Override
  public void execute(CommonStageContext context) {
    var request = context.getEntitlementRequest();

    var requestedDescriptors = loadRequested(request);

    // validate the directly requested descriptors before building the dependency tree
    platformApplicationNotExistInRequestValidator.validate(requestedDescriptors);

    var allApplicationDescriptors = load(request, requestedDescriptors);

    context.withApplicationDescriptors(filter(allApplicationDescriptors, applicationFromRequest(request)))
      .withApplicationAndDependencyDescriptors(allApplicationDescriptors);
  }

  /**
   * Loads application descriptors with all parent descriptors.
   *
   * @param entitlementRequest - entitlement request to process
   * @return {@link List} with loaded {@link ApplicationDescriptor} object
   */
  public List<ApplicationDescriptor> load(EntitlementRequest entitlementRequest) {
    return load(entitlementRequest, loadRequested(entitlementRequest));
  }

  private List<ApplicationDescriptor> load(EntitlementRequest entitlementRequest,
    List<ApplicationDescriptor> descriptors) {
    var tenantId = entitlementRequest.getTenantId();
    var token = entitlementRequest.getOkapiToken();
    var allApplications = descriptors.stream().collect(toMap(ApplicationDescriptor::getName, identity()));

    for (var descriptor : descriptors) {
      loadInstalledAppDependencies(descriptor, allApplications, tenantId, token);
    }

    return List.copyOf(allApplications.values());
  }

  private List<ApplicationDescriptor> loadRequested(EntitlementRequest entitlementRequest) {
    return applicationManagerService.getApplicationDescriptors(entitlementRequest.getApplications(),
      entitlementRequest.getOkapiToken());
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

    var dependencyApps = applicationManagerService.getApplicationDescriptors(dependencyIds, token);
    for (var dependencyDescriptor : dependencyApps) {
      var dependencyId = dependencyDescriptor.getId();

      accumulator.put(dependencyId, dependencyDescriptor);
      log.debug("Dependency loaded: dependencyId = {}", dependencyId);

      loadInstalledAppDependencies(dependencyDescriptor, accumulator, tenantId, token);
    }
  }

  private static Set<String> resolveDependenciesToEntitledApps(Map<String, Dependency> dependenciesToBeLoaded,
    List<EntitledApp> entitled, ApplicationDescriptor descriptor) {
    var dependencyIds = new HashSet<String>();

    for (var entitlement : entitled) {
      var dependency = dependenciesToBeLoaded.get(entitlement.name());

      testEntitledAppMatchesWithDependency(entitlement, dependency, descriptor);

      dependencyIds.add(entitlement.id());
    }

    return dependencyIds;
  }

  private static void testEntitledAppMatchesWithDependency(EntitledApp entitlement, Dependency dependency,
    ApplicationDescriptor descriptor) {
    var depVersion = dependency.getVersion();
    log.debug("Testing if entitled application version satisfies the dependency: application = {}, dependency = [{}],"
      + " entitledApp = {}", descriptor::getId, dependency::nameVersion, entitlement::id);

    if (versionNotMatches(entitlement.version(), depVersion)) {
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

  private static String nameVersion(EntitledApp entitlement) {
    return join(" ", entitlement.name(), entitlement.version());
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

  private List<EntitledApp> loadEntitlementsByDependencyNames(UUID tenantId, Set<String> names) {
    var tenantEntitled = entitlementRepository.findByTenantIdAndApplicationNameIn(tenantId, List.copyOf(names));
    var tenantEntitledNames = tenantEntitled.stream().map(EntitlementEntity::getApplicationName).collect(toSet());

    // dependencies that are not entitled for the tenant may be entitled platform applications
    var platformNames = names.stream().filter(name -> !tenantEntitledNames.contains(name)).toList();
    var platformEntitled = platformNames.isEmpty()
      ? List.<PlatformEntitlementEntity>of()
      : platformEntitlementRepository.findAllById(platformNames);

    var result = new ArrayList<EntitledApp>();
    tenantEntitled.forEach(e -> result.add(
      new EntitledApp(e.getApplicationName(), e.getApplicationId(), e.getApplicationVersion())));
    platformEntitled.forEach(e -> result.add(
      new EntitledApp(e.getApplicationName(), e.getApplicationId(), e.getApplicationVersion())));

    checkAllEntitlementsFound(names, result);

    return result;
  }

  private static void checkAllEntitlementsFound(Set<String> applicationNames, List<EntitledApp> entitled) {
    var appNameCount = entitled.stream().collect(groupingBy(EntitledApp::name, counting()));

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

  private static Predicate<ApplicationDescriptor> applicationFromRequest(EntitlementRequest entitlementRequest) {
    return appDescriptor -> entitlementRequest.getApplications().contains(appDescriptor.getId());
  }

  private record EntitledApp(String name, String id, String version) {}
}
