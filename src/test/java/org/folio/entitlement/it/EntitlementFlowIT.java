package org.folio.entitlement.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.entitlement.domain.dto.EntitlementType.ENTITLE;
import static org.folio.entitlement.domain.dto.ExecutionStatus.FAILED;
import static org.folio.entitlement.domain.dto.ExecutionStatus.FINISHED;
import static org.folio.entitlement.support.TestUtils.parseResponse;
import static org.folio.test.TestConstants.OKAPI_AUTH_TOKEN;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.context.jdbc.Sql.ExecutionPhase.AFTER_TEST_METHOD;
import static org.springframework.test.context.jdbc.Sql.ExecutionPhase.BEFORE_TEST_METHOD;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import org.folio.common.utils.OkapiHeaders;
import org.folio.entitlement.domain.dto.ApplicationFlow;
import org.folio.entitlement.domain.dto.ApplicationFlows;
import org.folio.entitlement.domain.dto.Flow;
import org.folio.entitlement.domain.dto.FlowStage;
import org.folio.entitlement.domain.dto.FlowStages;
import org.folio.entitlement.support.base.BaseIntegrationTest;
import org.folio.test.extensions.EnableKeycloakSecurity;
import org.folio.test.extensions.EnableKeycloakTlsMode;
import org.folio.test.types.IntegrationTest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.keycloak.admin.client.Keycloak;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.jdbc.Sql;

@EnableKeycloakTlsMode
@IntegrationTest
@EnableKeycloakSecurity
@Sql(executionPhase = BEFORE_TEST_METHOD, scripts = "classpath:/sql/entitlement-flow-data.sql")
@Sql(executionPhase = AFTER_TEST_METHOD, scripts = "classpath:/sql/truncate-tables.sql")
class EntitlementFlowIT extends BaseIntegrationTest {

  private static final UUID APP_FLOW_ID_1 = UUID.fromString("bed2dc08-17f4-45f1-82da-84ffc65c5825");
  private static final UUID APP_FLOW_ID_2 = UUID.fromString("e9b839d8-140f-4ef2-b9db-38c289d220d6");
  private static final UUID TENANT_ID_1 = UUID.fromString("176317cb-c3aa-45be-a60b-47e73737eb55");
  private static final UUID TENANT_ID_2 = UUID.fromString("ae1aabc8-c329-476b-901e-991c0dda8426");
  private static final UUID FLOW_ID_1 = UUID.fromString("def173a0-7b4c-4f45-b66c-5fe4aa7c8f98");
  private static final UUID FLOW_ID_2 = UUID.fromString("3d94cd49-0ede-4426-81dc-416ff7deb187");

  @BeforeAll
  static void beforeAll(@Autowired Keycloak keycloak) {
    var accessTokenString = keycloak.tokenManager().getAccessTokenString();
    System.setProperty(SYSTEM_ACCESS_TOKEN_SYSTEM_PROPERTY_KEY, accessTokenString);
  }

  @AfterAll
  static void afterAll() {
    System.clearProperty(SYSTEM_ACCESS_TOKEN_SYSTEM_PROPERTY_KEY);
  }

  @Test
  void getEntitlementFlowById_positive() throws Exception {
    var entitlementFlowResponse = mockMvc.perform(get("/entitlement-flows/{flowId}", FLOW_ID_1)
        .contentType(APPLICATION_JSON)
        .header(OkapiHeaders.TOKEN, getSystemAccessToken()))
      .andExpect(status().isOk())
      .andExpect(content().contentType(APPLICATION_JSON))
      .andReturn();

    var entitlementFlow = parseResponse(entitlementFlowResponse, Flow.class);
    assertThat(entitlementFlow).isEqualTo(new Flow()
      .id(FLOW_ID_1).status(FINISHED).type(ENTITLE).tenantId(TENANT_ID_1)
      .startedAt(timestampFrom("2023-01-01T12:01:00"))
      .finishedAt(timestampFrom("2023-01-01T12:02:59"))
      .applicationFlows(List.of(applicationFlow1(), applicationFlow2())));
  }

  @Test
  void getEntitlementFlowById_positiveWithIncludeStages() throws Exception {
    var entitlementFlowResponse = mockMvc.perform(get("/entitlement-flows/{flowId}", FLOW_ID_1)
        .queryParam("includeStages", "true")
        .contentType(APPLICATION_JSON)
        .header(OkapiHeaders.TOKEN, getSystemAccessToken()))
      .andExpect(status().isOk())
      .andExpect(content().contentType(APPLICATION_JSON))
      .andReturn();

    var entitlementFlow = parseResponse(entitlementFlowResponse, Flow.class);
    assertThat(entitlementFlow).isEqualTo(new Flow()
      .id(FLOW_ID_1).status(FINISHED).type(ENTITLE).tenantId(TENANT_ID_1)
      .stages(List.of(
        stage(FLOW_ID_1, "FlowInitializer", "2023-01-01T12:01:01", "2023-01-01T12:01:02"),
        stage(FLOW_ID_1, "FlowFinalizer", "2023-01-01T12:02:46", "2023-01-01T12:02:50")))
      .startedAt(timestampFrom("2023-01-01T12:01:00"))
      .finishedAt(timestampFrom("2023-01-01T12:02:59"))
      .applicationFlows(List.of(
        applicationFlow1().stages(entitlementStages1()),
        applicationFlow2().stages(entitlementStages2()))));
  }

