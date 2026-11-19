package org.folio.entitlement.service.validator;

import static java.util.stream.Collectors.groupingBy;
import static org.folio.common.utils.CollectionUtils.toStream;

import org.folio.common.domain.model.error.Parameter;
import org.folio.common.utils.CollectionUtils;
import org.folio.common.utils.SemverUtils;
import org.folio.entitlement.domain.model.CommonStageContext;
import org.folio.entitlement.exception.RequestValidationException;
import org.folio.entitlement.service.stage.DatabaseLoggingStage;
import org.springframework.stereotype.Component;

/**
 * Rejects the request if it names one application more than once, in any versions. Every duplicate is reported.
 *
 * <p>Runs first in the platform flows: the validators and stages after it map applications by name and assume each
 * name occurs once.</p>
 */
@Component
public class PlatformDistinctApplicationsValidator extends DatabaseLoggingStage<CommonStageContext> {

  @Override
  public void execute(CommonStageContext context) {
    var idsByName = toStream(context.getEntitlementRequest().getApplications())
      .collect(groupingBy(SemverUtils::getName));
    var duplicates = toStream(idsByName.values())
      .filter(ids -> ids.size() > 1)
      .flatMap(CollectionUtils::toStream)
      .map(applicationId -> new Parameter().key(applicationId).value("Application is requested more than once"))
      .toList();

    if (!duplicates.isEmpty()) {
      throw new RequestValidationException("Request contains the same application more than once", duplicates);
    }
  }
}
