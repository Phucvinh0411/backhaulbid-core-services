package iuh.fit.se.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PostgresAdvisoryLockTest {

    @Mock private DataSource dataSource;
    @Mock private Connection connection;
    @Mock private DatabaseMetaData metaData;
    @Mock private PreparedStatement tryLockStmt;
    @Mock private PreparedStatement unlockStmt;
    @Mock private ResultSet tryLockRs;
    @Mock private ResultSet unlockRs;
    @Mock private TransactionTemplate transactionTemplate;

    @Test
    void runsTaskAndUnlocksWhenPostgresAdvisoryLockAcquired() throws Exception {
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.getMetaData()).thenReturn(metaData);
        when(metaData.getDatabaseProductName()).thenReturn("PostgreSQL");
        when(connection.prepareStatement("SELECT pg_try_advisory_lock(?)")).thenReturn(tryLockStmt);
        when(connection.prepareStatement("SELECT pg_advisory_unlock(?)")).thenReturn(unlockStmt);
        when(tryLockStmt.executeQuery()).thenReturn(tryLockRs);
        when(tryLockRs.next()).thenReturn(true);
        when(tryLockRs.getBoolean(1)).thenReturn(true);
        when(unlockStmt.executeQuery()).thenReturn(unlockRs);

        PostgresAdvisoryLock lock = new PostgresAdvisoryLock(dataSource);
        AtomicInteger runs = new AtomicInteger();

        boolean executed = lock.runWithLock(1001L, runs::incrementAndGet);

        assertThat(executed).isTrue();
        assertThat(runs.get()).isEqualTo(1);
        verify(tryLockStmt).setLong(1, 1001L);
        verify(unlockStmt).setLong(1, 1001L);
        verify(connection).close();
    }

    @Test
    void skipsTaskWhenAnotherPodHoldsPostgresAdvisoryLock() throws Exception {
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.getMetaData()).thenReturn(metaData);
        when(metaData.getDatabaseProductName()).thenReturn("PostgreSQL");
        when(connection.prepareStatement("SELECT pg_try_advisory_lock(?)")).thenReturn(tryLockStmt);
        when(tryLockStmt.executeQuery()).thenReturn(tryLockRs);
        when(tryLockRs.next()).thenReturn(true);
        when(tryLockRs.getBoolean(1)).thenReturn(false);

        PostgresAdvisoryLock lock = new PostgresAdvisoryLock(dataSource);
        AtomicInteger runs = new AtomicInteger();

        boolean executed = lock.runWithLock(1001L, runs::incrementAndGet);

        assertThat(executed).isFalse();
        assertThat(runs.get()).isZero();
        verify(connection, never()).prepareStatement("SELECT pg_advisory_unlock(?)");
        verify(connection).close();
    }

    @Test
    void fallsBackToRunningDirectlyOnH2TestDatabase() throws Exception {
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.getMetaData()).thenReturn(metaData);
        when(metaData.getDatabaseProductName()).thenReturn("H2");

        PostgresAdvisoryLock lock = new PostgresAdvisoryLock(dataSource);
        AtomicInteger runs = new AtomicInteger();

        boolean executed = lock.runWithLock(1001L, runs::incrementAndGet);

        assertThat(executed).isTrue();
        assertThat(runs.get()).isEqualTo(1);
        verify(connection, never()).prepareStatement(anyString());
    }

    @Test
    void usesSingleConnectionXactLockInsideTransaction() throws Exception {
        when(transactionTemplate.execute(any())).thenAnswer(inv -> {
            TransactionCallback<Boolean> cb = inv.getArgument(0);
            return cb.doInTransaction(null);
        });
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.getMetaData()).thenReturn(metaData);
        when(metaData.getDatabaseProductName()).thenReturn("PostgreSQL");
        when(connection.prepareStatement("SELECT pg_try_advisory_xact_lock(?)")).thenReturn(tryLockStmt);
        when(tryLockStmt.executeQuery()).thenReturn(tryLockRs);
        when(tryLockRs.next()).thenReturn(true);
        when(tryLockRs.getBoolean(1)).thenReturn(true, false);

        PostgresAdvisoryLock lock = new PostgresAdvisoryLock(dataSource, transactionTemplate);
        AtomicInteger runs = new AtomicInteger();

        assertThat(lock.runInTransactionWithLock(4100001L, runs::incrementAndGet)).isTrue();
        assertThat(lock.runInTransactionWithLock(4100001L, runs::incrementAndGet)).isFalse();
        assertThat(runs.get()).isEqualTo(1);
        verify(connection, never()).prepareStatement("SELECT pg_advisory_unlock(?)");
    }
}
