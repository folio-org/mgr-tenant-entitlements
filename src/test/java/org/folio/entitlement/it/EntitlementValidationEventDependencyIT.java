package org.folio.entitlement.it;

import static org.folio.entitlement.domain.dto.EntitlementRequestType.ENTITLE;
import static org.folio.entitlement.support.TestConstants.TENANT_ID;
import static org.folio.entitlement.support.TestUtils.asJsonString;
import static org.folio.entitlement.support.TestValues.entitlement;
import static org.folio.entitlement.support.TestValues.entitlementRequest;
import static org.folio.entitlement.support.TestValues.extendedEntitlements;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.context.jdbc.Sql.ExecutionPhase.AFTER_TEST_METHOD;
import static org.springframework.test.context.jdbc.SqlMergeMode.MergeMode.MERGE;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import org.folio.entitlement.domain.dto.EntitlementRequestType;
import org.folio.entitlement.exception.RequestValidationException;
import org.folio.entitlement.service.validator.EventDependencyValidator;
import org.folio.entitlement.support.base.BaseIntegrationTest;
import org.folio.test.extensions.WireMockStub;
import org.folio.test.types.IntegrationTest;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.context.jdbc.SqlMergeMode;
import org.springframework.test.web.servlet.ResultActions;

class EntitlementValidationEventDependencyIT {

  private static final String VALIDATOR = EventDependencyValidator.class.getSimpleName();
  private static final String VALIDATE_URL =
    "/entitlements/validate?entitlementRequestType={type}&validator={validator}";

  private static final String MATCHED_APP_ID = "evt-matched-1.0.0";
  private static final String REQUIRED_MISSING_APP_ID = "evt-required-1.0.0";
  private static final String OPTIONAL_MISSING_APP_ID = "evt-optional-1.0.0";
  private static final String ENTITLED_PRODUCER_APP_ID = "folio-app1-1.0.0";
  private static final String CONSUMER_OF_ENTITLED_EVENT_APP_ID = "evt-consumer-1.0.0";

  @Nested
  @IntegrationTest
  @SqlMergeMode(MERGE)
  @Sql(scripts = "classpath:/sql/truncate-tables.sql", executionPhase = AFTER_TEST_METHOD)
  @TestPropertySource(properties = {
    "application.keycloak.enabled=false",
    "application.apigw.enabled=false",
    "application.validation.event-dependency.strict-event-validation=false"
  })
  class StrictValidationDisabled extends ValidationIT {

    @Test
    @WireMockStub(scripts = "/wiremock/mgr-applications/event-dependency/get-by-ids-required-missing.json")
    void validate_positive_requiredEventWithoutProducerOnlyWarns() throws Exception {
      validate(REQUIRED_MISSING_APP_ID).andExpect(status().isNoContent());
    }

    @Test
    @WireMockStub(scripts = "/wiremock/mgr-applications/event-dependency/get-by-ids-optional-missing.json")
    void validate_positive_optionalEventWithoutProducerOnlyWarns() throws Exception {
      validate(OPTIONAL_MISSING_APP_ID).andExpect(status().isNoContent());
    }
  }

  @Nested
  @IntegrationTest
  @SqlMergeMode(MERGE)
  @Sql(scripts = "classpath:/sql/truncate-tables.sql", executionPhase = AFTER_TEST_METHOD)
  @TestPropertySource(properties = {
    "application.keycloak.enabled=false",
    "application.apigw.enabled=false",
    "application.validation.event-dependency.strict-event-validation=true"
  })
  class StrictValidationEnabled extends ValidationIT {

    @Test
    @WireMockStub(scripts = "/wiremock/mgr-applications/event-dependency/get-by-ids-matched.json")
    void validate_positive_matchedEvent() throws Exception {
      validate(MATCHED_APP_ID).andExpect(status().isNoContent());
    }

