package org.folio.entitlement.configuration.cache;

import static org.assertj.core.api.Assertions.assertThat;

import org.folio.test.types.UnitTest;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.annotation.UserConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.cache.support.NoOpCacheManager;

@UnitTest
class ModuleEntitlementsCacheConfigurationTest {

  private final ApplicationContextRunner runner = new ApplicationContextRunner()
    .withConfiguration(UserConfigurations.of(ModuleEntitlementsCacheConfiguration.class));

  @Test
  void enabledByDefault_createsCaffeineCacheManager() {
    runner.run(ctx -> {
      assertThat(ctx).hasSingleBean(CacheManager.class);
      assertThat(ctx.getBean("moduleEntitlementsCacheManager")).isInstanceOf(CaffeineCacheManager.class);
      assertThat(((CaffeineCacheManager) ctx.getBean("moduleEntitlementsCacheManager")).getCacheNames())
        .contains(ModuleEntitlementsCacheConfiguration.MODULE_ENTITLEMENTS_CACHE);
    });
  }

  @Test
  void disabled_createsNoOpCacheManager() {
    runner.withPropertyValues("application.module-entitlements-cache.enabled=false").run(ctx ->
      assertThat(ctx.getBean("moduleEntitlementsCacheManager")).isInstanceOf(NoOpCacheManager.class));
  }
}
