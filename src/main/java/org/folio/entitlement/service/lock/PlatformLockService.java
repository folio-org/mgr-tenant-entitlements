package org.folio.entitlement.service.lock;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.function.Supplier;
import javax.sql.DataSource;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.entitlement.configuration.PlatformLockConfigurationProperties;
import org.folio.entitlement.exception.PlatformOperationInProgressException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Deployment-wide lock that excludes tenant-scoped entitlement operations while a platform-scoped operation runs.
 *
 * <p>The platform operation holds a transaction-level Postgres advisory lock for the duration of a callback, on one
 * connection of the main pool. Postgres releases the lock on commit, rollback or connection loss, so it can never stay
 * on a pooled connection. Tenant operations never hold the lock: they only probe it with a shared lock that lives for
 * one statement, so tenant operations never contend with each other. Every method is non-blocking - a contended request
 * fails immediately instead of waiting.</p>
 *
 * <p>{@link #runExclusively(Supplier)} uses its own connection. {@link #assertNotHeld()} uses the current Spring
 * transaction's connection, and its shared lock lasts until that transaction ends. Callers of
 * {@code assertNotHeld()}, such as {@code EntitlementService.performRequest}, must therefore not be
 * {@code @Transactional}.</p>
 */
@Log4j2
@Service
@RequiredArgsConstructor
public class PlatformLockService {

  /**
   * Namespace, the first int of the advisory lock key: {@code 0x4D475445} is ASCII "MGTE" (manager tenant
   * entitlements). A module-specific value keeps the lock from colliding with advisory locks taken by other code in
   * the same database.
   */
  static final int LOCK_NAMESPACE = 0x4D475445;
  /**
   * Lock name inside the namespace, the second int of the key: {@code 0x504C4154} is ASCII "PLAT" (platform). Both
   * values are constant, so all module instances share one deployment-wide lock.
   */
  static final int LOCK_KEY = 0x504C4154;

  private static final String TRY_EXCLUSIVE_LOCK_SQL = "SELECT pg_try_advisory_xact_lock(?, ?)";
  private static final String TRY_SHARED_LOCK_SQL = "SELECT pg_try_advisory_xact_lock_shared(?, ?)";

  private final DataSource dataSource;
  private final JdbcTemplate jdbcTemplate;
  private final PlatformLockConfigurationProperties properties;

  /**
   * Runs the operation while holding the exclusive platform lock. Never waits.
   *
   * @param operation - the platform operation to run
   * @param <T> - result type
   * @return the operation result
   * @throws PlatformOperationInProgressException if another platform operation holds the lock
   */
  public <T> T runExclusively(Supplier<T> operation) {
    try (var connection = dataSource.getConnection()) {
      // false = autocommit off, so the connection starts a transaction that stays open until we end it. The lock is
      // transaction-level: with autocommit on (true), the lock statement would commit as soon as it finishes and
      // Postgres would release the lock before the operation even starts.
      connection.setAutoCommit(false);
      try {
        if (!tryLock(connection)) {
          throw platformOperationInProgress();
        }
        log.debug("Platform lock acquired");
        return operation.get();
      } finally {
        endTransaction(connection);
      }
    } catch (SQLException exception) {
      throw new DataAccessResourceFailureException("Failed to acquire platform lock", exception);
    }
  }

  /**
   * Checks whether a platform operation currently holds the lock. Takes a shared lock that is released at once.
   *
   * @return {@code true} if the platform lock is held
   */
  public boolean isHeld() {
    var free = jdbcTemplate.queryForObject(TRY_SHARED_LOCK_SQL, Boolean.class, LOCK_NAMESPACE, LOCK_KEY);
    return !Boolean.TRUE.equals(free);
  }

  /**
   * Fails fast if a platform operation holds the lock. Called by every tenant-scoped entitlement operation before it
   * starts, so the rejected request creates no flow and changes no tenant state.
   *
   * @throws PlatformOperationInProgressException if the platform lock is held
   */
  public void assertNotHeld() {
    if (isHeld()) {
      throw platformOperationInProgress();
    }
  }

  private PlatformOperationInProgressException platformOperationInProgress() {
    return new PlatformOperationInProgressException(
      "A platform operation is in progress, retry later", properties.getRetryAfterSeconds());
  }

  private static boolean tryLock(Connection connection) throws SQLException {
    try (var statement = connection.prepareStatement(TRY_EXCLUSIVE_LOCK_SQL)) {
      statement.setInt(1, LOCK_NAMESPACE);
      statement.setInt(2, LOCK_KEY);
      try (var resultSet = statement.executeQuery()) {
        return resultSet.next() && resultSet.getBoolean(1);
      }
    }
  }

  /**
   * Ends the lock transaction, which releases the lock, and restores autocommit for the pool. A failure is logged and
   * not thrown, so it cannot hide the exception of the operation; a closed connection loses the lock anyway.
   */
  private void endTransaction(Connection connection) {
    try {
      connection.rollback();
      connection.setAutoCommit(true);
      log.debug("Platform lock released");
    } catch (SQLException exception) {
      log.error("Failed to end platform lock transaction cleanly", exception);
    }
  }
}
