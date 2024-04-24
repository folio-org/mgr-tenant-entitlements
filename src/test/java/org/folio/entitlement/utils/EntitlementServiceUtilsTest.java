package org.folio.entitlement.utils;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.folio.test.types.UnitTest;
import org.junit.jupiter.api.Test;

@UnitTest
class EntitlementServiceUtilsTest {

  @Test
  void toHashMap_positive() {
    var list = List.of(1, 2);
    var result = EntitlementServiceUtils.toHashMap(list, Function.identity(), value -> Integer.toString(value));
    assertThat(result).isEqualTo(Map.of(1, "1", 2, "2"));
  }

  @Test
  void toHashMap_positive_emptyInput() {
    var list = Collections.<Integer>emptyList();
    var result = EntitlementServiceUtils.toHashMap(list, Function.identity(), value -> Integer.toString(value));
    assertThat(result).isEmpty();
  }

  @Test
  void toUnmodifiableMap_positive() {
    var list = List.of(1, 2);
    var result = EntitlementServiceUtils.toUnmodifiableMap(list, Object::toString);
    assertThat(result).isEqualTo(Map.of("1", 1, "2", 2));
  }

  @Test
  void toUnmodifiableMap_positive_emptyInput() {
    var list = Collections.<Integer>emptyList();
    var result = EntitlementServiceUtils.toUnmodifiableMap(list, Object::toString);
    assertThat(result).isEmpty();
  }
}
