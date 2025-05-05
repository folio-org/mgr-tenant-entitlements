package org.folio.entitlement.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.io.Serial;
import java.io.Serializable;
import java.util.UUID;
import lombok.Data;
import org.folio.common.utils.SemverUtils;
import org.folio.entitlement.domain.entity.key.ApplicationDependencyKey;

@Data
@Entity
@Table(name = "application_dependency")
@IdClass(ApplicationDependencyKey.class)
public class ApplicationDependencyEntity implements Serializable {

  @Serial private static final long serialVersionUID = 3699053013284229407L;

  @Id
  @Column(name = "tenant_id")
  private UUID tenantId;

  @Id
  @Column(name = "application_id")
  private String applicationId;

  /**
   * Application's name that is a part of id.
   */
  @Column(name = "application_name", updatable = false)
  private String applicationName;

  /**
   * Application's version that is a part of id.
   */
  @Column(name = "application_version", updatable = false)
  private String applicationVersion;

  @Id
  @Column(name = "parent_name")
  private String parentName;

  @Id
  @Column(name = "parent_version")
  private String parentVersion;

  @Column(name = "optional")
  private Boolean optional;

  /**
   * Sets application name and version from application id.
   */
  @PrePersist
  private void calculateNameVersion() {
    applicationName = SemverUtils.getName(applicationId);
    applicationVersion = SemverUtils.getVersion(applicationId);
  }
}
