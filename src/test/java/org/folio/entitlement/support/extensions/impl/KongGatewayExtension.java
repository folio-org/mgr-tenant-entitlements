package org.folio.entitlement.support.extensions.impl;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.any;
import static com.github.tomakehurst.wiremock.client.WireMock.urlMatching;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;
import static org.folio.entitlement.support.extensions.impl.PostgresContainerExtension.POSTGRES_NETWORK_ALIAS;
import static org.folio.test.extensions.impl.DockerImageRegistry.getKongImageName;

import com.github.tomakehurst.wiremock.client.WireMock;
import java.net.HttpURLConnection;
import java.net.URI;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import org.folio.entitlement.support.extensions.EnableKongGateway;
import org.folio.test.extensions.impl.WireMockExtension;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;

public class KongGatewayExtension implements BeforeAllCallback, AfterAllCallback, BeforeEachCallback {

  public static final String KONG_GATEWAY_URL_PROPERTY = "kong.gateway.url";
  public static final String KONG_URL_PROPERTY = "kong.url";

  private static final String ENV_KONG_READINESS_TIMEOUT = "TESTCONTAINERS_KONG_READINESS_TIMEOUT";
  private static final long DEFAULT_CONTAINER_READINESS_TIMEOUT = 120;
  // folioci/folio-kong runs migrations, starts Kong temporarily for deck sync, stops Kong,
  // then restarts as the final foreground process. This log line appears just before the stop.
  private static final String KONG_INIT_DONE_LOG = ".*Kong initialization finished successfully!.*\n";

  private static final long CONTAINER_READINESS_TIMEOUT;
  private static final GenericContainer<?> CONTAINER;

  static {
    var env = System.getenv();
    CONTAINER_READINESS_TIMEOUT = Long.parseLong(
      env.getOrDefault(ENV_KONG_READINESS_TIMEOUT, String.valueOf(DEFAULT_CONTAINER_READINESS_TIMEOUT)));
    CONTAINER = kongContainer(getKongImageName());
  }

  private static WireMock wireMockClient;

  @Override
  public void beforeAll(ExtensionContext extensionContext) {
    if (!CONTAINER.isRunning()) {
      CONTAINER.start();
      waitForKongFinalReady();
    }

    var enableWiremock = isWireMockEnabled(extensionContext);

    String kongUrl = getUrlForExposedPort(8001);
    if (enableWiremock) {
      kongUrl = prepareAndRunWiremock();
    }

    System.setProperty(KONG_URL_PROPERTY, kongUrl);
    System.setProperty(KONG_GATEWAY_URL_PROPERTY, getUrlForExposedPort(8000));
  }

  @Override
  public void beforeEach(ExtensionContext extensionContext) {
    var enableWiremock = isWireMockEnabled(extensionContext);
    if (enableWiremock) {
      wireMockClient.removeMappings();
      wireMockClient.register(
        any(urlMatching("(/services|/routes).*")).atPriority(1000)
          .willReturn(aResponse().proxiedFrom("http://kong:8001")));
    }
  }

  @Override
  public void afterAll(ExtensionContext extensionContext) {
    System.clearProperty(KONG_URL_PROPERTY);
    System.clearProperty(KONG_GATEWAY_URL_PROPERTY);
  }

  // Waits for the stop+restart cycle that folioci/folio-kong performs after deck sync.
  // CONTAINER.start() returns when the init-done log line fires; Kong then briefly stops before
  // coming back as the foreground process. We poll until we observe unavailable?available.
  private static void waitForKongFinalReady() {
    var wasUnavailable = new AtomicBoolean(false);
    await()
      .pollInterval(200, MILLISECONDS)
      .atMost(30, SECONDS)
      .until(() -> {
        var available = CONTAINER.isRunning() && isKongStatusOk();
        if (!available) {
          wasUnavailable.set(true);
        }
        return available && wasUnavailable.get();
      });
  }

  private static boolean isKongStatusOk() {
    try {
      var url = URI.create(getUrlForExposedPort(8001) + "/status").toURL();
      var connection = (HttpURLConnection) url.openConnection();
      connection.setConnectTimeout(500);
      connection.setReadTimeout(500);
      connection.setRequestMethod("GET");
      var responseCode = connection.getResponseCode();
      connection.disconnect();
      return responseCode == 200;
    } catch (Exception e) {
      return false;
    }
  }

  private static String getUrlForExposedPort(int port) {
    return String.format("http://%s:%s", CONTAINER.getHost(), CONTAINER.getMappedPort(port));
  }

  @SuppressWarnings("resource")
  private static GenericContainer<?> kongContainer(String imageName) {
    return new GenericContainer<>(imageName)
      .withEnv(kongEnvironment())
      .withNetwork(Network.SHARED)
      .withExposedPorts(8000, 8001)
      .withAccessToHost(true)
      .withNetworkAliases("kong")
      .waitingFor(Wait.forLogMessage(KONG_INIT_DONE_LOG, 1)
        .withStartupTimeout(Duration.ofSeconds(CONTAINER_READINESS_TIMEOUT)));
  }

  private static Map<String, String> kongEnvironment() {
    var environment = new LinkedHashMap<String, String>();

    environment.put("KONG_DATABASE", "postgres");
    environment.put("KONG_PG_DATABASE", "kong_it");
    environment.put("KONG_PG_USER", "kong_admin");
    environment.put("KONG_PG_PASSWORD", "kong123");
    environment.put("KONG_PG_PORT", "5432");
    environment.put("KONG_PG_HOST", POSTGRES_NETWORK_ALIAS);
    environment.put("KONG_PROXY_ERROR_LOG", "/dev/stderr");
    environment.put("KONG_ADMIN_ERROR_LOG", "/dev/stderr");
    environment.put("KONG_PROXY_LISTEN", "0.0.0.0:8000");
    environment.put("KONG_ADMIN_LISTEN", "0.0.0.0:8001");
    environment.put("KONG_PLUGINS", "bundled");
    environment.put("KONG_LOG_LEVEL", "info");
    environment.put("KONG_WORKER_STATE_UPDATE_FREQUENCY", "2");

    return environment;
  }

  private static boolean isWireMockEnabled(ExtensionContext extensionContext) {
    return extensionContext.getElement().map(element -> element.getAnnotation(EnableKongGateway.class))
      .map(EnableKongGateway::enableWiremock).orElse(false);
  }

  /**
   * Prepare and start WireMock server.
   *
   * @return returns WireMock URL
   */
  private String prepareAndRunWiremock() {
    try {
      var runContainer = WireMockExtension.class.getDeclaredMethod("runContainer");
      runContainer.setAccessible(true);
      runContainer.invoke(null);
      var getUrlForExposedPort = WireMockExtension.class.getDeclaredMethod("getUrlForExposedPort");
      getUrlForExposedPort.setAccessible(true);
      String wireMockUrl = getUrlForExposedPort.invoke(null).toString();
      wireMockClient = new WireMock(new URI(wireMockUrl).getHost(), new URI(wireMockUrl).getPort());
      return wireMockUrl;
    } catch (Exception e) {
      throw new RuntimeException("Failed to start Wiremock", e);
    }
  }
}
