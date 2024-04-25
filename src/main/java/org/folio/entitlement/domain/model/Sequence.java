package org.folio.entitlement.domain.model;

public final class Sequence {

  private int counter;
  private final String prefix;

  private Sequence(String prefix) {
    this.prefix = prefix;
    this.counter = 0;
  }

  public static Sequence withPrefix(String prefix) {
    return new Sequence(prefix);
  }

  public String nextValue() {
    return prefix + counter++;
  }
}
