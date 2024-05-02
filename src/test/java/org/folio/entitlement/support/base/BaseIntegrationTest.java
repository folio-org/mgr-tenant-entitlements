package org.folio.entitlement.support.base;

import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.entitlement.support.TestUtils.asJsonString;
import static org.folio.entitlement.support.TestUtils.parseResponse;
import static org.folio.test.TestConstants.OKAPI_AUTH_TOKEN;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_CLASS;
import static org.springframework.test.context.TestExecutionListeners.MergeMode.MERGE_WITH_DEFAULTS;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;
import junit.framework.AssertionFailedError;
import lombok.extern.log4j.Log4j2;
import org.folio.entitlement.domain.dto.EntitlementRequestBody;
import org.folio.entitlement.domain.dto.Entitlements;
import org.folio.entitlement.domain.dto.ExtendedEntitlements;
import org.folio.entitlement.exception.RequestValidationException;
import org.folio.entitlement.support.extensions.EnableKongGateway;
import org.folio.entitlement.support.extensions.EnablePostgres;
import org.folio.test.FakeKafkaConsumer;
import org.folio.test.base.BaseBackendIntegrationTest;
import org.folio.test.extensions.EnableKafka;
import org.folio.test.extensions.EnableWireMock;
import org.folio.test.extensions.impl.KafkaTestExecutionListener;
import org.folio.test.extensions.impl.WireMockAdminClient;
import org.folio.test.extensions.impl.WireMockExecutionListener;
import org.junit.jupiter.api.BeforeAll;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultHandler;
import org.springframework.test.web.servlet.ResultMatcher;

/**
 * Base integration test class with required extension and helper methods.
 *
 * <p>Order of extensions is important</p>
 *
 * @see org.junit.jupiter.api.extension.ExtendWith
 */
@Log4j2
@EnableKafka
@EnableWireMock
@EnablePostgres
@SpringBootTest
@ActiveProfiles("it")
@EnableKongGateway
@AutoConfigureMockMvc
@DirtiesContext(classMode = AFTER_CLASS)
@TestExecutionListeners(mergeMode = MERGE_WITH_DEFAULTS, value = {
  WireMockExecutionListener.class,
  KafkaTestExecutionListener.class
})
public abstract class BaseIntegrationTest extends BaseBackendIntegrationTest {

  protected static FakeKafkaConsumer fakeKafkaConsumer;
  protected static WireMockAdminClient wmAdminClient;

  @BeforeAll
  static void setUp(@Autowired FakeKafkaConsumer consumer) {
    fakeKafkaConsumer = consumer;
  }

  protected static MvcResult entitleApplications(EntitlementRequestBody entitlementRequest,
    Map<String, String> queryParams, ExtendedEntitlements expectedEntitlements) throws Exception {
    var request = post("/entitlements")
      .contentType(APPLICATION_JSON)
      .header(TOKEN, OKAPI_AUTH_TOKEN)
      .content(asJsonString(entitlementRequest));
    queryParams.forEach(request::queryParam);

    var mvcResult = mockMvc.perform(request)
      .andExpect(status().isCreated())
      .andExpect(content().contentType(APPLICATION_JSON))
      .andExpect(content().json(asJsonString(expectedEntitlements), false))
      .andReturn();

    verifyExtendedEntitlements(mvcResult, expectedEntitlements);

    return mvcResult;
  }

  protected static MvcResult upgradeApplications(EntitlementRequestBody entitlementRequest,
    Map<String, String> queryParams, ExtendedEntitlements expectedEntitlements) throws Exception {
    var request = put("/entitlements")
      .contentType(APPLICATION_JSON)
      .header(TOKEN, OKAPI_AUTH_TOKEN)
      .content(asJsonString(entitlementRequest));
    queryParams.forEach(request::queryParam);

    var mvcResult = mockMvc.perform(request)
      .andExpect(status().isOk())
      .andExpect(content().contentType(APPLICATION_JSON))
      .andExpect(content().json(asJsonString(expectedEntitlements), false))
      .andReturn();

    verifyExtendedEntitlements(mvcResult, expectedEntitlements);

    return mvcResult;
  }