  @Test
  void getEntitlementFlowById_positive_failedFlow() throws Exception {
    var entitlementFlowResponse = mockMvc.perform(get("/entitlement-flows/{flowId}", FLOW_ID_2)
        .contentType(APPLICATION_JSON)
        .header(OkapiHeaders.TOKEN, OKAPI_AUTH_TOKEN))
      .andExpect(status().isOk())
      .andExpect(content().contentType(APPLICATION_JSON))
      .andReturn();

    var entitlementFlow = parseResponse(entitlementFlowResponse, Flow.class);
    assertThat(entitlementFlow).isEqualTo(new Flow()
      .id(FLOW_ID_2).status(FAILED).type(ENTITLE).tenantId(TENANT_ID_2)
      .startedAt(timestampFrom("2023-01-01T13:01:00"))
      .finishedAt(timestampFrom("2023-01-01T13:02:59"))
      .applicationFlows(List.of(applicationFlow3(), applicationFlow4())));
  }

  @Test
  void queryApplicationFlows_positive() throws Exception {
    var applicationFlowsResponse = mockMvc.perform(get("/application-flows")
        .queryParam("query", "tenantId==" + "176317cb-c3aa-45be-a60b-47e73737eb55")
        .queryParam("limit", "10")
        .queryParam("offset", "0")
        .contentType(APPLICATION_JSON)
        .header(OkapiHeaders.TOKEN, OKAPI_AUTH_TOKEN))
      .andExpect(status().isOk())
      .andExpect(content().contentType(APPLICATION_JSON))
      .andReturn();

    var applicationFlows = parseResponse(applicationFlowsResponse, ApplicationFlows.class);
    assertThat(applicationFlows).isEqualTo(new ApplicationFlows()
      .totalRecords(2)
      .applicationFlows(List.of(applicationFlow1(), applicationFlow2())));
  }

  @Test
  void getApplicationFlowById_positive() throws Exception {
    var mvcResult = mockMvc.perform(get("/application-flows/{applicationFlowId}", APP_FLOW_ID_1)
        .contentType(APPLICATION_JSON)
        .header(OkapiHeaders.TOKEN, getSystemAccessToken()))
      .andExpect(status().isOk())
      .andExpect(content().contentType(APPLICATION_JSON))
      .andReturn();

    var applicationFlow = parseResponse(mvcResult, ApplicationFlow.class);
    assertThat(applicationFlow).isEqualTo(applicationFlow1());
  }

  @Test
  void getApplicationFlowById_positive_includeStages() throws Exception {
    var mvcResult = mockMvc.perform(get("/application-flows/{applicationFlowId}", APP_FLOW_ID_1)
        .queryParam("includeStages", "true")
        .contentType(APPLICATION_JSON)
        .header(OkapiHeaders.TOKEN, getSystemAccessToken()))
      .andExpect(status().isOk())
      .andExpect(content().contentType(APPLICATION_JSON))
      .andReturn();

    var applicationFlow = parseResponse(mvcResult, ApplicationFlow.class);
    assertThat(applicationFlow).isEqualTo(applicationFlow1().stages(entitlementStages1()));
  }

  @Test
  void getApplicationFlowStages_positive() throws Exception {
    var entitlementStagesResponse = mockMvc.perform(
        get("/application-flows/{applicationFlowId}/stages", APP_FLOW_ID_1)
          .contentType(APPLICATION_JSON)
          .header(OkapiHeaders.TOKEN, getSystemAccessToken()))
      .andExpect(status().isOk())
      .andExpect(content().contentType(APPLICATION_JSON))
      .andReturn();

    var entitlementStages = parseResponse(entitlementStagesResponse, FlowStages.class);
    assertThat(entitlementStages).isEqualTo(
      new FlowStages().totalRecords(9).stages(entitlementStages1()));
  }

  @Test
  void getApplicationFlowStageByName_positive() throws Exception {
    var applicationFlowStageResponse = mockMvc.perform(
        get("/application-flows/{applicationFlowId}/stages/{name}", APP_FLOW_ID_1, "KongRouteCreator")
          .contentType(APPLICATION_JSON)
          .header(OkapiHeaders.TOKEN, getSystemAccessToken()))
      .andExpect(status().isOk())
      .andExpect(content().contentType(APPLICATION_JSON))
      .andReturn();

    var applicationFlowStages = parseResponse(applicationFlowStageResponse, FlowStage.class);
    var expectedStage = stage(APP_FLOW_ID_1, "KongRouteCreator", "2023-01-01T12:01:26", "2023-01-01T12:01:30");
    assertThat(applicationFlowStages).isEqualTo(expectedStage);
  }

