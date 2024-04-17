package org.folio.entitlement.controller;

import static org.folio.entitlement.domain.model.ResultList.asSinglePage;
import static org.folio.entitlement.support.TestConstants.APPLICATION_ID;
import static org.folio.entitlement.support.TestConstants.OKAPI_TOKEN;
import static org.folio.entitlement.support.TestConstants.TENANT_ID;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.folio.common.utils.OkapiHeaders;
import org.folio.entitlement.domain.dto.Entitlement;
import org.folio.entitlement.service.EntitlementService;
import org.folio.entitlement.service.FlowStageService;
import org.folio.test.extensions.EnableKeycloakSecurity;
import org.folio.test.types.UnitTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@UnitTest
@EnableKeycloakSecurity
@Import({ControllerTestConfiguration.class, EntitlementController.class})
@MockBean(FlowStageService.class)
@WebMvcTest(EntitlementController.class)
@TestPropertySource(properties = "application.router.path-prefix=mgr-tenant-entitlements")
class RoutesPrefixControllerTest {

  @Autowired private MockMvc mockMvc;
  @MockBean private EntitlementService entitlementService;

  @Test
  void get_positive() throws Exception {
    var cqlQuery = String.format("tenantId=%s", TENANT_ID);
    when(entitlementService.findByQuery(cqlQuery, false, 10, 0)).thenReturn(asSinglePage(entitlement()));

    mockMvc.perform(get("/mgr-tenant-entitlements/entitlements")
        .param("query", cqlQuery)
        .param("limit", "10")
        .param("offset", "0")
        .header(OkapiHeaders.TOKEN, OKAPI_TOKEN)
        .contentType(APPLICATION_JSON))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.totalRecords", is(1)))
      .andExpect(jsonPath("$.entitlements[0].applicationId", is(APPLICATION_ID)))
      .andExpect(jsonPath("$.entitlements[0].tenantId", is(TENANT_ID.toString())));
  }

  private static Entitlement entitlement() {
    return new Entitlement().applicationId(APPLICATION_ID).tenantId(TENANT_ID);
  }
}
