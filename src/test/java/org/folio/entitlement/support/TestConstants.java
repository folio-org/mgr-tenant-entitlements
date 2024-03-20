package org.folio.entitlement.support;

import static javax.net.ssl.SSLContext.getInstance;
import static org.folio.integration.kafka.KafkaUtils.getEnvTopicName;
import static org.folio.integration.kafka.KafkaUtils.getTenantTopicName;

import java.net.Socket;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.UUID;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509ExtendedTrustManager;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.SneakyThrows;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class TestConstants {

  public static final UUID TENANT_ID = UUID.fromString("6ad28dae-7c02-4f89-9320-153c55bf1914");
  public static final String TENANT_NAME = "test";
  public static final String TENANT_DESC = "test tenant";

  public static final String APPLICATION_NAME = "test-app";
  public static final String APPLICATION_VERSION = "1.0.0";
  public static final String APPLICATION_ID = APPLICATION_NAME + "-" + APPLICATION_VERSION;

  public static final String OKAPI_TOKEN = "X-Okapi-Token test value";
  public static final String TENANT_PARAMETERS = "loadSample=true";
  public static final Boolean PURGE = false;
  public static final Boolean IGNORE_ERRORS = false;

  public static final UUID FLOW_ID = UUID.randomUUID();
  public static final UUID APPLICATION_FLOW_ID = UUID.randomUUID();
  public static final String FLOW_STAGE_ID = FLOW_ID + "/appi-l0/" + APPLICATION_FLOW_ID;

  public static final SSLContext DUMMY_SSL_CONTEXT = dummySslContext();

  public static String entitlementTopic() {
    return getEnvTopicName("entitlement");
  }

  public static String capabilitiesTenantTopic() {
    return getTenantTopicName("mgr-tenant-entitlements.capability", TENANT_NAME);
  }

  public static String scheduledJobsTenantTopic() {
    return getTenantTopicName("mgr-tenant-entitlements.scheduled-job", TENANT_NAME);
  }

  public static String systemUserTenantTopic() {
    return getTenantTopicName("mgr-tenant-entitlements.system-user", TENANT_NAME);
  }

  @SneakyThrows
  private static SSLContext dummySslContext() {
    var dummyTrustManager = new X509ExtendedTrustManager() {

      @Override
      public void checkClientTrusted(X509Certificate[] chain, String authType, Socket socket) {
      }

      @Override
      public void checkClientTrusted(X509Certificate[] chain, String authType, SSLEngine engine) {
      }

      @Override
      public void checkClientTrusted(X509Certificate[] chain, String authType) {
      }

      @Override
      public void checkServerTrusted(X509Certificate[] chain, String authType, Socket socket) {
      }

      @Override
      public void checkServerTrusted(X509Certificate[] chain, String authType, SSLEngine engine) {
      }

      @Override
      public void checkServerTrusted(X509Certificate[] chain, String authType) {
      }

      @Override
      public X509Certificate[] getAcceptedIssuers() {
        return new X509Certificate[0];
      }
    };

    var sslContext = getInstance("TLS");
    sslContext.init(null, new TrustManager[] {dummyTrustManager}, new SecureRandom());
    return sslContext;
  }
}
