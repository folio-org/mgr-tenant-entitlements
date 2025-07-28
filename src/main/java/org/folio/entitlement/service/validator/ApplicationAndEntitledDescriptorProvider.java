package org.folio.entitlement.service.validator;

import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.common.domain.model.ApplicationDescriptor;
import org.folio.entitlement.domain.model.CommonStageContext;
import org.folio.entitlement.domain.model.EntitlementRequest;
import org.springframework.stereotype.Component;

@Log4j2
@Component
@RequiredArgsConstructor
public class ApplicationAndEntitledDescriptorProvider implements ApplicationDescriptorProvider {

  @Override
  public List<ApplicationDescriptor> getDescriptors(CommonStageContext context) {
    return List.of();
  }

  @Override
  public List<ApplicationDescriptor> getDescriptors(EntitlementRequest request) {
    return List.of();
  }
}
