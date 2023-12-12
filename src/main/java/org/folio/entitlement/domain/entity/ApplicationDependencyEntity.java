package org.folio.entitlement.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serial;
import java.io.Serializable;
import java.util.UUID;
import lombok.Data;
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

  @Id
  @Column(name = "parent_name")
  private String parentName;

  @Id
  @Column(name = "parent_version")
  private String parentVersion;
}
