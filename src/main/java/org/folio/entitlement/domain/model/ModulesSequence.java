package org.folio.entitlement.domain.model;

import java.util.List;
import org.folio.common.domain.model.ModuleDescriptor;

public record ModulesSequence(
  List<List<ModuleDescriptorHolder>> moduleDescriptors,
  List<List<ModuleDescriptor>> deprecatedModuleDescriptors) {}
