package org.folio.entitlement.integration.kong;

import java.util.Iterator;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.folio.entitlement.integration.kong.model.KongRoute;
import org.folio.entitlement.integration.kong.model.KongService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

public interface KongAdminClient {

  /**
   * Creates a route for {@code serviceName} in Kong gateway.
   *
   * @param serviceId - service id or name
   * @param kongRoute - route descriptor
   * @return created {@link KongRoute} object
   */
  @PutMapping("/services/{serviceId}/routes/{routeId}")
  KongRoute upsertRoute(
    @PathVariable("serviceId") String serviceId,
    @PathVariable("routeId") String routeId,
    @RequestBody KongRoute kongRoute);

  /**
   * Deletes a route by id for {@code serviceName} in Kong gateway.
   *
   * @param serviceId - service id or name
   * @param routeId - route id
   */
  @DeleteMapping("/services/{serviceId}/routes/{routeId}")
  void deleteRoute(@PathVariable("serviceId") String serviceId, @PathVariable String routeId);

  /**
   * Deletes a route by id for {@code serviceName} in Kong gateway.
   *
   * @param tags - list of tags to search for
   * @return created {@link KongRoute} object
   */
  @GetMapping("/routes")
  KongResultList<KongRoute> getRoutesByTag(
    @RequestParam("tags") String tags,
    @RequestParam(value = "offset", required = false) String offset);

  /**
   * Retrieves {@link KongService} object by its id.
   *
   * @param serviceName - kong service name
   * @return retrieved {@link KongService} object
   */
  @GetMapping("/services/{id}")
  KongService getService(@PathVariable("id") String serviceName);

  /**
   * Result list object wrapper for get by tag endpoints.
   *
   * @param <T> - generic type for result list value
   */
  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  class KongResultList<T> implements Iterable<T> {

    /**
     * Next value identifier.
     */
    private String offset;

    /**
     * List with result objects.
     */
    private List<T> data;

    @Override
    public Iterator<T> iterator() {
      return data.iterator();
    }
  }
}
