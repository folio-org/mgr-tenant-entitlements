package org.folio.entitlement.integration.folio;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.folio.common.domain.model.error.Parameter;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class FolioIntegrationUtils {

  /**
   * Parses tenant parameter string to a list of {@link Parameter} values.
   *
   * @param tenantParametersString - tenant parameters in format {@code key=value,key2=value2 }
   * @return created list with tenant parameters.
   */
  public static List<Parameter> parseTenantParameters(String tenantParametersString) {
    if (StringUtils.isBlank(tenantParametersString)) {
      return Collections.emptyList();
    }

    var tenantParameters = new ArrayList<Parameter>();
    for (var parameterString : tenantParametersString.split(",")) {
      var keyValueArray = parameterString.split("=");
      var tenantParameter = new Parameter().key(keyValueArray[0]);
      if (keyValueArray.length > 1) {
        tenantParameter.setValue(keyValueArray[1]);
      }
      tenantParameters.add(tenantParameter);
    }

    return tenantParameters;
  }
}
