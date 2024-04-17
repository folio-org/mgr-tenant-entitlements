package org.folio.entitlement.integration.kafka;

import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.entitlement.integration.folio.CommonStageContext.PARAM_REQUEST;
import static org.folio.entitlement.integration.folio.CommonStageContext.PARAM_TENANT_NAME;
import static org.folio.entitlement.support.TestConstants.FLOW_ID;
import static org.folio.entitlement.support.TestConstants.TENANT_ID;
import static org.folio.entitlement.support.TestConstants.TENANT_NAME;
import static org.folio.entitlement.support.TestValues.appStageContext;
import static org.folio.entitlement.support.TestValues.entitlement;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import org.apache.kafka.clients.admin.NewTopic;
import org.folio.entitlement.domain.model.EntitlementRequest;
import org.folio.entitlement.integration.kafka.configuration.TenantEntitlementKafkaProperties;
import org.folio.entitlement.service.EntitlementCrudService;
import org.folio.entitlement.support.TestUtils;
import org.folio.integration.kafka.FolioKafkaProperties.KafkaTopic;
import org.folio.integration.kafka.KafkaAdminService;
import org.folio.test.types.UnitTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class KafkaTenantTopicCreatorTest {

  @InjectMocks private KafkaTenantTopicCreator topicCreator;
  @Mock private KafkaAdminService kafkaAdminService;
  @Mock private EntitlementCrudService entitlementCrudService;
  @Spy private final TenantEntitlementKafkaProperties tenantEntitlementKafkaProperties = tenantTopicProperties();

  @BeforeEach
  void setUp() {
    System.setProperty("env", "tst");
  }

  @AfterEach
  void tearDown() {
    TestUtils.verifyNoMoreInteractions(this);
    System.clearProperty("env");
  }

  @Test
  void execute_positive() {
    var entitlementRequest = EntitlementRequest.builder().tenantId(TENANT_ID).build();
    var flowParameters = Map.of(PARAM_REQUEST, entitlementRequest);
    var contextParameters = Map.of(PARAM_TENANT_NAME, TENANT_NAME);
    var stageContext = appStageContext(FLOW_ID, flowParameters, contextParameters);

    when(entitlementCrudService.findByTenantId(TENANT_ID)).thenReturn(emptyList());

    topicCreator.execute(stageContext);

    assertThat(stageContext.<Boolean>get("KafkaTenantTopicCreator.created")).isTrue();
    verify(tenantEntitlementKafkaProperties).getTenantTopics();
    verify(kafkaAdminService).createTopic(new NewTopic("tst.test.test-topic", 10, (short) 1));
  }

  @Test
  void execute_positive_entitlementsFound() {
    var entitlementRequest = EntitlementRequest.builder().tenantId(TENANT_ID).build();
    var flowParameters = Map.of(PARAM_REQUEST, entitlementRequest);
    var contextParameters = Map.of(PARAM_TENANT_NAME, TENANT_NAME);
    var stageContext = appStageContext(FLOW_ID, flowParameters, contextParameters);

    when(entitlementCrudService.findByTenantId(TENANT_ID)).thenReturn(List.of(entitlement()));

    topicCreator.execute(stageContext);

    verify(tenantEntitlementKafkaProperties, never()).getTenantTopics();
    verify(kafkaAdminService, never()).createTopic(any());
    assertThat(stageContext.<Boolean>get("KafkaTenantTopicCreator.created")).isNull();
  }

  @Test
  void cancel_positive() {
    var entitlementRequest = EntitlementRequest.builder().tenantId(TENANT_ID).build();
    var flowParameters = Map.of(PARAM_REQUEST, entitlementRequest);
    var contextParameters = Map.of(PARAM_TENANT_NAME, TENANT_NAME, "KafkaTenantTopicCreator.created", true);

    var stageContext = appStageContext(FLOW_ID, flowParameters, contextParameters);

    topicCreator.cancel(stageContext);

    verify(tenantEntitlementKafkaProperties).getTenantTopics();
    verify(kafkaAdminService).deleteTopics(List.of("tst.test.test-topic"));
  }

  @Test
  void cancel_positive_skipped() {
    var entitlementRequest = EntitlementRequest.builder().tenantId(TENANT_ID).build();
    var flowParameters = Map.of(PARAM_REQUEST, entitlementRequest);
    var contextParameters = Map.of(PARAM_TENANT_NAME, TENANT_NAME);

    var stageContext = appStageContext(FLOW_ID, flowParameters, contextParameters);

    topicCreator.cancel(stageContext);

    verify(tenantEntitlementKafkaProperties, never()).getTenantTopics();
    verify(kafkaAdminService, never()).deleteTopics(anyList());
  }

  private static TenantEntitlementKafkaProperties tenantTopicProperties() {
    var props = new TenantEntitlementKafkaProperties();
    props.setTenantTopics(List.of(KafkaTopic.of("test-topic", 10, (short) 1)));
    return props;
  }
}
