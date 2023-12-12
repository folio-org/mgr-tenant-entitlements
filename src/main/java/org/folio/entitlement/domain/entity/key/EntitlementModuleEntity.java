package org.folio.entitlement.domain.entity.key;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serial;
import java.io.Serializable;
import java.util.UUID;
import lombok.Data;
import org.springframework.data.domain.Sort;

@Data
@Entity
@Table(name = "entitlement_module")
@IdClass(EntitlementModuleKey.class)
public class EntitlementModuleEntity implements Serializable {

  public static final Sort SORT_BY_TENANT = Sort.by(Sort.Direction.ASC, "tenantId");

  @Serial private static final long serialVersionUID = 191195764119176160L;

  @Id
  @Column(name = "module_id")
  private String moduleId;
  @Id
  @Column(name = "tenant_id")
  private UUID tenantId;
  @Id
  @Column(name = "application_id")
  private String applicationId;
}
