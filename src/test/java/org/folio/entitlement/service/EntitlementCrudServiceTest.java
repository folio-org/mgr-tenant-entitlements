package org.folio.entitlement.service;

import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.entitlement.domain.model.ResultList.asSinglePage;
import static org.folio.entitlement.support.TestConstants.APPLICATION_ID;
import static org.folio.entitlement.support.TestConstants.TENANT_ID;
import static org.folio.entitlement.support.TestValues.entitlement;
import static org.folio.entitlement.support.TestValues.entitlementEntity;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import org.folio.common.domain.model.OffsetRequest;
import org.folio.entitlement.domain.entity.key.EntitlementKey;
import org.folio.entitlement.domain.model.EntitlementRequest;
import org.folio.entitlement.mapper.ApplicationDependencyMapper;
import org.folio.entitlement.mapper.EntitlementMapper;
import org.folio.entitlement.repository.EntitlementRepository;
import org.folio.test.types.UnitTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;

@UnitTest
@ExtendWith(MockitoExtension.class)
class EntitlementCrudServiceTest {

  @InjectMocks private EntitlementCrudService service;
  @Mock private EntitlementMapper entitlementMapper;
  @Mock private EntitlementRepository entitlementRepository;
  @Mock private ApplicationDependencyMapper applicationDependencyMapper;

  @AfterEach
  void tearDown() {
    verifyNoMoreInteractions(entitlementMapper, entitlementRepository, applicationDependencyMapper);
  }

  @Test
  void findByQuery_positive() {
    var offset = 0;
    var limit = 10;
    var cqlQuery = "cql.allRecords=1";
    var offsetRequest = OffsetRequest.of(offset, limit);
    var entity = entitlementEntity();

    when(entitlementRepository.findByCql(cqlQuery, offsetRequest)).thenReturn(new PageImpl<>(singletonList(entity)));
    when(entitlementMapper.map(entity)).thenReturn(entitlement(TENANT_ID, APPLICATION_ID));

    var actual = service.findByQuery(cqlQuery, false, limit, offset);

    assertThat(actual).isEqualTo(asSinglePage(entitlement(TENANT_ID, APPLICATION_ID)));
  }

  @Test
  void findByQuery_positive_includeModules() {
    var offset = 0;
    var limit = 10;
    var cqlQuery = "cql.allRecords=1";
    var offsetRequest = OffsetRequest.of(offset, limit);
    var entity = entitlementEntity();

    when(entitlementRepository.findByCql(cqlQuery, offsetRequest)).thenReturn(new PageImpl<>(singletonList(entity)));
    when(entitlementMapper.mapWithModules(entity)).thenReturn(entitlement(TENANT_ID, APPLICATION_ID));

    var actual = service.findByQuery(cqlQuery, true, limit, offset);

    assertThat(actual).isEqualTo(asSinglePage(entitlement(TENANT_ID, APPLICATION_ID)));
  }

  @Test
  void save_positive() {
    var expectedEntity = entitlementEntity();
    var entitlement = entitlement();

    when(entitlementMapper.map(entitlement)).thenReturn(expectedEntity);
    when(entitlementMapper.map(expectedEntity)).thenReturn(entitlement);
    when(entitlementRepository.save(expectedEntity)).thenReturn(expectedEntity);

    var actual = service.save(entitlement);

    assertThat(actual).isEqualTo(entitlement);
  }

  @Test
  void delete_positive() {
    var entitlementKey = EntitlementKey.of(TENANT_ID, APPLICATION_ID);
    var entitlementEntity = entitlementEntity();
    when(entitlementRepository.getReferenceById(entitlementKey)).thenReturn(entitlementEntity);

    service.delete(entitlement(TENANT_ID, APPLICATION_ID));

    verify(entitlementRepository).delete(entitlementEntity);
  }

  @Test
  void getEntitlements_positive() {
    var appIds = List.of(APPLICATION_ID);
    var request = EntitlementRequest.builder().applications(appIds).tenantId(TENANT_ID).build();
    var entity = entitlementEntity();
    var entitlement = entitlement();

    when(entitlementMapper.map(entity)).thenReturn(entitlement);
    when(entitlementRepository.findByTenantIdAndApplicationIdIn(TENANT_ID, appIds)).thenReturn(List.of(entity));

    var result = service.getEntitlements(request);

    assertThat(result).isEqualTo(List.of(entitlement));
  }

  @Test
  void findByTenantId_positive() {
    var foundEntities = List.of(entitlementEntity());
    when(entitlementRepository.findByTenantId(TENANT_ID)).thenReturn(foundEntities);
    when(entitlementMapper.map(entitlementEntity())).thenReturn(entitlement());

    var result = service.findByTenantId(TENANT_ID);

    assertThat(result).containsExactly(entitlement());
  }
}
