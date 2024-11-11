package org.folio.entitlement.service.stage;

import lombok.extern.slf4j.Slf4j;
import org.folio.entitlement.domain.model.ModuleStageContext;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class ThreadLocalModuleStageContext {

  private static final ThreadLocal<ModuleStageContext> THREAD_LOCAL_MODULE_STAGE_CONTEXT = new ThreadLocal<>();

  public ModuleStageContext get() {
    return THREAD_LOCAL_MODULE_STAGE_CONTEXT.get();
  }

  public void set(ModuleStageContext moduleStageContext, String stageName) {
    THREAD_LOCAL_MODULE_STAGE_CONTEXT.set(moduleStageContext);
  }

  public void clear() {
    THREAD_LOCAL_MODULE_STAGE_CONTEXT.remove();
  }
}
