package org.folio.entitlement.integration.am.model;

import java.util.List;
import lombok.Data;
import org.folio.entitlement.domain.model.Artifact;
import org.folio.security.domain.model.descriptor.ModuleDescriptor;
import org.hibernate.boot.Metadata;

@Data
public class ApplicationDescriptor implements Artifact {

  private String id;
  private String name;
  private String version;
  private String description;
  private Metadata metadata;
  private List<Module> modules;
  private List<ModuleDescriptor> moduleDescriptors;
  private List<Module> uiModules;
  private List<ModuleDescriptor> uiModuleDescriptors;
  private String platform;
  private List<Dependency> dependencies;
  private ApplicationDeployment deployment;

  public ApplicationDescriptor id(String id) {
    this.id = id;
    return this;
  }

  public ApplicationDescriptor name(String name) {
    this.name = name;
    return this;
  }

  public ApplicationDescriptor version(String version) {
    this.version = version;
    return this;
  }

  public ApplicationDescriptor description(String description) {
    this.description = description;
    return this;
  }

  public ApplicationDescriptor metadata(Metadata metadata) {
    this.metadata = metadata;
    return this;
  }

  public ApplicationDescriptor modules(List<Module> modules) {
    this.modules = modules;
    return this;
  }

  public ApplicationDescriptor moduleDescriptors(List<ModuleDescriptor> moduleDescriptors) {
    this.moduleDescriptors = moduleDescriptors;
    return this;
  }

  public ApplicationDescriptor uiModules(List<Module> uiModules) {
    this.uiModules = uiModules;
    return this;
  }

  public ApplicationDescriptor uiModuleDescriptors(List<ModuleDescriptor> uiModuleDescriptors) {
    this.uiModuleDescriptors = uiModuleDescriptors;
    return this;
  }

  public ApplicationDescriptor platform(String platform) {
    this.platform = platform;
    return this;
  }

  public ApplicationDescriptor dependencies(List<Dependency> dependencies) {
    this.dependencies = dependencies;
    return this;
  }

  public ApplicationDescriptor deployment(ApplicationDeployment deployment) {
    this.deployment = deployment;
    return this;
  }
}
