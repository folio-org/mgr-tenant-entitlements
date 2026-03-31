package org.folio.entitlement.integration.okapi;

import static org.folio.common.utils.OkapiHeaders.TOKEN;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

import java.util.Collection;
import java.util.List;
import org.folio.entitlement.integration.okapi.model.DeploymentDescriptor;
import org.folio.entitlement.integration.okapi.model.TenantModuleDescriptor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

@HttpExchange(accept = APPLICATION_JSON_VALUE)
public interface OkapiClient {

  @GetExchange("/_/discovery/modules/{srvId}")
  List<DeploymentDescriptor> getDiscovery(
    @PathVariable("srvId") String srvId,
    @RequestHeader(TOKEN) String token);

  @SuppressWarnings("java:S107")
  @PostExchange("/_/proxy/tenants/{tenant}/install")
  List<TenantModuleDescriptor> installTenantModules(
    @PathVariable(name = "tenant") String tenantId,
    @RequestParam(value = "reinstall", required = false) Boolean reinstall,
    @RequestParam(value = "purge", required = false) Boolean purge,
    @RequestParam(value = "tenantParameters", required = false) String tenantParameters,
    @RequestParam(value = "ignoreErrors", required = false) Boolean ignoreErrors,
    @RequestParam(value = "async", required = false) Boolean async,
    @RequestBody Collection<TenantModuleDescriptor> descriptors,
    @RequestHeader(TOKEN) String token);
}
