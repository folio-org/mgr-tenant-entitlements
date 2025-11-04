package org.folio.entitlement.controller.converter;

import org.apache.commons.lang3.StringUtils;
import org.folio.entitlement.domain.dto.EntitlementRequestType;
import org.springframework.core.convert.converter.Converter;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

public final class EntitlementRequestTypeConverters {

  private EntitlementRequestTypeConverters() {
  }

  @Component
  public static class FromString implements Converter<String, EntitlementRequestType> {

    @Override
    public EntitlementRequestType convert(@NonNull String source) {
      return EntitlementRequestType.fromValue(StringUtils.lowerCase(source));
    }
  }

  @Component
  public static class ToString implements Converter<EntitlementRequestType, String> {

    @Override
    public String convert(EntitlementRequestType source) {
      return source.getValue();
    }
  }
}
