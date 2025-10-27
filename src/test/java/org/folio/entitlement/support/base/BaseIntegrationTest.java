package org.folio.entitlement.support.base;

import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.entitlement.support.TestConstants.OKAPI_TOKEN;
import static org.folio.entitlement.support.TestUtils.asJsonString;
import static org.folio.entitlement.support.TestUtils.parseResponse;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_CLASS;
import static org.springframework.test.context.TestExecutionListeners.MergeMode.MERGE_WITH_DEFAULTS;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.github.tomakehurst.wiremock.client.WireMock;
import java.net.URI;
import java.util.List;
import java.util.Map;
import lombok.extern.log4j.Log4j2;
import org.folio.entitlement.domain.dto.EntitlementRequestBody;
import org.folio.entitlement.domain.dto.Entitlements;
import org.folio.entitlement.domain.dto.ExtendedEntitlements;
import org.folio.entitlement.exception.RequestValidationException;
import org.folio.entitlement.support.TestUtils;
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
import org.opentest4j.AssertionFailedError;
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

  public static final String ROUTER_PATH_PREFIX_SYSTEM_PROPERTY_KEY = "it.router.path-prefix";
  public static final String SYSTEM_ACCESS_TOKEN_SYSTEM_PROPERTY_KEY = "it.system.access-token";
  public static final String FOLIO_MODULE1_ID = "folio-module1-1.0.0";
  public static final String FOLIO_MODULE2_ID = "folio-module2-2.0.0";
  public static final String FOLIO_MODULE2_V2_ID = "folio-module2-2.1.0";
  public static final String FOLIO_MODULE3_ID = "folio-module3-3.0.0";
  public static final String FOLIO_MODULE4_ID = "folio-module4-4.0.0";

  protected static FakeKafkaConsumer fakeKafkaConsumer;
  protected static WireMockAdminClient wmAdminClient;

  static {
    TestUtils.disableSslVerification();
  }

  @BeforeAll
  static void setUp(@Autowired FakeKafkaConsumer consumer) {
    fakeKafkaConsumer = consumer;
  }

  protected static MvcResult entitleApplications(EntitlementRequestBody entitlementRequest,
    Map<String, String> queryParams, ExtendedEntitlements expectedEntitlements) throws Exception {

    var request = post(updatePathWithPrefix("/entitlements"))
      .contentType(APPLICATION_JSON)
      .header(TOKEN, getSystemAccessToken())
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
    var request = put(updatePathWithPrefix("/entitlements"))
      .contentType(APPLICATION_JSON)
      .header(TOKEN, getSystemAccessToken())
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
    var request = delete(updatePathWithPrefix("/entitlements"))
      .contentType(APPLICATION_JSON)
      .header(TOKEN, getSystemAccessToken())
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
    var mvcResult = mockMvc.perform(get(updatePathWithPrefix("/entitlements"))
        .contentType(APPLICATION_JSON)
        .header(TOKEN, getSystemAccessToken())
        .queryParam("query", cqlQuery))
      .andExpect(status().isOk())
      .andReturn();

    var result = parseResponse(mvcResult, Entitlements.class);
    assertThat(result).isEqualTo(expected);
  }

  protected static void assertEntitlementsWithModules(String cqlQuery, Entitlements expected) throws Exception {
    mockMvc.perform(get(updatePathWithPrefix("/entitlements"))
        .contentType(APPLICATION_JSON)
        .header(TOKEN, getSystemAccessToken())
        .queryParam("includeModules", "true")
        .queryParam("query", cqlQuery))
      .andExpect(status().isOk())
      .andExpect(content().json(asJsonString(expected), true));
  }

  protected static void assertModuleEntitlements(String moduleId, Entitlements expected) throws Exception {
    mockMvc.perform(get(updatePathWithPrefix("/entitlements/modules/{moduleId}"), moduleId)
        .contentType(APPLICATION_JSON)
        .header(TOKEN, getSystemAccessToken())
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

  public static String updatePathWithPrefix(String path) {
    var pathPrefix = System.getProperty(ROUTER_PATH_PREFIX_SYSTEM_PROPERTY_KEY);
    return pathPrefix == null ? path : "/" + pathPrefix + path;
  }

  public static String getSystemAccessToken() {
    var accessToken = System.getProperty(SYSTEM_ACCESS_TOKEN_SYSTEM_PROPERTY_KEY);
    return accessToken != null ? accessToken : OKAPI_TOKEN;
  }

  protected WireMock getWireMockClient() throws Exception {
    return new WireMock(new URI(wmAdminClient.getWireMockUrl()).getHost(), wmAdminClient.getWireMockPort());
  }
}
