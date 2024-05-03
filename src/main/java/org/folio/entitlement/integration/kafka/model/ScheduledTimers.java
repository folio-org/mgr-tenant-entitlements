package org.folio.entitlement.integration.kafka.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.folio.common.domain.model.RoutingEntry;

@Data
@JsonInclude(Include.NON_EMPTY)
@AllArgsConstructor(staticName = "of")
public class ScheduledTimers {

  /**
   * Module identifier.
   */
  private String moduleId;

  /**
   * Application identifier.
   */
  private String applicationId;

  /**
   * List with defined folio resources and corresponding permissions.
   */
  private List<RoutingEntry> timers;
}
