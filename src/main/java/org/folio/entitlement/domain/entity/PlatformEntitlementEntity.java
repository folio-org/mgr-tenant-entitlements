package org.folio.entitlement.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.io.Serial;
import java.io.Serializable;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.folio.common.utils.SemverUtils;

/**
 * A deployment-wide entitlement of a platform-scoped application. The application name is the key, so at most one
 * version of an application is entitled at a time.
 */
@Data
@Entity
@NoArgsConstructor
@Table(name = "platform_entitlement")
public class PlatformEntitlementEntity implements Serializable {

  @Serial private static final long serialVersionUID = 4120938615873240641L;

  /**
   * Application's name, the primary key. Assigned from the application id by {@link #of(String)}.
   */
  @Id
  @Column(name = "application_name", updatable = false)
  private String applicationName;

  /**
   * Full application identifier (name-version) of the entitled version.
   */
  @Column(name = "application_id")
  private String applicationId;

  /**
   * Application's version that is a part of id.
   */
  @Column(name = "application_version")
  private String applicationVersion;

  /**
   * Creates an entity with name and version derived from the application id.
   *
   * @param applicationId - application identifier as name-version
   * @return a new {@link PlatformEntitlementEntity}
   */
  public static PlatformEntitlementEntity of(String applicationId) {
    var entity = new PlatformEntitlementEntity();
    entity.setApplicationId(applicationId);
    entity.setApplicationName(SemverUtils.getName(applicationId));
    entity.setApplicationVersion(SemverUtils.getVersion(applicationId));
    return entity;
  }
}