    @Test
    @WireMockStub(scripts = "/wiremock/mgr-applications/event-dependency/get-by-ids-required-missing.json")
    void validate_negative_requiredEventWithoutProducerFails() throws Exception {
      validate(REQUIRED_MISSING_APP_ID)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].message",
          containsString("Consumed events without producers found: evt.missing")))
        .andExpect(jsonPath("$.errors[0].type", is(RequestValidationException.class.getSimpleName())))
        .andExpect(jsonPath("$.errors[0].code", is("validation_error")))
        .andExpect(jsonPath("$.errors[0].parameters[0].key", is("evt.missing")))
        .andExpect(jsonPath("$.errors[0].parameters[0].value", is(REQUIRED_MISSING_APP_ID)));
    }

    @Test
    @WireMockStub(scripts = "/wiremock/mgr-applications/event-dependency/get-by-ids-optional-missing.json")
    void validate_positive_optionalEventWithoutProducerNeverFails() throws Exception {
      validate(OPTIONAL_MISSING_APP_ID).andExpect(status().isNoContent());
    }

    @ParameterizedTest
    @EnumSource(value = EntitlementRequestType.class, names = {"UPGRADE", "STATE"})
    @WireMockStub(scripts = "/wiremock/mgr-applications/event-dependency/get-by-ids-required-missing.json")
    void validate_negative_requiredEventWithoutProducerFailsUpgradeAndState(EntitlementRequestType type)
      throws Exception {
      validate(REQUIRED_MISSING_APP_ID, type)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].message",
          containsString("Consumed events without producers found: evt.missing")))
        .andExpect(jsonPath("$.errors[0].parameters[0].key", is("evt.missing")));
    }

    @ParameterizedTest
    @EnumSource(value = EntitlementRequestType.class, names = {"UPGRADE", "STATE"})
    @WireMockStub(scripts = "/wiremock/mgr-applications/event-dependency/get-by-ids-matched.json")
    void validate_positive_matchedEventForUpgradeAndState(EntitlementRequestType type) throws Exception {
      validate(MATCHED_APP_ID, type).andExpect(status().isNoContent());
    }

    @Test
    @WireMockStub(scripts = {
      "/wiremock/mgr-tenants/test/get.json",
      "/wiremock/mgr-applications/event-dependency/get-by-ids-entitled-producer.json",
      "/wiremock/mgr-applications/folio-app1/get-discovery.json",
      "/wiremock/mgr-applications/validate-any-descriptor.json",
      "/wiremock/folio-module1/install.json",
      "/wiremock/mgr-applications/event-dependency/get-by-ids-consumer-and-entitled-producer.json"
    })
    void validate_positive_eventProducedByEntitledApplicationOutsideDependencyChain() throws Exception {
      var queryParams = Map.of("tenantParameters", "loadReference=true", "ignoreErrors", "true");
      entitleApplications(entitlementRequest(ENTITLED_PRODUCER_APP_ID), queryParams,
        extendedEntitlements(entitlement(ENTITLED_PRODUCER_APP_ID)));

      validate(CONSUMER_OF_ENTITLED_EVENT_APP_ID).andExpect(status().isNoContent());
    }

    @Test
    @WireMockStub(scripts = {
      "/wiremock/mgr-tenants/test/get.json",
      "/wiremock/mgr-applications/event-dependency/get-by-ids-required-missing.json"
    })
    void entitle_negative_requiredEventWithoutProducerFailsEntitlement() throws Exception {
      mockMvc.perform(post(updatePathWithPrefix("/entitlements"))
          .contentType(APPLICATION_JSON)
          .header(TOKEN, getSystemAccessToken())
          .content(asJsonString(entitlementRequest(REQUIRED_MISSING_APP_ID))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].type", is("FlowCancelledException")))
        .andExpect(jsonPath("$.errors[0].parameters[0].key", is(VALIDATOR)))
        .andExpect(jsonPath("$.errors[0].parameters[0].value",
          containsString("Consumed events without producers found: evt.missing")));
    }
  }

  private abstract static class ValidationIT extends BaseIntegrationTest {

    ResultActions validate(String appId) throws Exception {
      return validate(appId, ENTITLE);
    }

    ResultActions validate(String appId, EntitlementRequestType type) throws Exception {
      return attemptPost(VALIDATE_URL, entitlementRequest(TENANT_ID, appId), type.getValue(), VALIDATOR);
    }
  }
}
