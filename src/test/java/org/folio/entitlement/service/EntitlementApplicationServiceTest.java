package org.folio.entitlement.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.common.utils.CollectionUtils.mapItems;
import static org.folio.entitlement.support.TestConstants.OKAPI_TOKEN;
import static org.folio.entitlement.support.TestValues.entitlementEntity;
import static org.folio.entitlement.support.TestValues.tenant;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.apache.commons.text.CharacterPredicates;
import org.apache.commons.text.RandomStringGenerator;
import org.folio.common.domain.model.ApplicationDescriptor;
import org.folio.entitlement.domain.entity.EntitlementEntity;
import org.folio.entitlement.integration.keycloak.KeycloakCacheableService;
import org.folio.entitlement.integration.tm.TenantManagerService;
import org.folio.entitlement.integration.tm.model.Tenant;
import org.folio.entitlement.repository.EntitlementRepository;
import org.folio.entitlement.support.TestValues;
import org.folio.test.types.UnitTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.keycloak.representations.AccessTokenResponse;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@UnitTest
@ExtendWith(MockitoExtension.class)
class EntitlementApplicationServiceTest {

  private static final Tenant TEST_TENANT = tenant();
  private static final String TEST_TENANT_NAME = TEST_TENANT.getName();
  private static final UUID TEST_TENANT_ID = TEST_TENANT.getId();
  private static final String KEYCLOAK_TOKEN = "kc-token";
  private static final int BATCH_SIZE = 50;

  private static final RandomStringGenerator RANDOM_STRING_GENERATOR = new RandomStringGenerator.Builder()
    .withinRange('0', 'z')
    .filteredBy(CharacterPredicates.LETTERS)
    .build();

  @Mock private ApplicationManagerService applicationManagerService;
  @Mock private TenantManagerService tenantManagerService;
  @Mock private EntitlementRepository entitlementRepository;
  @Mock private KeycloakCacheableService keycloakCacheableService;

  private EntitlementApplicationService service;

  @BeforeEach
  void setup() {
    service = new EntitlementApplicationService(applicationManagerService, tenantManagerService, entitlementRepository);
    service.setKeycloakCacheableService(keycloakCacheableService);
    ReflectionTestUtils.setField(service, "keycloakEnabled", true);
  }

  @Test
  void getApplicationDescriptorsByTenantName_positive() {
    var keycloakAccessToken = new AccessTokenResponse();
    keycloakAccessToken.setToken(KEYCLOAK_TOKEN);

    int totalRecords = BATCH_SIZE * 2;
    int limit = BATCH_SIZE + 20;
    var appDescriptors = applicationDescriptors(totalRecords);
    var entitlementEntities = entitlementEntities(appDescriptors);

    when(tenantManagerService.findTenantByName(anyString(), anyString())).thenReturn(TEST_TENANT);
    when(entitlementRepository.findByTenantId(TEST_TENANT_ID)).thenReturn(entitlementEntities);
    when(keycloakCacheableService.getAccessToken(OKAPI_TOKEN)).thenReturn(keycloakAccessToken);
    when(applicationManagerService.getApplicationDescriptors(anyList(), anyString())).thenReturn(appDescriptors);

    var actualAppDescriptors =
      service.getApplicationDescriptorsByTenantName(TEST_TENANT_NAME, OKAPI_TOKEN, 0, limit);

    assertThat(actualAppDescriptors.getTotalRecords()).isEqualTo(totalRecords);
    assertThat(actualAppDescriptors.getApplicationDescriptors())
      .containsExactlyElementsOf(appDescriptors.subList(0, limit));

    verify(tenantManagerService).findTenantByName(TEST_TENANT_NAME, KEYCLOAK_TOKEN);
    verify(entitlementRepository).findByTenantId(TEST_TENANT_ID);
    verify(applicationManagerService)
      .getApplicationDescriptors(mapToIds(appDescriptors), KEYCLOAK_TOKEN);
    verifyNoMoreInteractions(applicationManagerService);
  }

  private static List<EntitlementEntity> entitlementEntities(List<ApplicationDescriptor> appDescriptors) {
    return appDescriptors.stream()
      .map(descriptor -> entitlementEntity(descriptor.getId(), TEST_TENANT_ID))
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
