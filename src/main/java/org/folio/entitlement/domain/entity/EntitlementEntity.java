package org.folio.entitlement.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.io.Serial;
import java.io.Serializable;
import java.util.Set;
import java.util.UUID;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.folio.entitlement.domain.entity.key.EntitlementKey;
import org.folio.entitlement.domain.entity.key.EntitlementModuleEntity;
import org.folio.entitlement.utils.SemverUtils;

@Data
@Entity
@Table(name = "entitlement")
@IdClass(EntitlementKey.class)
public class EntitlementEntity implements Serializable {

  @Serial private static final long serialVersionUID = 1717674444565778201L;

  /**
   * A tenant identifier.
   */
  @Id
  @Column(name = "tenant_id")
  private UUID tenantId;

  /**
   * A tenant identifier.
   */
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

  /**
   * A set of entitled modules.
   */
  @OneToMany(fetch = FetchType.LAZY)
  @ToString.Exclude
  @EqualsAndHashCode.Exclude
  @JoinColumn(name = "application_id")
  @JoinColumn(name = "tenant_id")
  private Set<EntitlementModuleEntity> modules;

  /**
   * Sets application name and version from application id.
   */
  @PrePersist
  private void calculateNameVersion() {
    applicationName = SemverUtils.getName(applicationId);
    applicationVersion = SemverUtils.getVersion(applicationId);
  }
}
