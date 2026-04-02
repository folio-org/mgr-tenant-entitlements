package org.folio.entitlement.integration.interceptor;

import static org.folio.common.utils.OkapiHeaders.TOKEN;

import java.io.IOException;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.entitlement.integration.token.AdminTokenProvider;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

/**
 * ClientHttpRequestInterceptor that refreshes the authorization token for outgoing requests
 * to ensure a valid token is used, particularly for long-running operations.
 * It utilizes the AdminTokenProvider to obtain a fresh token based on the existing user token.
 */
@Log4j2
@RequiredArgsConstructor
public class TokenRefreshRequestInterceptor implements ClientHttpRequestInterceptor {

  private final AdminTokenProvider adminTokenProvider;

  @Override
  public ClientHttpResponse intercept(HttpRequest request, byte[] body,
    ClientHttpRequestExecution execution) throws IOException {
    var headers = request.getHeaders();
    var userToken = headers.getFirst(TOKEN);

    if (userToken != null) {
      var freshToken = adminTokenProvider.getToken(userToken);
      headers.set(TOKEN, freshToken);
      log.debug("Replaced token for external service request");
    }

    return execution.execute(request, body);
  }
}
