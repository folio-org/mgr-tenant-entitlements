package org.folio.entitlement.controller.converter;

import org.apache.commons.lang3.StringUtils;
import org.folio.entitlement.domain.dto.EntitlementType;
import org.springframework.core.convert.converter.Converter;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

public final class EntitlementTypeConverters {

  private EntitlementTypeConverters() {
  }

  @Component
  public static class FromString implements Converter<String, EntitlementType> {

    @Override
    public EntitlementType convert(@NonNull String source) {
      return EntitlementType.fromValue(StringUtils.lowerCase(source));
    }
  }

  @Component
  public static class ToString implements Converter<EntitlementType, String> {

    @Override
    public String convert(EntitlementType source) {
      return source.getValue();
    }
  }
}
