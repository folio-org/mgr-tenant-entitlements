package org.folio.entitlement.integration.kafka;

import static java.lang.Boolean.TRUE;
import static org.apache.commons.collections4.ListUtils.emptyIfNull;
import static org.folio.common.utils.CollectionUtils.mapItems;
import static org.folio.integration.kafka.KafkaUtils.createTopic;
import static org.folio.integration.kafka.KafkaUtils.getTenantTopicName;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.collections4.CollectionUtils;
import org.folio.entitlement.domain.model.ApplicationStageContext;
import org.folio.entitlement.integration.kafka.configuration.TenantEntitlementKafkaProperties;
import org.folio.entitlement.service.EntitlementCrudService;
import org.folio.entitlement.service.stage.DatabaseLoggingStage;
import org.folio.integration.kafka.KafkaAdminService;
import org.springframework.stereotype.Component;

@Log4j2
@Component
@RequiredArgsConstructor
public class KafkaTenantTopicCreator extends DatabaseLoggingStage<ApplicationStageContext> {

  private static final String PARAM_TOPICS_CREATED = "KafkaTenantTopicCreator.created";

  private final KafkaAdminService kafkaAdminService;
  private final TenantEntitlementKafkaProperties tenantEntitlementKafkaProperties;
  private final EntitlementCrudService entitlementCrudService;

  @Override
  public void execute(ApplicationStageContext context) {
    var tenantName = context.getTenantName();
    var tenantId = context.getTenantId();

    var existingEntitlements = entitlementCrudService.findByTenantId(tenantId);
    if (CollectionUtils.isNotEmpty(existingEntitlements)) {
      var appId = context.getApplicationId();
      var msgTemplate = "Ignoring topics creation, current application is not first: applicationId = {}, tenantId = {}";
      log.debug(msgTemplate, appId, tenantId);
      return;
    }

    createTenantTopics(tenantName);
    context.put(PARAM_TOPICS_CREATED, true);
  }

  @Override
  public void cancel(ApplicationStageContext context) {
    var topicsCreated = context.<Boolean>get(PARAM_TOPICS_CREATED);
    if (!TRUE.equals(topicsCreated)) {
      return;
    }

    var tenantName = context.getTenantName();
    removeTenantTopics(tenantName);
  }

  private void createTenantTopics(String tenantName) {
    for (var kafkaTopic : emptyIfNull(tenantEntitlementKafkaProperties.getTenantTopics())) {
      var name = getTenantTopicName(kafkaTopic.getName(), tenantName);
      var topic = createTopic(name, kafkaTopic.getNumPartitions(), kafkaTopic.getReplicationFactor());
      kafkaAdminService.createTopic(topic);
    }
  }

  private void removeTenantTopics(String tenantName) {
    var tenantTopics = tenantEntitlementKafkaProperties.getTenantTopics();
    var topicsToPurge = mapItems(tenantTopics, topic -> getTenantTopicName(topic.getName(), tenantName));
    kafkaAdminService.deleteTopics(topicsToPurge);
  }
}
