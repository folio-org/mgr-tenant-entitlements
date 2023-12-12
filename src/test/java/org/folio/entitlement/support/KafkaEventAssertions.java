package org.folio.entitlement.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Durations.ONE_HUNDRED_MILLISECONDS;
import static org.folio.common.utils.CollectionUtils.mapItems;
import static org.folio.entitlement.support.TestConstants.capabilitiesTenantTopic;
import static org.folio.entitlement.support.TestConstants.entitlementTopic;
import static org.folio.entitlement.support.TestConstants.scheduledJobsTenantTopic;
import static org.folio.entitlement.support.TestConstants.systemUserTenantTopic;
import static org.folio.test.FakeKafkaConsumer.getEvents;
import static org.hamcrest.Matchers.emptyIterable;
import static org.testcontainers.shaded.org.awaitility.Durations.FIVE_SECONDS;

import com.fasterxml.jackson.core.type.TypeReference;
import java.util.List;
import java.util.concurrent.Callable;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.awaitility.Awaitility;
import org.awaitility.core.ConditionFactory;
import org.folio.entitlement.integration.kafka.model.CapabilityEventBody;
import org.folio.entitlement.integration.kafka.model.EntitlementEvent;
import org.folio.entitlement.integration.kafka.model.ResourceEvent;
import org.folio.entitlement.integration.kafka.model.SystemUserEvent;
import org.folio.security.domain.model.descriptor.RoutingEntry;
import org.hamcrest.Matchers;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class KafkaEventAssertions {

  private static ConditionFactory await() {
    return Awaitility.await().atMost(FIVE_SECONDS).pollInterval(ONE_HUNDRED_MILLISECONDS);
  }

  public static void assertEntitlementEvents(List<EntitlementEvent> events) {
    var entitlementEvents = getRecordValues(() -> getEvents(entitlementTopic(), EntitlementEvent.class));
    assertThat(entitlementEvents).containsExactlyInAnyOrderElementsOf(events);
  }

  @SafeVarargs
  public static void assertCapabilityEvents(ResourceEvent<CapabilityEventBody>... events) {
    var capabilityEvents = getRecordValues(() ->
      getEvents(capabilitiesTenantTopic(), new TypeReference<ResourceEvent<CapabilityEventBody>>() {}));
    assertThat(capabilityEvents).contains(events);
  }

  @SafeVarargs
  public static void assertScheduledJobEvents(ResourceEvent<RoutingEntry>... events) {
    var capabilityEvents = getRecordValues(() ->
      getEvents(scheduledJobsTenantTopic(), new TypeReference<ResourceEvent<RoutingEntry>>() {}));
    assertThat(capabilityEvents).containsExactly(events);
  }

  @SafeVarargs
  public static void assertSystemUserEvents(ResourceEvent<SystemUserEvent>... events) {
    var systemUserEvents = getRecordValues(() ->
      getEvents(systemUserTenantTopic(), new TypeReference<ResourceEvent<SystemUserEvent>>() {}));
    assertThat(systemUserEvents).containsExactly(events);
  }

  private static <T> List<T> getRecordValues(Callable<List<ConsumerRecord<String, T>>> callable) {
    var kafkaEvents = await().until(callable, Matchers.not(emptyIterable()));
    return mapItems(kafkaEvents, ConsumerRecord::value);
  }
}
