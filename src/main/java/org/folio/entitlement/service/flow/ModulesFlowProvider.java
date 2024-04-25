package org.folio.entitlement.service.flow;

import org.folio.flow.api.Flow;
import org.folio.flow.api.StageContext;

public interface ModulesFlowProvider {

  /**
   * Creates modules installation flow.
   *
   * @param context - {@link StageContext} object to process
   * @return {@link Flow} to install/uninstall/upgrade modules in application
   */
  Flow createFlow(StageContext context);

  /**
   * Returns customizable stage name for {@link ModuleFlowFactory}.
   *
   * @return modules flow factory stage name as {@link String}
   */
  default String getName() {
    return this.getClass().getSimpleName();
  }
}
