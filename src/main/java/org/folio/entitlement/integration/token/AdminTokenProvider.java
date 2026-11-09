package org.folio.entitlement.integration.token;

public interface AdminTokenProvider {

  String getToken(String userToken);

  /**
   * Tells whether all callers reach the downstream services under the same identity.
   *
   * <p>When {@code false}, the downstream services authorize every request against the caller's own token,
   * so responses must not be shared between callers (e.g. through a cache).</p>
   *
   * @return {@code true} if the token does not depend on the calling user
   */
  default boolean isSharedIdentity() {
    return true;
  }
}
