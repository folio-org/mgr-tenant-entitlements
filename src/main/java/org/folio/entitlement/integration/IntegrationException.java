package org.folio.entitlement.integration;

import static java.util.Collections.emptyList;

import java.io.Serial;
import java.util.List;
import lombok.Getter;
import org.folio.common.domain.model.error.Parameter;

@Getter
public class IntegrationException extends RuntimeException {

  @Serial private static final long serialVersionUID = 8607358059178754096L;

  private final transient List<Parameter> errors;

  private final Integer causeHttpStatus;

  /**
   * Creates a new {@link IntegrationException} with corresponding error message.
   *
   * @param message - error message as {@link String} object
   * @param errors - {@link List} with error {@link Parameter} objects
   */
  public IntegrationException(String message, List<Parameter> errors) {
    super(message);
    this.errors = errors;
    this.causeHttpStatus = null;
  }

  /**
   * Creates a new {@link IntegrationException} with corresponding error message.
   *
   * @param message - error message as {@link String} object
   * @param errors - {@link List} with error {@link Parameter} objects
   * @param cause - error cause as {@link Throwable} object
   */
  public IntegrationException(String message, List<Parameter> errors, Throwable cause) {
    super(message, cause);
    this.errors = errors;
    this.causeHttpStatus = null;
  }

  /**
   * Creates a new {@link IntegrationException} with error message and error cause.
   *
   * @param message - error message as {@link String} object
   * @param cause - error cause as {@link Throwable} object
   */
  public IntegrationException(String message, Throwable cause) {
    super(message, cause);
    this.errors = emptyList();
    this.causeHttpStatus = null;
  }

  /**
   * Creates a new {@link IntegrationException} with corresponding error message.
   *
   * @param message - error message as {@link String} object
   * @param errors - {@link List} with error {@link Parameter} objects
   */
  public IntegrationException(String message, List<Parameter> errors, Integer causeHttpStatus) {
    super(message);
    this.errors = errors;
    this.causeHttpStatus = causeHttpStatus;
  }
}
