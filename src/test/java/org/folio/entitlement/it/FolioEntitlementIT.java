package org.folio.entitlement.it;

import org.folio.test.types.IntegrationTest;

@IntegrationTest
class FolioEntitlementIT extends AbstractFolioEntitlementIT {

  @Override
  protected String getUserAccessToken() {
    return null;
  }
}
