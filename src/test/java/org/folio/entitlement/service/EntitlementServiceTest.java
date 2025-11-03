package org.folio.entitlement.service;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.folio.entitlement.domain.dto.EntitlementRequestType.ENTITLE;
import static org.folio.entitlement.domain.model.ResultList.asSinglePage;
import static org.folio.entitlement.support.TestConstants.APPLICATION_ID;
import static org.folio.entitlement.support.TestConstants.FLOW_ID;
import static org.folio.entitlement.support.TestConstants.OKAPI_TOKEN;
import static org.folio.entitlement.support.TestConstants.TENANT_ID;
import static org.folio.entitlement.support.TestConstants.TENANT_NAME;
import static org.folio.entitlement.support.TestValues.entitlement;
import static org.folio.entitlement.support.TestValues.extendedEntitlements;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.folio.entitlement.domain.model.EntitlementRequest;
import org.folio.entitlement.exception.RequestValidationException;
import org.folio.entitlement.integration.tm.TenantManagerService;
import org.folio.entitlement.service.flow.FlowProvider;
import org.folio.entitlement.support.TestUtils;
import org.folio.entitlement.support.TestValues;
import org.folio.flow.api.Flow;
import org.folio.flow.api.FlowEngine;
import org.folio.test.types.UnitTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class EntitlementServiceTest {

  @InjectMocks private EntitlementService entitlementService;
  @Mock private Flow flow;
  @Mock private FlowEngine flowEngine;
  @Mock private FlowProvider flowProvider;
  @Mock private TenantManagerService tenantManagerService;
  @Mock private EntitlementCrudService crudService;

  @AfterEach
  void tearDown() {
    TestUtils.verifyNoMoreInteractions(this);
  }

  @Nested
  @DisplayName("get")
  class Get {

    @Test
    void get_positive() {
      var cqlQuery = "cql.allRecords=1";
      var expectedEntitlement = TestValues.entitlement();
      when(crudService.findByQuery(cqlQuery, false, 0, 100)).thenReturn(asSinglePage(expectedEntitlement));

      var actual = entitlementService.findByQueryOrTenantName(cqlQuery, null, false, 0, 100, OKAPI_TOKEN);

      assertThat(actual).isEqualTo(asSinglePage(expectedEntitlement));
    }

    @Test
    void get_positive_byTenant() {
      var cqlQuery = "tenantId==" + TENANT_ID;
      var expectedEntitlement = TestValues.entitlement();

      when(tenantManagerService.findTenantByName(TENANT_NAME, OKAPI_TOKEN)).thenReturn(TestValues.tenant());
      when(crudService.findByQuery(cqlQuery, false, 0, 100)).thenReturn(asSinglePage(expectedEntitlement));

      var actual = entitlementService.findByQueryOrTenantName(null, TENANT_NAME, false, 0, 100, OKAPI_TOKEN);

      assertThat(actual).isEqualTo(asSinglePage(expectedEntitlement));
    }

    @Test
    void get_negative_queryAndTenantSpecified() {
      assertThatThrownBy(() -> entitlementService.findByQueryOrTenantName(
          "cql.allRecords=1", TENANT_NAME, false, 0, 100, OKAPI_TOKEN))
        .isInstanceOf(RequestValidationException.class)
        .hasMessage("Both 'query' and 'tenant' parameters are provided but only one of them has to be specified");
    }
  }

  @Nested
  @DisplayName("performRequest")
  class PerformRequest {

    @Test
    void positive_validatorIsCalled() {
      var request = entitlementRequest(false);
      var extendedEntitlements = extendedEntitlements(FLOW_ID, entitlement());
      when(flowProvider.createFlow(request)).thenReturn(flow);
      when(flow.getId()).thenReturn(FLOW_ID.toString());

      var actual = entitlementService.performRequest(request);

      assertThat(actual).isEqualTo(extendedEntitlements);
      verify(flowEngine).execute(flow);
    }

    @Test
    void positive_async() {
      var request = entitlementRequest(true);
      var extendedEntitlements = extendedEntitlements(FLOW_ID, entitlement());

      when(flowProvider.createFlow(request)).thenReturn(flow);
      when(flow.getId()).thenReturn(FLOW_ID.toString());
      when(flowEngine.executeAsync(flow)).thenReturn(completedFuture(null));

      var actual = entitlementService.performRequest(request);

      assertThat(actual).isEqualTo(extendedEntitlements);
      verify(flowEngine).executeAsync(flow);
    }

    private static EntitlementRequest entitlementRequest(boolean async) {
      return EntitlementRequest.builder()
        .applications(List.of(APPLICATION_ID))
        .tenantId(TENANT_ID)
        .async(async)
        .type(ENTITLE)
        .build();
    }
  }
}