  protected static void revokeEntitlements(EntitlementRequestBody entitlementRequest,
    Map<String, String> queryParams, ExtendedEntitlements expectedEntitlements) throws Exception {
    var request = delete("/entitlements")
      .contentType(APPLICATION_JSON)
      .header(TOKEN, OKAPI_AUTH_TOKEN)
      .content(asJsonString(entitlementRequest));
    queryParams.forEach(request::queryParam);

    var mvcResult = mockMvc.perform(request)
      .andExpect(status().isOk())
      .andExpect(content().contentType(APPLICATION_JSON))
      .andExpect(content().json(asJsonString(expectedEntitlements), false))
      .andReturn();

    verifyExtendedEntitlements(mvcResult, expectedEntitlements);
  }

  protected static void getEntitlementsByQuery(String cqlQuery, Entitlements expected) throws Exception {
    var mvcResult = mockMvc.perform(get("/entitlements")
        .contentType(APPLICATION_JSON)
        .header(TOKEN, OKAPI_AUTH_TOKEN)
        .queryParam("query", cqlQuery))
      .andExpect(status().isOk())
      .andReturn();

    var result = parseResponse(mvcResult, Entitlements.class);
    assertThat(result).isEqualTo(expected);
  }

  protected static void assertEntitlementsWithModules(String cqlQuery, Entitlements expected) throws Exception {
    mockMvc.perform(get("/entitlements")
        .contentType(APPLICATION_JSON)
        .header(TOKEN, OKAPI_AUTH_TOKEN)
        .queryParam("includeModules", "true")
        .queryParam("query", cqlQuery))
      .andExpect(status().isOk())
      .andExpect(content().json(asJsonString(expected), true));
  }

  protected static void assertModuleEntitlements(String moduleId, Entitlements expected) throws Exception {
    mockMvc.perform(get("/entitlements/modules/{moduleId}", moduleId)
        .contentType(APPLICATION_JSON)
        .header(TOKEN, OKAPI_AUTH_TOKEN)
        .queryParam("includeModules", "true"))
      .andExpect(status().isOk())
      .andExpect(content().json(asJsonString(expected), true));
  }

  protected static void checkExistingBean(ApplicationContext appContext, Class<?> beanClass) {
    try {
      appContext.getBean(beanClass);
    } catch (NoSuchBeanDefinitionException e) {
      throw new AssertionFailedError("No Bean of type " + beanClass.getName() + " found");
    }
  }

  protected static void checkExistingBeans(ApplicationContext appContext, List<Class<?>> beanClasses) {
    beanClasses.forEach(beanClass -> checkExistingBean(appContext, beanClass));
  }

  protected static void checkMissingBean(ApplicationContext appContext, Class<?> beanClass) {
    try {
      appContext.getBean(beanClass);
      throw new AssertionFailedError("Bean of type " + beanClass.getName() + " found");
    } catch (NoSuchBeanDefinitionException e) {
      // nothing to do, bean should be missing
    }
  }

  protected static void checkMissingBeans(ApplicationContext appContext, List<Class<?>> beanClasses) {
    beanClasses.forEach(beanClass -> checkMissingBean(appContext, beanClass));
  }

  protected static ResultHandler logResponseBody() {
    return result -> log.info("[Res-Body] {}", result.getResponse().getContentAsString());
  }

  protected static ResultMatcher[] requestValidationErr(String errMsg, String fieldName, Object fieldValue) {
    return validationErr(RequestValidationException.class.getSimpleName(), errMsg, fieldName, fieldValue);
  }

  private static void verifyExtendedEntitlements(MvcResult mvcResult, ExtendedEntitlements expectedEntitlements) {
    var responseEntitlements = parseResponse(mvcResult, ExtendedEntitlements.class);
    var expectedFlowId = expectedEntitlements.getFlowId();
    if (expectedFlowId != null) {
      assertThat(responseEntitlements.getFlowId()).isEqualTo(expectedFlowId);
    } else {
      assertThat(responseEntitlements.getFlowId()).isNotNull();
    }
  }

  public static String exactRouteExpression(String path, String method) {
    return "(http.path == \"" + path + "\""
      + " && http.method == \"" + method + "\""
      + " && http.headers.x_okapi_tenant == \"test\")";
  }

  @SuppressWarnings("SameParameterValue")
  public static String regexRouteExpression(String expression, String method) {
    return "(http.path ~ \"" + expression + "\""
      + " && http.method == \"" + method + "\""
      + " && http.headers.x_okapi_tenant == \"test\")";
  }
}
