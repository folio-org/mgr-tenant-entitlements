package org.folio.entitlement.service.validator.configuration;

import lombok.Data;

@Data
public class CollectedInterfaceSettings {

  private static final boolean DEFAULT_EXCLUDE_ENTITLED = true;

  private boolean excludeEntitled = DEFAULT_EXCLUDE_ENTITLED;
}
