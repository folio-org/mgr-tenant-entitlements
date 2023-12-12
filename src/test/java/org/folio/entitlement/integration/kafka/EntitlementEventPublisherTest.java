package org.folio.entitlement.integration.kafka;

import static org.folio.common.utils.OkapiHeaders.MODULE_ID;
import static org.folio.common.utils.OkapiHeaders.TENANT;
import static org.folio.entitlement.domain.dto.EntitlementType.ENTITLE;
import static org.folio.entitlement.support.TestConstants.TENANT_ID;
import static org.mockito.Mockito.verify;

import org.folio.entitlement.integration.kafka.model.EntitlementEvent;
import org.folio.entitlement.support.TestUtils;
import org.folio.test.types.UnitTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class EntitlementEventPublisherTest {

  @InjectMocks private EntitlementEventPublisher entitlementEventPublisher;
  @Mock private KafkaEventPublisher kafkaEventPublisher;

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
  void publish_positive() {
    var event = new EntitlementEvent(ENTITLE.name(), MODULE_ID, TENANT, TENANT_ID);
    entitlementEventPublisher.publish(event);
    verify(kafkaEventPublisher).send("tst.entitlement", MODULE_ID, event);
  }
}
