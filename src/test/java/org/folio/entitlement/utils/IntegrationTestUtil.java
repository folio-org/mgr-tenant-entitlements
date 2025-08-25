package org.folio.entitlement.utils;

import static java.util.regex.Pattern.compile;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.UnsupportedEncodingException;
import lombok.experimental.UtilityClass;
import org.folio.entitlement.domain.dto.Flow;
import org.folio.entitlement.domain.dto.FlowStage;
import org.folio.security.integration.keycloak.service.KeycloakStoreKeyProvider;
import org.folio.tools.store.properties.SecureStoreProperties;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;

@UtilityClass
public class IntegrationTestUtil {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  public static String extractFlowIdFromFailedEntitlementResponse(MockHttpServletResponse response)
    throws UnsupportedEncodingException {
    var responseText = response.getContentAsString();
    var matcher = compile(".*Flow '([A-Za-z0-9\\-]+)' finished with status: FAILED.*").matcher(responseText);
    assertThat(matcher.matches()).isTrue();
    return matcher.group(1);
  }

  public static FlowStage getFlowStage(String flowId, String stageName, MockMvc mockMvc) throws Exception {
    var flowData = OBJECT_MAPPER.readValue(
      mockMvc.perform(get("/entitlement-flows/" + flowId + "?includeStages=true")).andReturn().getResponse()
        .getContentAsByteArray(), Flow.class);
    return flowData.getApplicationFlows().get(0).getStages().stream().filter(s -> s.getName().equals(stageName))
      .findAny().orElseThrow();
  }

  public static KeycloakStoreKeyProvider getDefaultKeycloakStoreKeyProvider() {
    var secureStoreProperties = new SecureStoreProperties();
    secureStoreProperties.setEnvironment("folio");
    return new KeycloakStoreKeyProvider(secureStoreProperties);
  }
}
