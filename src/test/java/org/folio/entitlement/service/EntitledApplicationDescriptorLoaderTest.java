package org.folio.entitlement.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.folio.entitlement.configuration.cache.CacheConfiguration.APPLICATION_DESCRIPTOR;
import static org.folio.entitlement.support.TestConstants.OKAPI_TOKEN;
import static org.folio.entitlement.support.TestValues.simpleAppDescriptor;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import org.folio.common.domain.model.ApplicationDescriptor;
import org.folio.entitlement.configuration.cache.EntitlementCachePolicy;
import org.folio.test.types.UnitTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCache;

/**
 * Verifies per-application descriptor caching, including the case when the cache is bypassed.
 */
@UnitTest
@ExtendWith(MockitoExtension.class)
class EntitledApplicationDescriptorLoaderTest {

  private static final ApplicationDescriptor APP_A = simpleAppDescriptor("app-a-1.0.0");
  private static final ApplicationDescriptor APP_B = simpleAppDescriptor("app-b-1.0.0");
  private static final ApplicationDescriptor APP_C = simpleAppDescriptor("app-c-1.0.0");

  @Mock private ApplicationManagerService applicationManagerService;
  @Mock private EntitlementCachePolicy cachePolicy;
  @Mock private CacheManager cacheManager;

  private ConcurrentMapCache cache;
  private EntitledApplicationDescriptorLoader loader;

  @BeforeEach
  void setUp() {
    cache = new ConcurrentMapCache(APPLICATION_DESCRIPTOR);
    loader = new EntitledApplicationDescriptorLoader(applicationManagerService, cachePolicy, cacheManager);
  }

  @Test
  void load_positive_allDescriptorsMissing() {
    enableCache();
    when(applicationManagerService.getApplicationDescriptors(List.of(APP_A.getId(), APP_B.getId()), OKAPI_TOKEN))
      .thenReturn(List.of(APP_A, APP_B));

    var result = loader.load(List.of(APP_A.getId(), APP_B.getId()), OKAPI_TOKEN);

    assertThat(result).containsExactly(APP_A, APP_B);
    assertThat(cache.get(APP_A.getId(), ApplicationDescriptor.class)).isSameAs(APP_A);
    assertThat(cache.get(APP_B.getId(), ApplicationDescriptor.class)).isSameAs(APP_B);
  }

  @Test
  void load_positive_partiallyCached_loadsOnlyMissingDescriptors() {
    enableCache();
    cache.put(APP_A.getId(), APP_A);
    when(applicationManagerService.getApplicationDescriptors(List.of(APP_B.getId(), APP_C.getId()), OKAPI_TOKEN))
      .thenReturn(List.of(APP_B, APP_C));

    var result = loader.load(List.of(APP_A.getId(), APP_B.getId(), APP_C.getId()), OKAPI_TOKEN);

    assertThat(result).containsExactly(APP_A, APP_B, APP_C);
    assertThat(cache.get(APP_C.getId(), ApplicationDescriptor.class)).isSameAs(APP_C);
  }

  @Test
  void load_positive_allCached_noRemoteCall() {
    enableCache();
    cache.put(APP_A.getId(), APP_A);
    cache.put(APP_B.getId(), APP_B);

    var result = loader.load(List.of(APP_A.getId(), APP_B.getId()), OKAPI_TOKEN);

    assertThat(result).containsExactly(APP_A, APP_B);
    verifyNoInteractions(applicationManagerService);
  }

  @Test
  void load_positive_noApplicationIds_emptyResultWithoutRemoteCall() {
    enableCache();

    var result = loader.load(List.of(), OKAPI_TOKEN);

    assertThat(result).isEmpty();
    verifyNoInteractions(applicationManagerService);
  }

  @Test
  void load_positive_unsortedAndDuplicatedIds_resultOrderedById() {
    enableCache();
    when(applicationManagerService.getApplicationDescriptors(List.of(APP_A.getId(), APP_B.getId()), OKAPI_TOKEN))
      .thenReturn(List.of(APP_B, APP_A));

    var result = loader.load(List.of(APP_B.getId(), APP_A.getId(), APP_B.getId()), OKAPI_TOKEN);

    assertThat(result).containsExactly(APP_A, APP_B);
  }

  @Test
  void load_positive_cachePolicyDisabled_delegatesWithoutUsingCache() {
    var applicationIds = List.of(APP_B.getId(), APP_A.getId());
    when(cachePolicy.isEnabled()).thenReturn(false);
    when(applicationManagerService.getApplicationDescriptors(applicationIds, OKAPI_TOKEN))
      .thenReturn(List.of(APP_B, APP_A));

    var result = loader.load(applicationIds, OKAPI_TOKEN);

    assertThat(result).containsExactly(APP_B, APP_A);
    assertThat(cache.get(APP_A.getId())).isNull();
    verifyNoInteractions(cacheManager);
  }

  @Test
  void load_negative_remoteCallFails_nothingIsCached() {
    enableCache();
    var failure = new IllegalStateException("mgr-applications is unavailable");
    when(applicationManagerService.getApplicationDescriptors(List.of(APP_A.getId()), OKAPI_TOKEN))
      .thenThrow(failure);

    assertThatThrownBy(() -> loader.load(List.of(APP_A.getId()), OKAPI_TOKEN)).isSameAs(failure);

    assertThat(cache.get(APP_A.getId())).isNull();
  }

  private void enableCache() {
    when(cachePolicy.isEnabled()).thenReturn(true);
    when(cacheManager.getCache(APPLICATION_DESCRIPTOR)).thenReturn(cache);
  }
}
