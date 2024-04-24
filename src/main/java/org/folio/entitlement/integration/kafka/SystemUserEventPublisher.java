package org.folio.entitlement.integration.kafka;

import static java.util.stream.Collectors.toMap;
import static org.folio.common.utils.CollectionUtils.toStream;

import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.common.domain.model.ModuleDescriptor;
import org.folio.entitlement.domain.model.ApplicationStageContext;
import org.folio.entitlement.integration.am.model.ApplicationDescriptor;
import org.folio.entitlement.integration.am.model.Module;
import org.folio.entitlement.service.stage.DatabaseLoggingStage;
import org.springframework.stereotype.Component;

@Log4j2
@Component
@RequiredArgsConstructor
public class SystemUserEventPublisher extends DatabaseLoggingStage<ApplicationStageContext> {

  private final SystemUserModuleEventPublisher moduleEventPublisher;

  @Override
  public void execute(ApplicationStageContext context) {
    var descriptor = context.getApplicationDescriptor();
    var tenantName = context.getTenantName();

    var moduleIdNameMap = getModuleIdNames(descriptor);

    toStream(descriptor.getModuleDescriptors())
      .filter(SystemUserEventPublisher::hasSystemUser)
      .forEach(md -> moduleEventPublisher.sendSystemUserEvent(resolveModuleName(moduleIdNameMap, md), md, tenantName));
  }

  private static boolean hasSystemUser(ModuleDescriptor moduleDescriptor) {
    return moduleDescriptor.getUser() != null;
  }

  private static Map<String, String> getModuleIdNames(ApplicationDescriptor descriptor) {
    return toStream(descriptor.getModules()).collect(toMap(Module::getId, Module::getName));
  }

  private static String resolveModuleName(Map<String, String> moduleIdNameMap, ModuleDescriptor descriptor) {
    return moduleIdNameMap.get(descriptor.getId());
  }
}
