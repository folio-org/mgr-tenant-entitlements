package org.folio.entitlement.service.stage;

import static org.apache.commons.lang3.StringUtils.uncapitalize;

import lombok.extern.log4j.Log4j2;
import org.folio.entitlement.domain.model.ModuleStageContext;

@Log4j2
public abstract class ModuleDatabaseLoggingStage extends DatabaseLoggingStage<ModuleStageContext> {

  @Override
  public String getStageName(ModuleStageContext context) {
    return context.getModuleId() + "-" + uncapitalize(this.getClass().getSimpleName());
  }
}
