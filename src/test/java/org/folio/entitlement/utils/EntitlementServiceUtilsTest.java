package org.folio.entitlement.utils;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.folio.common.domain.model.ModuleDescriptor;
import org.folio.test.types.UnitTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

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

  @ParameterizedTest
  @CsvSource({
    "mod-foo-1.0.0, mod-foo-1.1.0, true",
    "mod-foo-1.0.0, mod-foo-1.0.0, false",
    " , mod-foo-1.0.0, true",
    "mod-foo-1.0.0, , true",
    " , , false"
  })
  void isModuleVersionChanged_parameterized(String id, String installedId, boolean expected) {
    var result = EntitlementServiceUtils.isModuleVersionChanged(moduleDescriptor(id), moduleDescriptor(installedId));
    assertThat(result).isEqualTo(expected);
  }

  private static ModuleDescriptor moduleDescriptor(String id) {
    return id != null ? new ModuleDescriptor().id(id) : null;
  }
}
