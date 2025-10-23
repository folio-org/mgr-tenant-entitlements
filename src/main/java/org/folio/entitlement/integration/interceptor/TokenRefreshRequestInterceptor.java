package org.folio.entitlement.integration.interceptor;

import static org.apache.commons.collections4.CollectionUtils.isNotEmpty;
import static org.folio.common.utils.OkapiHeaders.TOKEN;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.entitlement.integration.token.AdminTokenProvider;

/**
 * Feign RequestInterceptor that refreshes the authorization token for outgoing requests
 * to ensure a valid token is used, particularly for long-running operations.
 * It utilizes the AdminTokenProvider to obtain a fresh token based on the existing user token.
 */
@Log4j2
@RequiredArgsConstructor
public class TokenRefreshRequestInterceptor implements RequestInterceptor {

  private final AdminTokenProvider adminTokenProvider;

  @Override
  public void apply(RequestTemplate template) {
    var headers = template.headers();
    var tokenHeaders = headers.get(TOKEN);
    
    if (isNotEmpty(tokenHeaders)) {
      var userToken = tokenHeaders.iterator().next();
      var freshToken = adminTokenProvider.getToken(userToken);
      
      template.removeHeader(TOKEN);
      template.header(TOKEN, freshToken);
      
      log.debug("Replaced token for external service request");
    }
  }
}
