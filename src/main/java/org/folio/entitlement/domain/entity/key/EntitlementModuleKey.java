package org.folio.entitlement.domain.entity.key;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serial;
import java.io.Serializable;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.folio.entitlement.integration.folio.model.ModuleRequest;

@Data
@Embeddable
@NoArgsConstructor
@AllArgsConstructor(staticName = "of")
public class EntitlementModuleKey implements Serializable {

  @Serial private static final long serialVersionUID = -258846202138536041L;

  @Column(name = "module_id")
  private String moduleId;
  @Column(name = "tenant_id")
  private UUID tenantId;
  @Column(name = "application_id")
  private String applicationId;

  public static EntitlementModuleKey from(ModuleRequest request) {
    return new EntitlementModuleKey(request.getModuleId(), request.getTenantId(), request.getApplicationId());
  }
}
