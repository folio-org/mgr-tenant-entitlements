package org.folio.entitlement.exception;

import static java.util.Collections.singletonList;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.folio.common.domain.model.error.ErrorCode.VALIDATION_ERROR;

import java.io.Serial;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import org.folio.common.domain.model.error.ErrorCode;
import org.folio.common.domain.model.error.Parameter;

@Getter
public class RequestValidationException extends RuntimeException {

  @Serial private static final long serialVersionUID = 3694050167125346985L;

  private final transient List<Parameter> errorParameters;
  private final ErrorCode errorCode;

  /**
   * Creates {@link RequestValidationException} object for given message, key and value.
   *
   * @param message - validation error message
   * @param key - validation key as field or parameter name
   * @param value - invalid parameter value
   */
  public RequestValidationException(String message, String key, String value) {
    super(message);

    this.errorCode = VALIDATION_ERROR;
    this.errorParameters = singletonList(new Parameter().key(key).value(value));
  }

  /**
   * Creates {@link RequestValidationException} object for given message, key and value.
   *
   * @param message - validation error message
   * @param errorParameters - error parameters as {@link List} of {@link Parameter} objects
   */
  public RequestValidationException(String message, List<Parameter> errorParameters) {
    super(message);

    this.errorCode = VALIDATION_ERROR;
    this.errorParameters = errorParameters;
  }

  public RequestValidationException(String message, Params errorParameters) {
    super(message);

    this.errorCode = VALIDATION_ERROR;
    this.errorParameters = errorParameters.toList();
  }

  public static final class Params {

    private final List<Parameter> params = new ArrayList<>();

    public Params add(String key, String value) {
      if (isBlank(key)) {
        throw new IllegalArgumentException("Parameter's key is blank");
      }

      params.add(new Parameter().key(key).value(value));

      return this;
    }

    public List<Parameter> toList() {
      return new ArrayList<>(params);
    }
  }
}
