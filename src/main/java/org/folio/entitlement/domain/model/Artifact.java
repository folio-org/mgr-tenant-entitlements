package org.folio.entitlement.domain.model;

public interface Artifact extends WithNameVersion {

  /**
   * Creates service id from artifact name and version.
   *
   * @return created artifact id as {@link String} object
   */
  default String getId() {
    var name = getName();
    var version = getVersion();
    return name + "-" + version;
  }
}
