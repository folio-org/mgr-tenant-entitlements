package org.folio.entitlement.utils;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.any;
import static com.github.tomakehurst.wiremock.client.WireMock.delete;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.put;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.client.MappingBuilder;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.matching.UrlPattern;
import lombok.experimental.UtilityClass;

@UtilityClass
public class WireMockUtil {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  public static void stubGet(WireMock wireMockClient, int priority, UrlPattern urlPattern, Object response)
    throws Exception {
    stubGet(wireMockClient, priority, urlPattern, response, 200);
  }

  public static void stubGet(WireMock wireMockClient, int priority, UrlPattern urlPattern, Object response,
    int httpStatus) throws Exception {
    mock(wireMockClient, get(urlPattern), priority, response, httpStatus);
  }

  public static void stubPost(WireMock wireMockClient, int priority, UrlPattern urlPattern, Object response,
    int httpStatus) throws Exception {
    mock(wireMockClient, post(urlPattern), priority, response, httpStatus);
  }

  public static void stubPut(WireMock wireMockClient, int priority, UrlPattern urlPattern, Object response,
    int httpStatus) throws Exception {
    mock(wireMockClient, put(urlPattern), priority, response, httpStatus);
  }

  public static void stubDelete(WireMock wireMockClient, int priority, UrlPattern urlPattern, Object response,
    int httpStatus) throws Exception {
    mock(wireMockClient, delete(urlPattern), priority, response, httpStatus);
  }

  public static void stubAnyHttpMethod(WireMock wireMockClient, int priority, UrlPattern urlPattern, Object response,
    int httpStatus) throws Exception {
    mock(wireMockClient, any(urlPattern), priority, response, httpStatus);
  }

  private static void mock(WireMock wireMockClient, MappingBuilder mappingBuilder, int priority, Object response,
    int httpStatus) throws Exception {
    var responseDef = aResponse().withStatus(httpStatus);
    if (response != null) {
      responseDef =
        responseDef.withBody(OBJECT_MAPPER.writeValueAsBytes(response)).withHeader("Content-Type", "application/json");
    }
    wireMockClient.register(mappingBuilder.atPriority(priority).willReturn(responseDef));
  }
}
