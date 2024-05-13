package org.folio.entitlement.support;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Durations.FIVE_SECONDS;
import static org.awaitility.Durations.ONE_HUNDRED_MILLISECONDS;
import static org.folio.common.utils.CollectionUtils.mapItems;
import static org.folio.entitlement.support.TestConstants.capabilitiesTenantTopic;
import static org.folio.entitlement.support.TestConstants.entitlementTopic;
import static org.folio.entitlement.support.TestConstants.scheduledJobsTenantTopic;
import static org.folio.entitlement.support.TestConstants.systemUserTenantTopic;
import static org.folio.test.FakeKafkaConsumer.getEvents;

import com.fasterxml.jackson.core.type.TypeReference;
import java.util.List;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.awaitility.Awaitility;
import org.awaitility.core.ConditionFactory;
import org.folio.entitlement.integration.kafka.model.CapabilityEventPayload;
import org.folio.entitlement.integration.kafka.model.EntitlementEvent;
import org.folio.entitlement.integration.kafka.model.ResourceEvent;
import org.folio.entitlement.integration.kafka.model.ScheduledTimers;
import org.folio.entitlement.integration.kafka.model.SystemUserEvent;

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

  private static <T> void assertEventsSequence(String topic, TypeReference<T> type, List<T> events) {
    var consumerRecords = getEvents(topic, type);
    var eventValues = mapItems(consumerRecords, ConsumerRecord::value);
    assertThat(eventValues).containsAll(events);
  }
}
