package org.folio.entitlement.integration.token;

public interface AdminTokenProvider {
  String getToken(String userToken);
}
