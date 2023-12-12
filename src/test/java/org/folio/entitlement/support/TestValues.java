package org.folio.entitlement.support;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static org.awaitility.Durations.ONE_SECOND;
import static org.folio.common.utils.OkapiHeaders.MODULE_ID;
import static org.folio.entitlement.domain.model.ResultList.asSinglePage;
import static org.folio.entitlement.support.TestConstants.APPLICATION_ID;
import static org.folio.entitlement.support.TestConstants.APPLICATION_NAME;
import static org.folio.entitlement.support.TestConstants.APPLICATION_VERSION;
import static org.folio.entitlement.support.TestConstants.TENANT_DESC;
import static org.folio.entitlement.support.TestConstants.TENANT_ID;
import static org.folio.entitlement.support.TestConstants.TENANT_NAME;
import static org.folio.entitlement.utils.SemverUtils.getName;
import static org.folio.entitlement.utils.SemverUtils.getVersion;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.folio.entitlement.domain.dto.Entitlement;
import org.folio.entitlement.domain.dto.EntitlementRequestBody;
import org.folio.entitlement.domain.dto.Entitlements;
import org.folio.entitlement.domain.dto.ExtendedEntitlement;
import org.folio.entitlement.domain.dto.ExtendedEntitlements;
import org.folio.entitlement.domain.entity.ApplicationDependencyEntity;
import org.folio.entitlement.domain.entity.EntitlementEntity;
import org.folio.entitlement.domain.entity.key.EntitlementModuleEntity;
import org.folio.entitlement.domain.model.ResultList;
import org.folio.entitlement.integration.am.model.ApplicationDescriptor;
import org.folio.entitlement.integration.am.model.Dependency;
import org.folio.entitlement.integration.am.model.Module;
import org.folio.entitlement.integration.am.model.ModuleDiscovery;
import org.folio.entitlement.integration.tm.model.Tenant;
import org.folio.flow.api.FlowEngine;
import org.folio.security.domain.model.descriptor.ModuleDescriptor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class TestValues {

  public static ApplicationDescriptor applicationDescriptor() {
    return applicationDescriptor(APPLICATION_ID, APPLICATION_NAME, APPLICATION_VERSION);
  }

  public static ApplicationDescriptor applicationDescriptor(String appId) {
    return applicationDescriptor(appId, getName(appId), getVersion(appId));
  }

  public static ApplicationDescriptor applicationDescriptor(String id, String name, String version) {
    return new ApplicationDescriptor().id(id)
      .name(name)
      .version(version);
  }

  public static ApplicationDescriptor applicationDescriptor(String appId, Dependency... dependencies) {
    return applicationDescriptor(appId).dependencies(List.of(dependencies));
  }

  public static Dependency dependency(String depId) {
    return Dependency.of(getName(depId), getVersion(depId));
  }

  public static ApplicationDescriptor simpleApplicationDescriptor(String id) {
    var applicationDescriptor = applicationDescriptor(id, "test-app", "1.0.0");

    var moduleDescriptor = new ModuleDescriptor();
    moduleDescriptor.setId("mod-bar-1.7.9");

    applicationDescriptor.setModules(List.of(module("mod-bar", "1.7.9")));
    applicationDescriptor.setModuleDescriptors(List.of(moduleDescriptor));
    return applicationDescriptor;
  }

  public static ApplicationDescriptor uiApplicationDescriptor() {
    var applicationDescriptor = applicationDescriptor(APPLICATION_ID, "test-app", "1.0.0");

    var moduleDescriptor = new ModuleDescriptor();
    moduleDescriptor.setId("mod-bar-1.7.9");

    applicationDescriptor.setUiModules(List.of(module("mod-bar", "1.7.9")));
    applicationDescriptor.setUiModuleDescriptors(List.of(moduleDescriptor));
    return applicationDescriptor;
  }

  public static EntitlementEntity entitlementEntity() {
    var entity = new EntitlementEntity();
    entity.setApplicationId(APPLICATION_ID);
    entity.setTenantId(TENANT_ID);
    return entity;
  }

  public static EntitlementEntity entitlementEntity(String applicationId, UUID tenantId) {
    var entity = new EntitlementEntity();
    entity.setApplicationId(applicationId);
    entity.setTenantId(tenantId);
    return entity;
  }

  public static Entitlement entitlement() {
    return entitlement(TENANT_ID, APPLICATION_ID);
  }

  public static Entitlement entitlement(String applicationId) {
    return entitlement(TENANT_ID, applicationId);
  }

  public static Entitlement entitlement(UUID tenantId, String applicationId) {
    return new Entitlement().applicationId(applicationId).tenantId(tenantId);
  }

  public static Entitlement entitlementWithModules(UUID tenantId, String applicationId, List<String> modules) {
    return new Entitlement().applicationId(applicationId).tenantId(tenantId).modules(modules);
  }

  public static ExtendedEntitlement extendedEntitlement() {
    return extendedEntitlement(null, TENANT_ID, APPLICATION_ID);
  }

  public static ExtendedEntitlement extendedEntitlement(String applicationId) {
    return extendedEntitlement(null, TENANT_ID, applicationId);
  }

  public static ExtendedEntitlement extendedEntitlement(UUID tenantId, String applicationId) {
    return extendedEntitlement(null, tenantId, applicationId);
  }

  public static ExtendedEntitlement extendedEntitlement(UUID flowId, UUID tenantId, String applicationId) {
    return new ExtendedEntitlement().flowId(flowId).applicationId(applicationId).tenantId(tenantId);
  }

  public static Entitlements emptyEntitlements() {
    return new Entitlements().totalRecords(0).entitlements(emptyList());
  }

  public static Entitlements entitlements(Entitlement... entitlements) {
    return new Entitlements().totalRecords(entitlements.length).entitlements(asList(entitlements));
  }

  public static Entitlements entitlements(List<Entitlement> entitlements) {
    return new Entitlements().totalRecords(entitlements.size()).entitlements(entitlements);
  }

  public static ExtendedEntitlements extendedEntitlements(UUID flowId, ExtendedEntitlement... entitlements) {
    return new ExtendedEntitlements()
      .flowId(flowId)
      .totalRecords(entitlements.length)
      .entitlements(asList(entitlements));
  }

  public static ExtendedEntitlements extendedEntitlements(ExtendedEntitlement... entitlements) {
    return new ExtendedEntitlements().totalRecords(entitlements.length).entitlements(asList(entitlements));
  }

  public static EntitlementModuleEntity entitlementModuleEntity() {
    var entity = new EntitlementModuleEntity();
    entity.setApplicationId(APPLICATION_ID);
    entity.setTenantId(TENANT_ID);
    entity.setModuleId(MODULE_ID);
    return entity;
  }

  public static Module module() {
    return module(APPLICATION_NAME, APPLICATION_VERSION);
  }

  public static Module module(String name, String version) {
    return Module.of(name + "-" + version, name, version);
  }

  public static ModuleDiscovery moduleDiscovery() {
    var entity = new ModuleDiscovery();
    entity.setId("mod-bar-1.7.9");
    entity.setLocation("http://mod-bar:8080");
    entity.setName("mod-bar");
    entity.setVersion("1.7.9");
    return entity;
  }

  public static ResultList<ModuleDiscovery> moduleDiscoveries() {
    return asSinglePage(moduleDiscovery());
  }

  public static Dependency applicationDependency(String name, String version) {
    return Dependency.of(name, version);
  }

  public static ApplicationDependencyEntity applicationDependencyEntity(Dependency dependency) {
    var entity = new ApplicationDependencyEntity();
    entity.setApplicationId(APPLICATION_ID);
    entity.setTenantId(TENANT_ID);
    entity.setParentName(dependency.getName());
    entity.setParentVersion(dependency.getVersion());
    return entity;
  }

  public static Tenant tenant() {
    return Tenant.of(TENANT_ID, TENANT_NAME, TENANT_DESC);
  }

  public static EntitlementRequestBody entitlementRequest() {
    return entitlementRequest(TENANT_ID, APPLICATION_ID);
  }

  public static EntitlementRequestBody entitlementRequest(String applicationId) {
    return entitlementRequest(TENANT_ID, applicationId);
  }

  public static EntitlementRequestBody entitlementRequest(UUID tenantId, String applicationId) {
    return new EntitlementRequestBody().applications(List.of(applicationId)).tenantId(tenantId);
  }

  public static EntitlementRequestBody entitlementRequest(UUID tenantId, String... applicationIds) {
    return new EntitlementRequestBody().applications(List.of(applicationIds)).tenantId(tenantId);
  }

  public static String queryByTenantAndAppId() {
    return queryByTenantAndAppId(TENANT_ID, APPLICATION_ID);
  }

  public static String queryByTenantAndAppId(String applicationId) {
    return queryByTenantAndAppId(TENANT_ID, applicationId);
  }

  public static String queryByTenantAndAppId(UUID tenantId, String applicationId) {
    return String.format("applicationId == %s and tenantId == %s", applicationId, tenantId);
  }

  public static FlowEngine singleThreadFlowEngine(String name, boolean showReport) {
    return FlowEngine.builder()
      .executor(Executors.newSingleThreadExecutor())
      .name(name)
      .printFlowResult(showReport)
      .executionTimeout(ONE_SECOND)
      .build();
  }
}
