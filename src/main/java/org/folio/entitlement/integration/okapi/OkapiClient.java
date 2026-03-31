package org.folio.entitlement.integration.okapi;

import static org.folio.common.utils.OkapiHeaders.TOKEN;

import java.util.Collection;
import java.util.List;
import org.folio.entitlement.integration.okapi.model.DeploymentDescriptor;
import org.folio.entitlement.integration.okapi.model.TenantModuleDescriptor;
import org.springframework.lang.Nullable;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.PostExchange;

public interface OkapiClient {

  @GetExchange("/_/discovery/modules/{srvId}")
  List<DeploymentDescriptor> getDiscovery(
    @PathVariable("srvId") String srvId,
    @RequestHeader(TOKEN) String token);

  @PostExchange("/_/proxy/tenants/{tenant}/install")
  List<TenantModuleDescriptor> installTenantModules(
    @PathVariable(name = "tenant") String tenantId,
    @RequestParam("reinstall") boolean reinstall,
    @RequestParam("purge") boolean purge,
    @Nullable @RequestParam("tenantParameters") String tenantParameters,
    @RequestParam("ignoreErrors") boolean ignoreErrors,
    @RequestParam("async") boolean async,
    @RequestBody Collection<TenantModuleDescriptor> descriptors,
    @RequestHeader(TOKEN) String token);
}
