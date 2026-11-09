package org.folio.entitlement.integration.token;

/**
 * Provides the token used for calls to other mgr-* services.
 */
public interface AdminTokenProvider {

  /**
   * Provides the token for a call made on behalf of the given user token.
   *
   * @param userToken - token of the caller
   * @return token to use for the downstream call
   */
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
