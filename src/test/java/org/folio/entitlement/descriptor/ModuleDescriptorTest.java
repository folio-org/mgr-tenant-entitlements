package org.folio.entitlement.descriptor;

import static java.util.stream.Collectors.toSet;
import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.entitlement.integration.kafka.KafkaEventUtils.CAPABILITIES_TOPIC;
import static org.folio.entitlement.integration.kafka.KafkaEventUtils.CONSUMED_EVENT_RESOURCE_RESULT;
import static org.folio.entitlement.integration.kafka.KafkaEventUtils.PRODUCED_EVENT_CAPABILITY;
import static org.folio.entitlement.integration.kafka.KafkaEventUtils.PRODUCED_EVENT_ENTITLEMENT;
import static org.folio.entitlement.integration.kafka.KafkaEventUtils.PRODUCED_EVENT_SYSTEM_USER;
import static org.folio.entitlement.integration.kafka.KafkaEventUtils.SYSTEM_USER_TOPIC;
import static org.folio.entitlement.support.TestUtils.readModuleDescriptor;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import lombok.SneakyThrows;
import org.folio.common.domain.model.ConsumesEvent;
import org.folio.common.domain.model.InterfaceDescriptor;
import org.folio.common.domain.model.ModuleDescriptor;
import org.folio.common.domain.model.Permission;
import org.folio.common.domain.model.ProducesEvent;
import org.folio.common.domain.model.RoutingEntry;
import org.folio.test.types.UnitTest;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.io.ClassPathResource;

/**
 * Verifies that the internal module descriptor is structurally valid and ready to be bundled into the
 * {@code app-mgr-services} platform application (MGRENTITLE-211).
 *
 * <p>The descriptor is loaded from the same classpath location and into the same model that other FOLIO manager
 * services load at startup, so a failure here is a real startup failure.</p>
 */
@UnitTest
class ModuleDescriptorTest {

  private static final String DESCRIPTOR_PATH = "descriptors/ModuleDescriptor.json";

  private static ModuleDescriptor descriptor;

  @BeforeAll
  static void beforeAll() {
    descriptor = readModuleDescriptor(DESCRIPTOR_PATH);
  }

  @Test
  void moduleDescriptor_positive_parsesIntoSharedModel() {
    assertThat(descriptor.getId()).isNotBlank();
    assertThat(descriptor.getDescription()).isNotBlank();
    assertThat(descriptor.getName()).isEqualTo("mgr-tenant-entitlements");
    assertThat(descriptor.getVersion()).isNotBlank();
    assertThat(descriptor.isMgrComponent()).isTrue();
    assertThat(descriptor.getProvides()).isNotEmpty();
    assertThat(descriptor.getPermissionSets()).isNotEmpty();
  }

  @Test
  void moduleDescriptor_positive_handlersAreFullyDefined() {
    assertThat(handlers()).isNotEmpty().allSatisfy(handler -> {
      assertThat(handler.getMethods()).isNotEmpty();
      assertThat(handler.getPathPattern()).isNotBlank();
    });

    assertThat(descriptor.getProvides()).allSatisfy(provided -> {
      assertThat(provided.getId()).isNotBlank();
      assertThat(provided.getVersion()).isNotBlank();
    });
  }

  @Test
  void moduleDescriptor_positive_allHandlerPermissionsDefined() {
    var definedPermissions = definedPermissionNames();

    var requiredPermissions = handlers().stream()
      .flatMap(handler -> handler.getPermissionsRequired().stream())
      .collect(toSet());

    assertThat(requiredPermissions).isNotEmpty().allSatisfy(permission ->
      assertThat(definedPermissions).as("permission required by a handler must be defined in permissionSets: %s",
        permission).contains(permission));
  }

  @Test
  void moduleDescriptor_positive_subPermissionsResolve() {
    var definedPermissions = definedPermissionNames();

    var subPermissions = descriptor.getPermissionSets().stream()
      .flatMap(permission -> permission.getSubPermissions().stream())
      .collect(toSet());

    assertThat(subPermissions).isNotEmpty().allSatisfy(permission ->
      assertThat(definedPermissions).as("subPermission must be defined in permissionSets: %s", permission)
        .contains(permission));
  }

  @Test
  void moduleDescriptor_positive_permissionNamesUnique() {
    var permissionNames = descriptor.getPermissionSets().stream()
      .map(Permission::getPermissionName)
      .toList();

    assertThat(permissionNames).isNotEmpty().doesNotHaveDuplicates().allSatisfy(name ->
      assertThat(name).isNotBlank());
  }

  /**
   * mgr-tenant-entitlements publishes the entitlement, system-user-creation and capability-creation events.
   * capabilitySet is not declared separately: there is no backing publisher/topic for it, only the single
   * {@code capability} event, whose payload already covers both resource kinds (MGRENTITLE-211). The
   * scheduled-job event is deliberately excluded pending architect confirmation.
   */
  @Test
  void moduleDescriptor_positive_publishedEventsDeclared() {
    var producedEvents = descriptor.getProducesEvents().stream()
      .map(ProducesEvent::getEvent)
      .toList();

    assertThat(producedEvents).containsExactlyInAnyOrder(
      PRODUCED_EVENT_ENTITLEMENT, PRODUCED_EVENT_SYSTEM_USER, PRODUCED_EVENT_CAPABILITY);
  }

