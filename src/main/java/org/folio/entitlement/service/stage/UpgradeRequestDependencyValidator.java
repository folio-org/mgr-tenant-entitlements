package org.folio.entitlement.service.stage;

import lombok.RequiredArgsConstructor;
import org.folio.entitlement.integration.folio.ApplicationStageContext;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class UpgradeRequestDependencyValidator extends DatabaseLoggingStage<ApplicationStageContext> {

  @Override
  public void execute(ApplicationStageContext context) {
    // to be implemented
  }
}
