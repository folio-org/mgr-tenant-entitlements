package org.folio.entitlement.integration.okapi.model;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Data;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Data
public class TenantModuleDescriptor {

  private String id;
  private String from;
  private String message;
  private StageType stage;
  private ActionType action;

  public TenantModuleDescriptor id(String id) {
    this.id = id;
    return this;
  }

  public TenantModuleDescriptor from(String from) {
    this.from = from;
    return this;
  }

  public TenantModuleDescriptor action(ActionType action) {
    this.action = action;
    return this;
  }

  public TenantModuleDescriptor stage(StageType stage) {
    this.stage = stage;
    return this;
  }

  public TenantModuleDescriptor message(String message) {
    this.message = message;
    return this;
  }

  @Getter
  @RequiredArgsConstructor
  public enum ActionType {
    ENABLE("enable"),
    DISABLE("disable"),
    UP_TO_DATE("uptodate"),
    SUGGEST("suggest"),
    CONFLICT("conflict");

    @JsonValue
    private final String value;
  }

  @Getter
  @RequiredArgsConstructor
  public enum StageType {

    PENDING("pending"),
    DEPLOY("deploy"),
    INVOKE("invoke"),
    UNDEPLOY("undeploy"),
    DONE("done");

    @JsonValue
    private final String value;
  }
}
