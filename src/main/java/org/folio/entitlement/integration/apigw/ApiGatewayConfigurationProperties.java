package org.folio.entitlement.integration.apigw;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@NoArgsConstructor
@ConfigurationProperties(prefix = "application.apigw")
public class ApiGatewayConfigurationProperties {

  private RouteManagementProperties routeManagement = new RouteManagementProperties();
  private TenantChecksProperties tenantChecks = new TenantChecksProperties();

  @Data
  @NoArgsConstructor
  public static class RouteManagementProperties {
    private boolean enabled = true;
  }

  @Data
  @NoArgsConstructor
  public static class TenantChecksProperties {
    private boolean enabled = false;
  }
}
