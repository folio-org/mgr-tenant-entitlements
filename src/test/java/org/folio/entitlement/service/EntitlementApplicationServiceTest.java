package org.folio.entitlement.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.folio.common.utils.CollectionUtils.mapItems;
import static org.folio.entitlement.support.TestConstants.OKAPI_TOKEN;
import static org.folio.entitlement.support.TestValues.entitlementEntity;
import static org.folio.entitlement.support.TestValues.tenant;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import jakarta.persistence.EntityNotFoundException;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.apache.commons.text.CharacterPredicates;
import org.apache.commons.text.RandomStringGenerator;
import org.folio.common.domain.model.ApplicationDescriptor;
import org.folio.entitlement.configuration.cache.EntitlementCachePolicy;
import org.folio.entitlement.domain.entity.EntitlementEntity;
import org.folio.entitlement.integration.tm.model.Tenant;
import org.folio.entitlement.integration.token.AdminTokenProvider;
import org.folio.entitlement.repository.EntitlementRepository;
import org.folio.entitlement.support.TestValues;
import org.folio.test.types.UnitTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class EntitlementApplicationServiceTest {

  private static final Tenant TEST_TENANT = tenant();
  private static final String TEST_TENANT_NAME = TEST_TENANT.getName();
  private static final UUID TEST_TENANT_ID = TEST_TENANT.getId();
  private static final Tenant RECREATED_TENANT = Tenant.of(UUID.randomUUID(), TEST_TENANT_NAME, null);
  private static final String FRESH_TOKEN = "fresh-token";
  private static final int BATCH_SIZE = 50;

  private static final RandomStringGenerator RANDOM_STRING_GENERATOR = new RandomStringGenerator.Builder()
    .withinRange('0', 'z')
    .filteredBy(CharacterPredicates.LETTERS)
    .build();

  @Mock private TenantIdResolver tenantIdResolver;
  @Mock private EntitledApplicationDescriptorLoader descriptorLoader;
  @Mock private EntitlementCachePolicy cachePolicy;
  @Mock private EntitlementRepository entitlementRepository;
  @Mock private AdminTokenProvider adminTokenProvider;

  @InjectMocks
  private EntitlementApplicationService service;

  @AfterEach
  void tearDown() {
    verifyNoMoreInteractions(tenantIdResolver, entitlementRepository, adminTokenProvider);
  }

  @Test
  void getApplicationDescriptorsByTenantName_positive() {
    int totalRecords = BATCH_SIZE * 2;
    int limit = BATCH_SIZE + 20;
    var appDescriptors = applicationDescriptors(totalRecords);
    var entitlementEntities = entitlementEntities(appDescriptors);

    when(adminTokenProvider.getToken(OKAPI_TOKEN)).thenReturn(FRESH_TOKEN);
    when(tenantIdResolver.resolve(TEST_TENANT_NAME, FRESH_TOKEN)).thenReturn(TEST_TENANT);
    when(entitlementRepository.findByTenantId(TEST_TENANT_ID)).thenReturn(entitlementEntities);
    when(descriptorLoader.load(mapToIds(appDescriptors), FRESH_TOKEN)).thenReturn(appDescriptors);

    var actualAppDescriptors =
      service.getApplicationDescriptorsByTenantName(TEST_TENANT_NAME, OKAPI_TOKEN, 0, limit);

    assertThat(actualAppDescriptors.getTotalRecords()).isEqualTo(totalRecords);
    assertThat(actualAppDescriptors.getApplicationDescriptors())
      .containsExactlyElementsOf(appDescriptors.subList(0, limit));
    verify(adminTokenProvider).getToken(OKAPI_TOKEN);
    verify(entitlementRepository).findByTenantId(TEST_TENANT_ID);
  }

  @Test
  void getApplicationDescriptorsByTenantName_positive_noEntitlementsAndCachePolicyDisabled() {
    when(adminTokenProvider.getToken(OKAPI_TOKEN)).thenReturn(FRESH_TOKEN);
    when(tenantIdResolver.resolve(TEST_TENANT_NAME, FRESH_TOKEN)).thenReturn(TEST_TENANT);
    when(entitlementRepository.findByTenantId(TEST_TENANT_ID)).thenReturn(List.of());
    when(cachePolicy.isEnabled()).thenReturn(false);
    when(descriptorLoader.load(List.of(), FRESH_TOKEN)).thenReturn(List.of());

    var result = service.getApplicationDescriptorsByTenantName(TEST_TENANT_NAME, OKAPI_TOKEN, 0, 10);

    assertThat(result.getTotalRecords()).isZero();
    assertThat(result.getApplicationDescriptors()).isEmpty();
    verify(adminTokenProvider).getToken(OKAPI_TOKEN);
    verify(entitlementRepository).findByTenantId(TEST_TENANT_ID);
  }

  @Test
  void getApplicationDescriptorsByTenantName_positive_noEntitlementsAndTenantIdUnchanged() {
    when(adminTokenProvider.getToken(OKAPI_TOKEN)).thenReturn(FRESH_TOKEN);
    when(tenantIdResolver.resolve(TEST_TENANT_NAME, FRESH_TOKEN)).thenReturn(TEST_TENANT, TEST_TENANT);
    when(entitlementRepository.findByTenantId(TEST_TENANT_ID)).thenReturn(List.of());
    when(cachePolicy.isEnabled()).thenReturn(true);
    when(descriptorLoader.load(List.of(), FRESH_TOKEN)).thenReturn(List.of());

    var result = service.getApplicationDescriptorsByTenantName(TEST_TENANT_NAME, OKAPI_TOKEN, 0, 10);

    assertThat(result.getTotalRecords()).isZero();
    verify(adminTokenProvider).getToken(OKAPI_TOKEN);
    verify(tenantIdResolver).evict(TEST_TENANT_NAME);
    verify(entitlementRepository).findByTenantId(TEST_TENANT_ID);
  }

  @Test
  void getApplicationDescriptorsByTenantName_positive_tenantRecreatedUnderSameName() {
    var appDescriptors = applicationDescriptors(2);
    var recreatedEntitlements = entitlementEntities(appDescriptors, RECREATED_TENANT.getId());

    when(adminTokenProvider.getToken(OKAPI_TOKEN)).thenReturn(FRESH_TOKEN);
    when(tenantIdResolver.resolve(TEST_TENANT_NAME, FRESH_TOKEN)).thenReturn(TEST_TENANT, RECREATED_TENANT);
    when(entitlementRepository.findByTenantId(TEST_TENANT_ID)).thenReturn(List.of());
    when(entitlementRepository.findByTenantId(RECREATED_TENANT.getId())).thenReturn(recreatedEntitlements);
    when(cachePolicy.isEnabled()).thenReturn(true);
    when(descriptorLoader.load(mapToIds(appDescriptors), FRESH_TOKEN)).thenReturn(appDescriptors);

    var result = service.getApplicationDescriptorsByTenantName(TEST_TENANT_NAME, OKAPI_TOKEN, 0, 10);

    assertThat(result.getTotalRecords()).isEqualTo(2);
    assertThat(result.getApplicationDescriptors()).containsExactlyElementsOf(appDescriptors);
    verify(adminTokenProvider).getToken(OKAPI_TOKEN);
    verify(tenantIdResolver).evict(TEST_TENANT_NAME);
    verify(entitlementRepository).findByTenantId(TEST_TENANT_ID);
    verify(entitlementRepository).findByTenantId(RECREATED_TENANT.getId());
  }

  @Test
  void getApplicationDescriptorsByTenantName_negative_tenantNotFoundAfterReResolve() {
    var notFound = new EntityNotFoundException("Tenant is not found by name: " + TEST_TENANT_NAME);

    when(adminTokenProvider.getToken(OKAPI_TOKEN)).thenReturn(FRESH_TOKEN);
    when(tenantIdResolver.resolve(TEST_TENANT_NAME, FRESH_TOKEN)).thenReturn(TEST_TENANT).thenThrow(notFound);
    when(entitlementRepository.findByTenantId(TEST_TENANT_ID)).thenReturn(List.of());
    when(cachePolicy.isEnabled()).thenReturn(true);

    assertThatThrownBy(() -> service.getApplicationDescriptorsByTenantName(TEST_TENANT_NAME, OKAPI_TOKEN, 0, 10))
      .isSameAs(notFound);

    verify(adminTokenProvider).getToken(OKAPI_TOKEN);
    verify(tenantIdResolver).evict(TEST_TENANT_NAME);
    verify(entitlementRepository).findByTenantId(TEST_TENANT_ID);
  }

  private static List<EntitlementEntity> entitlementEntities(List<ApplicationDescriptor> appDescriptors) {
    return entitlementEntities(appDescriptors, TEST_TENANT_ID);
  }

  private static List<EntitlementEntity> entitlementEntities(List<ApplicationDescriptor> appDescriptors,
    UUID tenantId) {
    return appDescriptors.stream()
      .map(descriptor -> entitlementEntity(descriptor.getId(), tenantId))
      .collect(Collectors.toList());
  }

  private static List<String> mapToIds(List<ApplicationDescriptor> appDescriptors) {
    return mapItems(appDescriptors, ApplicationDescriptor::getId);
  }

  private static List<ApplicationDescriptor> applicationDescriptors(int totalRecords) {
    return IntStream.rangeClosed(0, totalRecords - 1)
      .mapToObj(idx -> TestValues.simpleAppDescriptor(RANDOM_STRING_GENERATOR.generate(32)))
      .collect(Collectors.toList());
  }
}
