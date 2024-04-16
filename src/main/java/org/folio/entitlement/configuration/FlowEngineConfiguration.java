package org.folio.entitlement.configuration;

import static java.lang.Boolean.TRUE;
import static java.lang.Runtime.getRuntime;

import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;
import org.folio.entitlement.service.InheritedClassLoaderThreadFactory;
import org.folio.flow.api.FlowEngine;
import org.folio.flow.utils.StageReportProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.concurrent.DelegatingSecurityContextExecutor;
import org.springframework.security.core.context.SecurityContextHolder;

@Configuration
public class FlowEngineConfiguration {

  /**
   * max #workers - 1.
   */
  private static final int POOL_MAX_CAP = 32767;

  /**
   * Creates a {@link FlowEngine} bean.
   *
   * @return {@link FlowEngine} bean
   */
  @Bean
  public FlowEngine flowEngine(FlowEngineConfigurationProperties configuration) {
    return FlowEngine.builder()
      .name("entitlement-flow-engine")
      .executor(inheritedClassLoaderPool())
      .executionTimeout(configuration.getExecutionTimeout())
      .printFlowResult(TRUE.equals(configuration.getPrintFlowResult()))
      .stageReportProvider(StageReportProvider.builder()
        .template("-> [${stageType}] ${formattedStageId} |> ${statusName}")
        .errorTemplate("-> [${stageType}] ${formattedStageId} |> ${statusName} with [${errorType}]: ${errorMessage}")
        .stageNameFormatter(FlowEngineConfiguration::shortenStageId)
        .build())
      .lastExecutionsStatusCacheSize(configuration.getLastExecutionsStatusCacheSize())
      .build();
  }

  private static Executor inheritedClassLoaderPool() {
    var factory = new InheritedClassLoaderThreadFactory();
    var pool = new ForkJoinPool(Math.min(POOL_MAX_CAP, getRuntime().availableProcessors()), factory, null, false);
    return new DelegatingSecurityContextExecutor(pool, SecurityContextHolder.getContext());
  }

  private static String shortenStageId(String stageId) {
    var stageIdParts = stageId.split("/");
    return stageIdParts[stageIdParts.length - 1];
  }
}
