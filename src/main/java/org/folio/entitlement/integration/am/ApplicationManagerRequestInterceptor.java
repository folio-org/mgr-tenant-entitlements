package org.folio.entitlement.integration.am;

import static org.folio.common.utils.OkapiHeaders.TOKEN;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.entitlement.integration.token.TokenProvider;

/**
 * Feign request interceptor that replaces user-provided tokens with fresh system tokens
 * from Keycloak for requests to mgr-applications. This prevents token expiration issues
 * during long-running operations.
 */
@Log4j2
@RequiredArgsConstructor
public class ApplicationManagerRequestInterceptor implements RequestInterceptor {

  private final TokenProvider tokenProvider;

  @Override
  public void apply(RequestTemplate template) {
    var headers = template.headers();
    var tokenHeaders = headers.get(TOKEN);
    
    if (tokenHeaders != null && !tokenHeaders.isEmpty()) {
      var userToken = tokenHeaders.iterator().next();
      var freshToken = tokenProvider.getToken(userToken);
      
      template.removeHeader(TOKEN);
      template.header(TOKEN, freshToken);
      
      log.debug("Replaced token for mgr-applications request");
    }
  }
}
