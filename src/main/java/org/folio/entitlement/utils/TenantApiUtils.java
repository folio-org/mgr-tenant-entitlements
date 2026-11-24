package org.folio.entitlement.utils;

import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.StringUtils;
import org.folio.common.domain.model.InterfaceDescriptor;
import org.folio.common.domain.model.RoutingEntry;

@UtilityClass
public class TenantApiUtils {

  public static final String TENANT_ENDPOINT = "/_/tenant";
  public static final String DISABLE_TENANT_ENDPOINT = TENANT_ENDPOINT + "/disable";

  /**
   * Asks the module's sidecar to route the module's own outbound calls made while handling the tenant API through the
   * API gateway, which is already updated, instead of using its possibly stale entitlement cache. The {@code x-okapi-}
   * prefix is kept, so frameworks that copy {@code x-okapi-*} headers propagate it to the calls the module makes.
   */
  public static final String EGRESS_USE_GATEWAY_HEADER = "x-okapi-egress-use-gateway";
  public static final String EGRESS_USE_GATEWAY_VALUE = "true";

  private static final String LEGACY_TENANT_INTERFACE_VERSION_PREFIX = "1.";

  public static boolean isLegacyApi(InterfaceDescriptor tenantInterface) {
    return tenantInterface.getVersion().startsWith(LEGACY_TENANT_INTERFACE_VERSION_PREFIX);
  }

  public static boolean supportsDisable(InterfaceDescriptor tenantInterface) {
    return tenantInterface.getHandlers().stream()
      .map(TenantApiUtils::getStaticPath)
      .anyMatch(path -> path.equals(DISABLE_TENANT_ENDPOINT));
  }

  private static String getStaticPath(RoutingEntry routingEntry) {
    return StringUtils.getIfEmpty(routingEntry.getPath(), routingEntry::getPathPattern);
  }
}
