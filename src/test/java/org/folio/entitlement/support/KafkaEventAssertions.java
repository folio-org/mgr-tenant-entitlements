package org.folio.entitlement.support;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Durations.FIVE_SECONDS;
import static org.awaitility.Durations.ONE_HUNDRED_MILLISECONDS;
import static org.folio.common.utils.CollectionUtils.mapItems;
import static org.folio.common.utils.OkapiHeaders.TENANT;
import static org.folio.entitlement.support.TestConstants.capabilitiesTenantTopic;
import static org.folio.entitlement.support.TestConstants.entitlementTopic;
import static org.folio.entitlement.support.TestConstants.scheduledJobsTenantTopic;
import static org.folio.entitlement.support.TestConstants.systemUserTenantTopic;
import static org.folio.integration.kafka.consumer.KafkaTenantHeaders.FOLIO_TENANT_ID;
import static org.folio.test.FakeKafkaConsumer.getEvents;

import java.util.List;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.awaitility.Awaitility;
import org.awaitility.core.ConditionFactory;
import org.folio.entitlement.integration.kafka.model.CapabilityEventPayload;
import org.folio.entitlement.integration.kafka.model.EntitlementEvent;
import org.folio.entitlement.integration.kafka.model.ScheduledTimers;
import org.folio.entitlement.integration.kafka.model.SystemUserEvent;
import org.folio.integration.kafka.model.ResourceEvent;
import tools.jackson.core.type.TypeReference;

@Log4j2
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class KafkaEventAssertions {

  private static ConditionFactory await() {
    return Awaitility.await().atMost(FIVE_SECONDS).pollInterval(ONE_HUNDRED_MILLISECONDS);
  }

  public static void assertEntitlementEvents(EntitlementEvent... events) {
    assertEntitlementEvents(asList(events));
  }

  public static void assertEntitlementEvents(List<EntitlementEvent> events) {
    await().untilAsserted(() -> {
      var consumerRecords = getEvents(entitlementTopic(), EntitlementEvent.class);
      consumerRecords.forEach(record -> assertTenantHeaders(record, record.value().getTenantName()));
      var entitlementEvents = mapItems(consumerRecords, ConsumerRecord::value);
      assertThat(entitlementEvents).containsAll(events);
    });
  }

  @SafeVarargs
  public static void assertCapabilityEvents(ResourceEvent<CapabilityEventPayload>... events) {
    assertCapabilityEvents(asList(events));
  }

  public static void assertCapabilityEvents(List<ResourceEvent<CapabilityEventPayload>> events) {
    var type = new TypeReference<ResourceEvent<CapabilityEventPayload>>() {};
    await().untilAsserted(() -> assertEventsSequence(capabilitiesTenantTopic(), type, events));
  }

  @SafeVarargs
  public static void assertScheduledJobEvents(ResourceEvent<ScheduledTimers>... events) {
    assertScheduledJobEvents(asList(events));
  }

  public static void assertScheduledJobEvents(List<ResourceEvent<ScheduledTimers>> events) {
    var type = new TypeReference<ResourceEvent<ScheduledTimers>>() {};
    await().untilAsserted(() -> assertEventsSequence(scheduledJobsTenantTopic(), type, events));
  }

  @SafeVarargs
  public static void assertSystemUserEvents(ResourceEvent<SystemUserEvent>... events) {
    assertSystemUserEvents(asList(events));
  }

  public static void assertSystemUserEvents(List<ResourceEvent<SystemUserEvent>> events) {
    var type = new TypeReference<ResourceEvent<SystemUserEvent>>() {};
    await().untilAsserted(() -> assertEventsSequence(systemUserTenantTopic(), type, events));
  }

  private static <T> void assertEventsSequence(String topic, TypeReference<ResourceEvent<T>> type,
    List<ResourceEvent<T>> events) {
    var consumerRecords = getEvents(topic, type);
    consumerRecords.forEach(record -> assertTenantHeaders(record, record.value().getTenant()));
    var eventValues = mapItems(consumerRecords, ConsumerRecord::value);
    // id is set to the stage UUID by AbstractModuleEventPublisher; ignore it when comparing event payloads
    assertThat(eventValues)
      .usingRecursiveFieldByFieldElementComparatorIgnoringFields("id").containsAll(events)
      .allSatisfy(event -> assertThat(event.getId()).isNotBlank());
  }

  private static void assertTenantHeaders(ConsumerRecord<String, ?> record, String tenant) {
    var tenantBytes = tenant.getBytes(UTF_8);
    assertThat(record.headers().lastHeader(TENANT))
      .as("%s header on topic %s", TENANT, record.topic()).isNotNull()
      .extracting(Header::value).isEqualTo(tenantBytes);
    assertThat(record.headers().lastHeader(FOLIO_TENANT_ID))
      .as("%s header on topic %s", FOLIO_TENANT_ID, record.topic()).isNotNull()
      .extracting(Header::value).isEqualTo(tenantBytes);
  }
}