  private static ApplicationFlow applicationFlow1() {
    return new ApplicationFlow().id(APP_FLOW_ID_1)
      .applicationId("test-app1-1.0.0")
      .tenantId(TENANT_ID_1)
      .flowId(FLOW_ID_1)
      .type(ENTITLE)
      .status(FINISHED)
      .startedAt(timestampFrom("2023-01-01T12:01:00"))
      .finishedAt(timestampFrom("2023-01-01T12:01:59"));
  }

  private static List<FlowStage> entitlementStages1() {
    return List.of(
      stage(APP_FLOW_ID_1, "EntitlementFlowInitializer", "2023-01-01T12:01:03", "2023-01-01T12:01:05"),
      stage(APP_FLOW_ID_1, "TenantLoader", "2023-01-01T12:01:06", "2023-01-01T12:01:10"),
      stage(APP_FLOW_ID_1, "ApplicationDescriptorLoader", "2023-01-01T12:01:11", "2023-01-01T12:01:15"),
      stage(APP_FLOW_ID_1, "ApplicationDependencyValidator", "2023-01-01T12:01:16", "2023-01-01T12:01:20"),
      stage(APP_FLOW_ID_1, "ApplicationDiscoveryValidator", "2023-01-01T12:01:21", "2023-01-01T12:01:25"),
      stage(APP_FLOW_ID_1, "KongRouteCreator", "2023-01-01T12:01:26", "2023-01-01T12:01:30"),
      stage(APP_FLOW_ID_1, "OkapiModuleInstaller", "2023-01-01T12:01:31", "2023-01-01T12:01:35"),
      stage(APP_FLOW_ID_1, "EntitlementEventPublisher", "2023-01-01T12:01:36", "2023-01-01T12:01:40"),
      stage(APP_FLOW_ID_1, "EntitlementFlowFinalizer", "2023-01-01T12:01:41", "2023-01-01T12:01:45")
    );
  }

  private static List<FlowStage> entitlementStages2() {
    return List.of(
      stage(APP_FLOW_ID_2, "EntitlementFlowInitializer", "2023-01-01T12:02:01", "2023-01-01T12:02:05"),
      stage(APP_FLOW_ID_2, "TenantLoader", "2023-01-01T12:02:06", "2023-01-01T12:02:10"),
      stage(APP_FLOW_ID_2, "ApplicationDescriptorLoader", "2023-01-01T12:02:11", "2023-01-01T12:02:15"),
      stage(APP_FLOW_ID_2, "ApplicationDependencyValidator", "2023-01-01T12:02:16", "2023-01-01T12:02:20"),
      stage(APP_FLOW_ID_2, "ApplicationDiscoveryValidator", "2023-01-01T12:02:21", "2023-01-01T12:02:25"),
      stage(APP_FLOW_ID_2, "KongRouteCreator", "2023-01-01T12:02:26", "2023-01-01T12:02:30"),
      stage(APP_FLOW_ID_2, "OkapiModuleInstaller", "2023-01-01T12:02:31", "2023-01-01T12:02:35"),
      stage(APP_FLOW_ID_2, "EntitlementEventPublisher", "2023-01-01T12:02:36", "2023-01-01T12:02:40"),
      stage(APP_FLOW_ID_2, "EntitlementFlowFinalizer", "2023-01-01T12:02:41", "2023-01-01T12:02:45")
    );
  }

  private static ApplicationFlow applicationFlow2() {
    return new ApplicationFlow().id(APP_FLOW_ID_2)
      .applicationId("test-app2-1.0.0")
      .tenantId(TENANT_ID_1)
      .flowId(FLOW_ID_1)
      .type(ENTITLE)
      .status(FINISHED)
      .startedAt(timestampFrom("2023-01-01T12:02:00"))
      .finishedAt(timestampFrom("2023-01-01T12:02:59"));
  }

  private static ApplicationFlow applicationFlow3() {
    return new ApplicationFlow()
      .id(UUID.fromString("64f6b5ab-4894-45cf-b1b9-760c1c6b800b"))
      .applicationId("test-app3-1.0.0")
      .tenantId(TENANT_ID_2)
      .flowId(FLOW_ID_2)
      .type(ENTITLE)
      .status(FINISHED)
      .startedAt(timestampFrom("2023-01-01T13:01:00"))
      .finishedAt(timestampFrom("2023-01-01T13:01:59"));
  }

  private static ApplicationFlow applicationFlow4() {
    return new ApplicationFlow()
      .id(UUID.fromString("4fdbb687-8e80-46b4-8328-a3ede141aa08"))
      .applicationId("test-app4-1.0.0")
      .tenantId(TENANT_ID_2)
      .flowId(FLOW_ID_2)
      .type(ENTITLE)
      .status(FAILED)
      .startedAt(timestampFrom("2023-01-01T13:02:00"))
      .finishedAt(timestampFrom("2023-01-01T13:02:59"));
  }

  private static Date timestampFrom(String value) {
    return Date.from(LocalDateTime.parse(value).atZone(ZoneId.of("UTC")).toInstant());
  }

  private static FlowStage stage(UUID appId, String name, String startedAt, String finishedAt) {
    return new FlowStage()
      .name(name)
      .flowId(appId)
      .status(FINISHED)
      .startedAt(timestampFrom(startedAt))
      .finishedAt(timestampFrom(finishedAt));
  }
}