  /**
   * mgr-tenant-entitlements consumes acknowledgments of its own previously-published events on the
   * {@code resource-result} topic — a self-contained feedback loop, config-gated per event type by
   * {@code application.event-publishing.*.await-completion}, so it is declared {@code optional} rather than
   * required (MGRENTITLE-211).
   */
  @Test
  void moduleDescriptor_positive_resourceResultConsumedEventDeclared() {
    var consumedEvents = descriptor.getConsumesEvents();

    assertThat(consumedEvents).singleElement().satisfies(event -> {
      assertThat(event.getEvent()).isEqualTo(CONSUMED_EVENT_RESOURCE_RESULT);
      assertThat(event.getOptional()).isTrue();
    });
  }

  /**
   * Every produced/consumed identifier follows the {@code <module>.<resource>[.<event-type>].<version>} shape
   * from the EUREKA-847 "Event Identifier as Dependency Unit" naming-convention feedback (e.g. its
   * {@code inventory.instance.created.v1} example) rather than being a bare Kafka topic/channel name:
   * module-qualified, dotted, and major-versioned (MGRENTITLE-211).
   */
  @Test
  void moduleDescriptor_positive_eventIdentifiersFollowNamingConvention() {
    var rawTopicNames = Set.of("entitlement", SYSTEM_USER_TOPIC, CAPABILITIES_TOPIC,
      "mgr-tenant-entitlements.resource-result");

    var allIdentifiers = allEventIdentifiers();

    assertThat(allIdentifiers).isNotEmpty().allSatisfy(identifier -> {
      assertThat(identifier)
        .as("event identifier should be <module>.<resource>[.<event-type>].vN, not the bare topic/channel name")
        .matches("[a-z][a-z0-9-]*(\\.[a-z][a-z0-9-]*){1,}\\.v\\d+")
        .startsWith(descriptor.getName() + ".");
      assertThat(rawTopicNames).as("event identifier must not be a bare topic/channel name: %s", identifier)
        .isNotEmpty()
        .doesNotContain(identifier);
    });
  }

  /**
   * The {@code entitlement} and {@code resource-result} identifiers correspond to {@code "entitlement"} and
   * {@code "mgr-tenant-entitlements.resource-result"}, the two env-scoped topics
   * {@code EntitlementEventPublisher}/{@code KafkaMessageListener} actually use, configured under
   * {@code application.kafka.producer.topics} (the environment prefix is added at runtime).
   */
  @Test
  void moduleDescriptor_positive_envScopedTopicsStillConfigured() {
    assertThat(configuredTopicNames("application.kafka.producer.topics["))
      .contains("entitlement", "mgr-tenant-entitlements.resource-result");
  }

  /**
   * The system-user and capability identifiers correspond to {@link org.folio.entitlement.integration.kafka
   * .KafkaEventUtils#SYSTEM_USER_TOPIC}/{@code CAPABILITIES_TOPIC}, the tenant-scoped topics those publishers
   * actually use, configured under {@code application.kafka.tenant-topics}. {@code SCHEDULED_JOB_TOPIC} is
   * deliberately not asserted here: that event stays undeclared pending architect confirmation.
   */
  @Test
  void moduleDescriptor_positive_tenantScopedTopicsStillConfigured() {
    assertThat(configuredTopicNames("application.kafka.tenant-topics["))
      .contains(SYSTEM_USER_TOPIC, CAPABILITIES_TOPIC);
  }

  private static Set<String> allEventIdentifiers() {
    var produced = descriptor.getProducesEvents().stream().map(ProducesEvent::getEvent);
    var consumed = descriptor.getConsumesEvents().stream().map(ConsumesEvent::getEvent);
    return Stream.concat(produced, consumed).collect(toSet());
  }

  private static Set<String> definedPermissionNames() {
    return descriptor.getPermissionSets().stream()
      .map(Permission::getPermissionName)
      .collect(toSet());
  }

  private static List<RoutingEntry> handlers() {
    return descriptor.getProvides().stream()
      .map(InterfaceDescriptor::getHandlers)
      .flatMap(List::stream)
      .toList();
  }

  @SneakyThrows
  private static Set<String> configuredTopicNames(String propertyPrefix) {
    var resource = new ClassPathResource("application.yml");
    var propertySource = (EnumerablePropertySource<?>) new YamlPropertySourceLoader()
      .load("application", resource)
      .getFirst();

    return Arrays.stream(propertySource.getPropertyNames())
      .filter(name -> name.startsWith(propertyPrefix) && name.endsWith("].name"))
      .map(name -> (String) propertySource.getProperty(name))
      .collect(toSet());
  }
}
