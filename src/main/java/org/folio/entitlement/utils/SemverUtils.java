package org.folio.entitlement.utils;

import static org.apache.commons.lang3.RegExUtils.removeAll;
import static org.apache.commons.lang3.StringUtils.chop;
import static org.apache.commons.lang3.StringUtils.isEmpty;
import static org.apache.commons.lang3.StringUtils.removeStart;
import static org.folio.common.utils.CollectionUtils.toStream;

import java.util.Collection;
import java.util.List;
import java.util.regex.Pattern;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.semver4j.Semver;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class SemverUtils {

  private static final Pattern VERSION_PATTERN = Pattern.compile(
    "(0|[1-9]\\d*)\\.(0|[1-9]\\d*)\\.(0|[1-9]\\d*)"
      + "(?:-((?:0|[1-9]\\d*|\\d*[a-zA-Z-][0-9a-zA-Z-]*)" //NOSONAR
      + "(?:\\.(?:0|[1-9]\\d*|\\d*[a-zA-Z-][0-9a-zA-Z-]*))*))?" //NOSONAR
      + "(?:\\+([0-9a-zA-Z-]+(?:\\.[0-9a-zA-Z-]+)*))?$");
  private static final String VERSION_DELIMITER = "-";
  private static final String ERROR_MSG = "Application id cannot be empty";

  /**
   * Returns application version from application id.
   *
   * @param applicationId - application id
   * @return application's version
   */
  public static String getVersion(String applicationId) {
    if (isEmpty(applicationId)) {
      throw new IllegalArgumentException(ERROR_MSG);
    }
    return removeStart(applicationId, getName(applicationId) + VERSION_DELIMITER);
  }

  /**
   * Returns application name from application id.
   *
   * @param applicationId - application id
   * @return application's name
   */
  public static String getName(String applicationId) {
    if (isEmpty(applicationId)) {
      throw new IllegalArgumentException(ERROR_MSG);
    }
    return chop(removeAll(applicationId, VERSION_PATTERN));
  }

  /**
   * Returns application names from application ids.
   *
   * @param ids - application ids list
   * @return application's name
   */
  public static List<String> getNames(Collection<String> ids) {
    return toStream(ids)
      .map(SemverUtils::getName)
      .distinct()
      .toList();
  }

  /**
   * Check if the version satisfies a range or version.
   *
   * @param version version
   * @param rangeOrVersion range or version
   * @return {@code true} if the version satisfies the range or version, {@code false} otherwise
   */
  public static boolean satisfies(String version, String rangeOrVersion) {
    var semver = Semver.parse(version);

    if (semver == null) {
      throw new IllegalArgumentException("Invalid semantic version: " + version);
    }

    return semver.satisfies(rangeOrVersion);
  }

  /**
   * Check if the version of application satisfies a range or version.
   *
   * @param applicationId application id which includes version under the test
   * @param rangeOrVersion range or version
   * @return {@code true} if the version satisfies the range or version, {@code false} otherwise
   */
  public static boolean applicationSatisfies(String applicationId, String rangeOrVersion) {
    return satisfies(getVersion(applicationId), rangeOrVersion);
  }
}
