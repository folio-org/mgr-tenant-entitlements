package org.folio.entitlement.utils;

import static org.apache.commons.lang3.exception.ExceptionUtils.getStackTrace;
import static org.apache.logging.log4j.core.config.Property.EMPTY_ARRAY;
import static org.apache.logging.log4j.core.layout.PatternLayout.createDefaultLayout;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import lombok.experimental.UtilityClass;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.AbstractAppender;

@UtilityClass
public class LogTestUtil {

  public static final String APPENDER_NAME = "log4j2_capture_appender";

  public static List<String> captureLog4J2Logs() {
    var logLines = Collections.synchronizedList(new ArrayList<String>());
    var listAppender = createAppender(logLines);
    listAppender.start();
    var ctx = (LoggerContext) LogManager.getContext(false);
    var config = ctx.getConfiguration();
    // Add to every named logger config (including those with additivity=false) and root
    config.getLoggers().values().forEach(lc -> lc.addAppender(listAppender, null, null));
    config.getRootLogger().addAppender(listAppender, null, null);
    ctx.updateLoggers();
    return logLines;
  }

  public static void stopCaptureLog4J2Logs() {
    var ctx = (LoggerContext) LogManager.getContext(false);
    var config = ctx.getConfiguration();
    config.getLoggers().values().forEach(lc -> lc.removeAppender(APPENDER_NAME));
    config.getRootLogger().removeAppender(APPENDER_NAME);
    ctx.updateLoggers();
  }

  private static AbstractAppender createAppender(Collection<String> logLines) {
    return new AbstractAppender(APPENDER_NAME, null, createDefaultLayout(), false, EMPTY_ARRAY) {
      @Override
      public void append(LogEvent event) {
        logLines.add(event.getMessage().getFormattedMessage());
        if (event.getThrown() != null) {
          logLines.add(getStackTrace(event.getThrown()));
        }
      }
    };
  }
}
