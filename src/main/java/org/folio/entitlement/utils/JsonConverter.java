package org.folio.entitlement.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.SerializationException;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class JsonConverter {

  private static final String SERIALIZATION_ERROR_MSG_TEMPLATE = "Failed to serialize value [message: %s]";
  private final ObjectMapper objectMapper;

  /**
   * Converts passed {@link Object} value to json string.
   *
   * @param value value to convert
   * @return json value as {@link String}.
   */
  public String toJson(Object value) {
    if (value == null) {
      return null;
    }

    try {
      return objectMapper.writeValueAsString(value);
    } catch (JsonProcessingException e) {
      throw new SerializationException(String.format(SERIALIZATION_ERROR_MSG_TEMPLATE, e.getMessage()));
    }
  }
}
