package org.folio.entitlement.service.lock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.function.Supplier;
import javax.sql.DataSource;
import org.folio.entitlement.configuration.PlatformLockConfigurationProperties;
import org.folio.entitlement.exception.PlatformOperationInProgressException;
import org.folio.test.types.UnitTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;

@UnitTest
@ExtendWith(MockitoExtension.class)
class PlatformLockServiceTest {

  private static final String LOCK_SQL = "SELECT pg_try_advisory_xact_lock(?, ?)";

  @Mock private DataSource dataSource;
  @Mock private JdbcTemplate jdbcTemplate;
  @Mock private Connection connection;
  @Mock private PreparedStatement statement;
  @Mock private ResultSet resultSet;

  @Test
  void runExclusively_positive_returnsResultAndEndsTransaction() throws SQLException {
    stubLockAttempt(true);

    var result = service().runExclusively(() -> "result");

    assertThat(result).isEqualTo("result");
    var order = inOrder(connection);
    order.verify(connection).setAutoCommit(false);
    order.verify(connection).rollback();
    order.verify(connection).setAutoCommit(true);
    order.verify(connection).close();
  }

  @Test
  void runExclusively_negative_lockNotAcquiredSkipsOperation() throws SQLException {
    stubLockAttempt(false);
    var called = new boolean[1];
    var service = service();
    Supplier<String> operation = () -> {
      called[0] = true;
      return "result";
    };

    assertThatThrownBy(() -> service.runExclusively(operation))
      .isInstanceOf(PlatformOperationInProgressException.class)
      .hasMessage("A platform operation is in progress, retry later");

    assertThat(called[0]).isFalse();
    verify(connection).rollback();
    verify(connection).close();
  }

  @Test
  void runExclusively_negative_connectionFailureIsReported() throws SQLException {
    var cause = new SQLException("no connection");
    when(dataSource.getConnection()).thenThrow(cause);
    var service = service();

    assertThatThrownBy(() -> service.runExclusively(() -> "result"))
      .isInstanceOf(DataAccessResourceFailureException.class)
      .hasCause(cause);
  }

  @Test
  void runExclusively_negative_lockStatementFailureIsReportedAndConnectionClosed() throws SQLException {
    var cause = new SQLException("statement failed");
    when(dataSource.getConnection()).thenReturn(connection);
    when(connection.prepareStatement(LOCK_SQL)).thenThrow(cause);
    var service = service();

    assertThatThrownBy(() -> service.runExclusively(() -> "result"))
      .isInstanceOf(DataAccessResourceFailureException.class)
      .hasCause(cause);

    verify(connection).rollback();
    verify(connection).close();
  }

  @Test
  void runExclusively_negative_rollbackFailureDoesNotHideOperationException() throws SQLException {
    stubLockAttempt(true);
    doThrow(new SQLException("rollback failed")).when(connection).rollback();
    var failure = new IllegalStateException("operation failed");
    var service = service();
    Supplier<String> operation = () -> {
      throw failure;
    };

    assertThatThrownBy(() -> service.runExclusively(operation)).isSameAs(failure);

    verify(connection, never()).setAutoCommit(true);
    verify(connection).close();
  }

  @Test
  void assertNotHeld_negative_throwsWhenProbeFindsLockTaken() {
    when(jdbcTemplate.queryForObject("SELECT pg_try_advisory_xact_lock_shared(?, ?)", Boolean.class,
      PlatformLockService.LOCK_NAMESPACE, PlatformLockService.LOCK_KEY)).thenReturn(false);

    assertThatThrownBy(service()::assertNotHeld)
      .isInstanceOf(PlatformOperationInProgressException.class);
  }

  @Test
  void isHeld_positive_falseWhenProbeSucceeds() {
    when(jdbcTemplate.queryForObject("SELECT pg_try_advisory_xact_lock_shared(?, ?)", Boolean.class,
      PlatformLockService.LOCK_NAMESPACE, PlatformLockService.LOCK_KEY)).thenReturn(true);

    assertThat(service().isHeld()).isFalse();
  }

  private final PlatformLockConfigurationProperties properties = new PlatformLockConfigurationProperties();

  private PlatformLockService service() {
    return new PlatformLockService(dataSource, jdbcTemplate, properties);
  }

  private void stubLockAttempt(boolean acquired) throws SQLException {
    when(dataSource.getConnection()).thenReturn(connection);
    when(connection.prepareStatement(LOCK_SQL)).thenReturn(statement);
    when(statement.executeQuery()).thenReturn(resultSet);
    when(resultSet.next()).thenReturn(true);
    when(resultSet.getBoolean(1)).thenReturn(acquired);
  }
}
