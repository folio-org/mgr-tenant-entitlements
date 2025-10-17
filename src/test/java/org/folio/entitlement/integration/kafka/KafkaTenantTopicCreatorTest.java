package org.folio.entitlement.integration.kafka;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static java.util.Collections.emptySet;
import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.entitlement.domain.dto.EntitlementType.ENTITLE;
import static org.folio.entitlement.domain.model.CommonStageContext.PARAM_REQUEST;
import static org.folio.entitlement.domain.model.CommonStageContext.PARAM_TENANT_NAME;
import static org.folio.entitlement.support.TestConstants.FLOW_ID;
import static org.folio.entitlement.support.TestConstants.TENANT_ID;
import static org.folio.entitlement.support.TestConstants.TENANT_NAME;
import static org.folio.entitlement.support.TestUtils.verifyNoMoreInteractions;
import static org.folio.entitlement.support.TestValues.commonStageContext;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.kafka.clients.admin.NewTopic;
import org.folio.entitlement.domain.dto.EntitlementType;
import org.folio.entitlement.domain.model.CommonStageContext;
import org.folio.entitlement.domain.model.EntitlementRequest;
import org.folio.entitlement.integration.kafka.configuration.TenantEntitlementKafkaProperties;
import org.folio.integration.kafka.FolioKafkaProperties.KafkaTopic;
import org.folio.integration.kafka.KafkaAdminService;
import org.folio.test.types.UnitTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class KafkaTenantTopicCreatorTest {

  private static final String KAFKA_TENANT_TOPIC_CREATOR_CREATED = "KafkaTenantTopicCreator.created";
  private static final String TEST_TENANT_TOPIC = "folio.test.test-topic";
  private static final String TEST_TENANT_COLLECTION_TOPIC = "folio.ALL.test-topic";

  @InjectMocks private KafkaTenantTopicCreator topicCreator;
  @Mock private KafkaAdminService kafkaAdminService;
  @Spy private final TenantEntitlementKafkaProperties tenantEntitlementKafkaProperties = tenantTopicProperties();

  @BeforeEach
  void setUp() {
    System.setProperty("env", "folio");
  }

  @AfterEach
  void tearDown() {
    System.clearProperty("env");
    verifyNoMoreInteractions(this);
  }

  @Test
  void execute_positive() {
    var entitlementRequest = entitlementRequest(ENTITLE);

    when(kafkaAdminService.findTopics(Set.of(TEST_TENANT_TOPIC))).thenReturn(emptyList());
    when(tenantEntitlementKafkaProperties.isProducerTenantCollection()).thenReturn(false);

    var stageContext = stageContext(entitlementRequest);
    topicCreator.execute(stageContext);

    assertThat(stageContext.<Boolean>get(KAFKA_TENANT_TOPIC_CREATOR_CREATED)).isTrue();
    verify(tenantEntitlementKafkaProperties).getTenantTopics();
    verify(kafkaAdminService).createTopic(new NewTopic(TEST_TENANT_TOPIC, 10, (short) 1));
  }

  @Test
  void execute_positive_useTenantCollectionTopic() {
    var entitlementRequest = entitlementRequest(ENTITLE);

    when(kafkaAdminService.findTopics(Set.of(TEST_TENANT_COLLECTION_TOPIC))).thenReturn(emptyList());
    when(tenantEntitlementKafkaProperties.isProducerTenantCollection()).thenReturn(true);

    var stageContext = stageContext(entitlementRequest);
    topicCreator.execute(stageContext);

    assertThat(stageContext.<Boolean>get(KAFKA_TENANT_TOPIC_CREATOR_CREATED)).isTrue();
    verify(tenantEntitlementKafkaProperties).getTenantTopics();
    verify(kafkaAdminService).createTopic(new NewTopic(TEST_TENANT_COLLECTION_TOPIC, 10, (short) 1));
  }

  @Test
  void execute_positive_topicIsPresent() {
    var entitlementRequest = entitlementRequest(ENTITLE);

    when(kafkaAdminService.findTopics(Set.of(TEST_TENANT_TOPIC))).thenReturn(Set.of(TEST_TENANT_TOPIC));
    when(tenantEntitlementKafkaProperties.isProducerTenantCollection()).thenReturn(false);

    var stageContext = stageContext(entitlementRequest);
    topicCreator.execute(stageContext);

    assertThat(stageContext.<Boolean>get(KAFKA_TENANT_TOPIC_CREATOR_CREATED)).isNull();
    verify(tenantEntitlementKafkaProperties).getTenantTopics();
    verify(kafkaAdminService, never()).createTopic(any());
  }

  @Test
  void execute_positive_noTenantTopics() {
    var entitlementRequest = entitlementRequest(ENTITLE);
    when(tenantEntitlementKafkaProperties.getTenantTopics()).thenReturn(emptyList());
    when(kafkaAdminService.findTopics(emptySet())).thenReturn(emptyList());
    when(tenantEntitlementKafkaProperties.isProducerTenantCollection()).thenReturn(false);

    var stageContext = stageContext(entitlementRequest);
    topicCreator.execute(stageContext);

    assertThat(stageContext.<Boolean>get(KAFKA_TENANT_TOPIC_CREATOR_CREATED)).isNull();
  }

  @ParameterizedTest
  @EnumSource(value = EntitlementType.class, names = {"ENTITLE"}, mode = EnumSource.Mode.EXCLUDE)
  void execute_positive_notEntitleType(EntitlementType type) {
    var entitlementRequest = entitlementRequest(type);
    var stageContext = stageContext(entitlementRequest);

    topicCreator.execute(stageContext);

    assertThat(stageContext.<Boolean>get(KAFKA_TENANT_TOPIC_CREATOR_CREATED)).isNull();
  }

  @Test
  void cancel_positive() {
    var entitlementRequest = entitlementRequest(ENTITLE);
    var stageContext = stageContext(entitlementRequest, Map.of(KAFKA_TENANT_TOPIC_CREATOR_CREATED, true));

    topicCreator.cancel(stageContext);

    verify(tenantEntitlementKafkaProperties).getTenantTopics();
    verify(kafkaAdminService).deleteTopics(List.of(TEST_TENANT_TOPIC));
  }

  @Test
  void cancel_positive_skipped() {
    var entitlementRequest = entitlementRequest(ENTITLE);
    var stageContext = stageContext(entitlementRequest);

    topicCreator.cancel(stageContext);

    verify(tenantEntitlementKafkaProperties, never()).getTenantTopics();
    verify(kafkaAdminService, never()).deleteTopics(anyList());
  }

  private static TenantEntitlementKafkaProperties tenantTopicProperties() {
    var props = new TenantEntitlementKafkaProperties();
    props.setTenantTopics(List.of(KafkaTopic.of("test-topic", 10, (short) 1)));
    return props;
  }

  private static EntitlementRequest entitlementRequest(EntitlementType type) {
    return EntitlementRequest.builder()
      .tenantId(TENANT_ID)
      .type(type)
      .build();
  }

  private static CommonStageContext stageContext(EntitlementRequest entitlementRequest) {
    return stageContext(entitlementRequest, emptyMap());
  }

  private static CommonStageContext stageContext(EntitlementRequest entitlementRequest,
    Map<?, ?> additionalStageParameters) {
    var stageParameters = new HashMap<>();
    stageParameters.put(PARAM_TENANT_NAME, TENANT_NAME);
    stageParameters.putAll(additionalStageParameters);

    var flowParameters = Map.of(PARAM_REQUEST, entitlementRequest);
    return commonStageContext(FLOW_ID, flowParameters, stageParameters);
  }
}
