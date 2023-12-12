package org.folio.entitlement.it;

import java.util.List;
import org.folio.entitlement.integration.kong.KongAdminClient;
import org.folio.entitlement.integration.kong.model.KongRoute;
import org.folio.entitlement.integration.kong.model.KongService;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "kongTestAdminClient", url = "${kong.url}")
public interface KongTestAdminClient {

  /**
   * Updates a service in Kong gateway.
   *
   * @param serviceId - service name or id
   * @param service - service descriptor
   * @return created {@link KongService} object
   */
  @PutMapping("/services/{serviceId}")
  KongService upsertService(@PathVariable("serviceId") String serviceId, @RequestBody KongService service);

  /**
   * Deletes a service in Kong gateway by its name or id.
   *
   * @param serviceId - service name or id
   */
  @DeleteMapping("/services/{serviceId}")
  void deleteService(@PathVariable("serviceId") String serviceId);

  @DeleteMapping("/services/{serviceId}/routes/{routeId}")
  void deleteRoute(@PathVariable("serviceId") String serviceId, @PathVariable String routeId);

  /**
   * Deletes a route by id for {@code serviceName} in Kong gateway.
   *
   * @param tags - list of tags to search for
   * @return created {@link KongRoute} object
   */
  @GetMapping("/routes")
  KongAdminClient.KongResultList<KongRoute> getRoutesByTag(
    @RequestParam("tags") List<String> tags,
    @RequestParam(value = "offset", required = false) String offset);
}
