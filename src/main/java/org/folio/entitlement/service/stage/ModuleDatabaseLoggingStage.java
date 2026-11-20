package org.folio.entitlement.service.stage;

import static org.apache.commons.lang3.StringUtils.uncapitalize;

import lombok.extern.log4j.Log4j2;
import org.folio.entitlement.domain.model.ModuleStageContext;

@Log4j2
public abstract class ModuleDatabaseLoggingStage extends DatabaseLoggingStage<ModuleStageContext> {

  static String stageNameSuffix(Class<?> type) {
    return "-" + uncapitalize(type.getSimpleName());
  }

  @Override
  public String getStageName(ModuleStageContext context) {
    return context.getModuleId() + stageNameSuffix(this.getClass());
  }
}
