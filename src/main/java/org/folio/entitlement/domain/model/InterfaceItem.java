package org.folio.entitlement.domain.model;

import org.folio.common.domain.model.InterfaceReference;

public record InterfaceItem(InterfaceReference interfaceRef, String appId) {

  public String interfaceRefAsString() {
    return this.interfaceRef().getId() + " " + this.interfaceRef().getVersion();
  }

  @Override
  public String toString() {
    return String.format("[interface = %s, appId = %s]", interfaceRefAsString(), appId);
  }
}
