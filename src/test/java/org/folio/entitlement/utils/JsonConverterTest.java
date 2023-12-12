package org.folio.entitlement.utils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.folio.entitlement.support.TestUtils.OBJECT_MAPPER;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.commons.lang3.SerializationException;
import org.folio.test.types.UnitTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class JsonConverterTest {

  private static final String FIELD_VALUE = "value";
  private static final String JSON_BODY = "{\"field\":\"value\"}";

  @Spy private final ObjectMapper objectMapper = OBJECT_MAPPER;
  @InjectMocks private JsonConverter jsonConverter;

  @Test
  void toJson_positive() throws JsonProcessingException {
    var actual = jsonConverter.toJson(TestClass.of(FIELD_VALUE));
    assertThat(actual).isEqualTo(JSON_BODY);

    verify(objectMapper).writeValueAsString(TestClass.of(FIELD_VALUE));
  }

  @Test
  void toJson_positive_nullValue() {
    var actual = jsonConverter.toJson(null);
    assertThat(actual).isNull();
  }

  @Test
  void toJson_negative_throwsException() {
    var value = new NonSerializableByJacksonClass();
    assertThatThrownBy(() -> jsonConverter.toJson(value))
      .isInstanceOf(SerializationException.class)
      .hasMessageContaining("Failed to serialize value");
  }

  @Data
  @NoArgsConstructor
  @AllArgsConstructor(staticName = "of")
  public static class TestClass {

    private String field;
  }

  public static class NonSerializableByJacksonClass {

    private final NonSerializableByJacksonClass self = this;

    @SuppressWarnings("unused")
    public NonSerializableByJacksonClass getSelf() {
      return self;
    }
  }
}
