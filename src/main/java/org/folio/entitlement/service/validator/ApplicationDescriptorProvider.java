package org.folio.entitlement.service.validator;

import java.util.List;
import org.folio.common.domain.model.ApplicationDescriptor;
import org.folio.entitlement.domain.model.CommonStageContext;
import org.folio.entitlement.domain.model.EntitlementRequest;

public interface ApplicationDescriptorProvider {

  List<ApplicationDescriptor> getDescriptors(CommonStageContext context);

  List<ApplicationDescriptor> getDescriptors(EntitlementRequest request);
}
