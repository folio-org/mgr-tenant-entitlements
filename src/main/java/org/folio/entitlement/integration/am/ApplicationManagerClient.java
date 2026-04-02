package org.folio.entitlement.integration.am;

import static org.folio.common.utils.OkapiHeaders.TOKEN;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

import org.folio.common.domain.model.ApplicationDescriptor;
import org.folio.common.utils.CqlQuery;
import org.folio.entitlement.domain.model.ResultList;
import org.folio.entitlement.integration.am.model.ModuleDiscovery;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

@HttpExchange(accept = APPLICATION_JSON_VALUE)
public interface ApplicationManagerClient {

  /**
   * Retrieves {@link ApplicationDescriptor} value by id from mgr-applications.
   *
   * @param applicationId - application descriptor identifier
   * @param full - defines if application descriptor must be loaded with all module descriptors
   * @param token - okapi auth token
   * @return retrieved {@link ApplicationDescriptor} object
   */
  @GetExchange("/applications/{id}")
  ApplicationDescriptor getApplicationDescriptor(
    @PathVariable("id") String applicationId,
    @RequestParam("full") Boolean full,
    @RequestHeader(TOKEN) String token);

  @GetExchange("/applications")
  ResultList<ApplicationDescriptor> queryApplicationDescriptors(
    @RequestParam("query") CqlQuery query,
    @RequestParam("full") Boolean full,
    @RequestParam("limit") int limit,
    @RequestParam("offset") int offset,
    @RequestHeader(TOKEN) String token);

  @PostExchange("/applications/validate")
  void validate(@RequestBody ApplicationDescriptor descriptor, @RequestHeader(TOKEN) String token);

  @GetExchange("/applications/{id}/discovery")
  ResultList<ModuleDiscovery> getModuleDiscoveries(
    @PathVariable("id") String applicationId,
    @RequestParam("limit") int limit,
    @RequestHeader(TOKEN) String token);
}
