package org.folio.entitlement.service.lock;

import com.zaxxer.hikari.HikariDataSource;
import jakarta.annotation.PreDestroy;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.log4j.Log4j2;
import org.folio.entitlement.configuration.PlatformLockConfigurationProperties;
import org.folio.entitlement.exception.PlatformOperationInProgressException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Deployment-wide lock that excludes tenant-scoped entitlement operations while a platform-scoped operation runs.
 *
 * <p>The platform operation holds a session-level Postgres advisory lock on a dedicated connection for its whole
 * duration. Tenant operations never acquire the lock: they only check whether it is held, so tenant operations never
 * contend with each other. Every method is non-blocking - a contended request fails immediately instead of waiting.</p>
 *
 * <p>A session advisory lock lives on the physical backend connection, so the holding connection must be unlocked
 * explicitly before it is returned to the pool, or evicted if that fails.</p>
 */
@Log4j2
@Service
public class PlatformLockService {

  /**
   * Namespace, the first int of the advisory lock key: {@code 0x4D475445} is ASCII "MGTE" (manager tenant
   * entitlements). A module-specific value keeps the lock from colliding with advisory locks taken by other code in
   * the same database, and is easy to spot in {@code pg_locks}.
   */
  static final int LOCK_NAMESPACE = 0x4D475445;
  /**
   * Lock name inside the namespace, the second int of the key: {@code 0x504C4154} is ASCII "PLAT" (platform). Both
   * values are constant, so all module instances share one deployment-wide lock.
   */
  static final int LOCK_KEY = 0x504C4154;

  private static final String TRY_LOCK_SQL = "SELECT pg_try_advisory_lock(?, ?)";
  private static final String UNLOCK_SQL = "SELECT pg_advisory_unlock(?, ?)";
  /**
   * Reads the lock table without acquiring anything. In {@code pg_locks}, {@code objsubid = 2} marks a key made of
   * two integers (the first in {@code classid}, the second in {@code objid}); {@code objsubid = 1} is a single bigint.
   */
  private static final String IS_HELD_SQL = """
    SELECT EXISTS (
      SELECT 1 FROM pg_locks
      WHERE locktype = 'advisory'
        AND database = (SELECT oid FROM pg_database WHERE datname = current_database())
        AND classid = ? AND objid = ? AND objsubid = 2
        AND granted)
    """;

  private final JdbcTemplate jdbcTemplate;
  private final PlatformLockConfigurationProperties properties;
  private final HikariDataSource lockDataSource;
  private final Map<UUID, Connection> holders = new ConcurrentHashMap<>();

  public PlatformLockService(JdbcTemplate jdbcTemplate, PlatformLockConfigurationProperties properties,
    @Value("${spring.datasource.url}") String url,
    @Value("${spring.datasource.username}") String username,
    @Value("${spring.datasource.password}") String password) {
    this.jdbcTemplate = jdbcTemplate;
    this.properties = properties;
    // A dedicated pool, deliberately not a Spring DataSource bean: a second DataSource bean would make Boot's
    // DataSourceAutoConfiguration back off and move JPA onto this pool.
    this.lockDataSource = new HikariDataSource();
    lockDataSource.setJdbcUrl(url);
    lockDataSource.setUsername(username);
    lockDataSource.setPassword(password);
    lockDataSource.setPoolName("platform-lock");
    lockDataSource.setMinimumIdle(0);
    lockDataSource.setMaximumPoolSize(2);
  }

  @PreDestroy
  void shutdown() {
    holders.keySet().forEach(this::release);
    lockDataSource.close();
  }

  /**
   * Acquires the platform lock for a platform operation. Never waits.
   *
   * @param holder - identifier of the holder that owns the lock
   * @throws PlatformOperationInProgressException if another platform operation holds the lock
   */
  public void acquire(UUID holder) {
    var connection = openLockConnection();
    boolean locked;
    try {
      locked = tryLock(connection);
    } catch (SQLException exception) {
      // the lock may have been taken server-side before the failure, so evict rather than return the connection
      lockDataSource.evictConnection(connection);
      throw new DataAccessResourceFailureException("Failed to acquire platform lock", exception);
    }

    if (!locked) {
      returnToPool(connection);
      throw platformOperationInProgress();
    }

    holders.put(holder, connection);
    log.info("Platform lock acquired [holder: {}]", holder);
  }

  /**
   * Releases the platform lock held by the holder. Idempotent: a holder that holds no lock is a no-op.
   *
   * @param holder - identifier of the holder that owns the lock
   */
  public void release(UUID holder) {
    var connection = holders.remove(holder);
    if (connection == null) {
      return;
    }

    try {
      var unlocked = unlock(connection);
      if (!unlocked) {
        log.warn("Platform lock was not held by its connection on release [holder: {}]", holder);
      }
      connection.close();
      log.info("Platform lock released [holder: {}]", holder);
    } catch (SQLException exception) {
      log.error("Failed to unlock platform lock cleanly, evicting its connection [holder: {}]", holder, exception);
      lockDataSource.evictConnection(connection);
    }
  }

  /**
   * Checks whether a platform operation currently holds the lock. Reads {@code pg_locks} and acquires nothing.
   *
   * @return {@code true} if the platform lock is held
   */
  public boolean isHeld() {
    return Boolean.TRUE.equals(jdbcTemplate.queryForObject(IS_HELD_SQL, Boolean.class, LOCK_NAMESPACE, LOCK_KEY));
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

  private Connection openLockConnection() {
    try {
      return lockDataSource.getConnection();
    } catch (SQLException exception) {
      throw new DataAccessResourceFailureException("Failed to open platform lock connection", exception);
    }
  }

  private static boolean tryLock(Connection connection) throws SQLException {
    try (var statement = connection.prepareStatement(TRY_LOCK_SQL)) {
      statement.setInt(1, LOCK_NAMESPACE);
      statement.setInt(2, LOCK_KEY);
      try (var resultSet = statement.executeQuery()) {
        return resultSet.next() && resultSet.getBoolean(1);
      }
    }
  }

  private static boolean unlock(Connection connection) throws SQLException {
    try (var statement = connection.prepareStatement(UNLOCK_SQL)) {
      statement.setInt(1, LOCK_NAMESPACE);
      statement.setInt(2, LOCK_KEY);
      try (var resultSet = statement.executeQuery()) {
        return resultSet.next() && resultSet.getBoolean(1);
      }
    }
  }

  private void returnToPool(Connection connection) {
    try {
      connection.close();
    } catch (SQLException exception) {
      log.warn("Failed to return platform lock connection to the pool, evicting it", exception);
      lockDataSource.evictConnection(connection);
    }
  }
}
