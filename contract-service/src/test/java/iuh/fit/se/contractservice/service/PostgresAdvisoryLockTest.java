package iuh.fit.se.contractservice.service;

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
    void runsTaskOnlyWhenPostgresAdvisoryLockIsAcquired() throws Exception {
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.getMetaData()).thenReturn(metaData);
        when(metaData.getDatabaseProductName()).thenReturn("PostgreSQL");
        when(connection.prepareStatement("SELECT pg_try_advisory_lock(?)")).thenReturn(tryLockStmt);
        when(connection.prepareStatement("SELECT pg_advisory_unlock(?)")).thenReturn(unlockStmt);
        when(tryLockStmt.executeQuery()).thenReturn(tryLockRs);
        when(tryLockRs.next()).thenReturn(true);
        when(tryLockRs.getBoolean(1)).thenReturn(true, false);
        when(unlockStmt.executeQuery()).thenReturn(unlockRs);

        PostgresAdvisoryLock lock = new PostgresAdvisoryLock(dataSource);
        AtomicInteger runs = new AtomicInteger();

        assertThat(lock.runWithLock(2001L, runs::incrementAndGet)).isTrue();
        assertThat(lock.runWithLock(2001L, runs::incrementAndGet)).isFalse();
        assertThat(runs.get()).isEqualTo(1);
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

        assertThat(lock.runInTransactionWithLock(4200002L, runs::incrementAndGet)).isTrue();
        assertThat(lock.runInTransactionWithLock(4200002L, runs::incrementAndGet)).isFalse();
        assertThat(runs.get()).isEqualTo(1);
        verify(connection, never()).prepareStatement("SELECT pg_advisory_unlock(?)");
    }
}
