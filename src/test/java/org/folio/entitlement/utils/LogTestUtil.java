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
    var rootLogger = ((LoggerContext) LogManager.getContext(false)).getRootLogger();
    rootLogger.removeAppender(listAppender);
    rootLogger.addAppender(listAppender);

    return logLines;
  }

  public static void stopCaptureLog4J2Logs() {
    var rootLogger = ((LoggerContext) LogManager.getContext(false)).getRootLogger();
    rootLogger.removeAppender(createAppender(new ArrayList<>()));
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
