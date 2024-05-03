package org.folio.entitlement.domain.model;

import java.util.Objects;
import org.folio.common.domain.model.ModuleDescriptor;

public record ModuleDescriptorHolder(ModuleDescriptor moduleDescriptor, ModuleDescriptor installedModuleDescriptor) {

  /**
   * Checks if version has been changed for provided module identifier.
   *
   * @return true if version changed, false - otherwise
   */
  public boolean isVersionChanged() {
    return installedModuleDescriptor == null
      || !Objects.equals(moduleDescriptor.getId(), installedModuleDescriptor.getId());
  }
}
