package iuh.fit.se.contractservice.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Coordinates scheduled jobs across multiple replicas using PostgreSQL advisory locks.
 */
@Component
@Slf4j
public class PostgresAdvisoryLock {

    private final DataSource dataSource;
    @Nullable
    private final TransactionTemplate transactionTemplate;

    public PostgresAdvisoryLock(DataSource dataSource) {
        this(dataSource, null);
    }

    @Autowired
    public PostgresAdvisoryLock(DataSource dataSource, @Nullable TransactionTemplate transactionTemplate) {
        this.dataSource = dataSource;
        this.transactionTemplate = transactionTemplate;
    }

    /**
     * Acquires a transaction-scoped advisory lock ({@code pg_try_advisory_xact_lock}) on the same
     * JDBC connection used by the transaction, avoiding extra Hikari pool connections.
     */
    public boolean runInTransactionWithLock(long lockKey, Runnable task) {
        if (transactionTemplate == null) {
            return runWithLock(lockKey, task);
        }
        Boolean executed = transactionTemplate.execute(status -> {
            if (!tryAcquireXactLock(lockKey)) {
                return Boolean.FALSE;
            }
            task.run();
            return Boolean.TRUE;
        });
        return Boolean.TRUE.equals(executed);
    }

    public boolean runWithLock(long lockKey, Runnable task) {
        if (dataSource == null) {
            task.run();
            return true;
        }
        try (Connection conn = dataSource.getConnection()) {
            if (!isPostgres(conn)) {
                task.run();
                return true;
            }
            boolean acquired = false;
            try (PreparedStatement tryLock = conn.prepareStatement("SELECT pg_try_advisory_lock(?)")) {
                tryLock.setLong(1, lockKey);
                try (ResultSet rs = tryLock.executeQuery()) {
                    acquired = rs.next() && rs.getBoolean(1);
                }
            }
            if (!acquired) {
                return false;
            }
            try {
                task.run();
                return true;
            } finally {
                try (PreparedStatement unlock = conn.prepareStatement("SELECT pg_advisory_unlock(?)")) {
                    unlock.setLong(1, lockKey);
                    unlock.executeQuery();
                } catch (SQLException ex) {
                    log.warn("Failed to release PostgreSQL advisory lock {}: {}", lockKey, ex.getMessage());
                }
            }
        } catch (SQLException ex) {
            log.warn("Could not acquire PostgreSQL advisory lock {}: {}", lockKey, ex.getMessage());
            return false;
        }
    }

    private boolean tryAcquireXactLock(long lockKey) {
        if (dataSource == null) {
            return true;
        }
        Connection conn = DataSourceUtils.getConnection(dataSource);
        try {
            if (!isPostgres(conn)) {
                return true;
            }
            try (PreparedStatement stmt = conn.prepareStatement("SELECT pg_try_advisory_xact_lock(?)")) {
                stmt.setLong(1, lockKey);
                try (ResultSet rs = stmt.executeQuery()) {
                    return rs.next() && rs.getBoolean(1);
                }
            }
        } catch (SQLException ex) {
            log.warn("Could not acquire PostgreSQL transaction advisory lock {}: {}", lockKey, ex.getMessage());
            return false;
        } finally {
            DataSourceUtils.releaseConnection(conn, dataSource);
        }
    }

    private boolean isPostgres(Connection conn) throws SQLException {
        String product = conn.getMetaData() != null ? conn.getMetaData().getDatabaseProductName() : "";
        return product != null && product.toLowerCase().contains("postgresql");
    }
}
