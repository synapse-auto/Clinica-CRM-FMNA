package com.synapse.clinicafemina.service;

import com.synapse.clinicafemina.integration.external.ExternalProviderType;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import javax.sql.DataSource;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class ExternalSyncLockService {

    private static final String TRY_LOCK_SQL =
            "SELECT pg_try_advisory_lock(hashtext(?), hashtext(?))";
    private static final String UNLOCK_SQL =
            "SELECT pg_advisory_unlock(hashtext(?), hashtext(?))";

    private final DataSource dataSource;

    public ExternalSyncLockService(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /**
     * The session-level PostgreSQL lock is held by a dedicated connection until
     * the whole orchestration, including history finalization, completes.
     * Non-PostgreSQL test databases do not expose advisory locks and run without
     * the distributed guard; production uses PostgreSQL as required.
     */
    public Optional<LockHandle> tryAcquire(Long clinicId, ExternalProviderType providerType) {
        Connection connection = null;
        try {
            connection = dataSource.getConnection();
            if (!"PostgreSQL".equalsIgnoreCase(connection.getMetaData().getDatabaseProductName())) {
                connection.close();
                return Optional.of(LockHandle.noop());
            }
            try (PreparedStatement statement = connection.prepareStatement(TRY_LOCK_SQL)) {
                statement.setString(1, "clinic-external-sync");
                statement.setString(2, clinicId + ":" + providerType.name());
                try (ResultSet resultSet = statement.executeQuery()) {
                    if (!resultSet.next()) {
                        throw new IllegalStateException("Resposta invalida ao adquirir lock de sincronizacao");
                    }
                    if (!resultSet.getBoolean(1)) {
                        connection.close();
                        return Optional.empty();
                    }
                    return Optional.of(new LockHandle(
                            connection, "clinic-external-sync", clinicId + ":" + providerType.name()));
                }
            }
        } catch (Exception error) {
            closeQuietly(connection);
            throw new IllegalStateException("Falha ao adquirir lock de sincronizacao externa", error);
        }
    }

    private void closeQuietly(Connection connection) {
        if (connection == null) {
            return;
        }
        try {
            connection.close();
        } catch (Exception ignored) {
            // Preserve the original acquisition error without logging connection data.
        }
    }

    public static class LockHandle implements AutoCloseable {

        private final Connection connection;
        private final String scope;
        private final String key;

        private LockHandle(Connection connection, String scope, String key) {
            this.connection = connection;
            this.scope = scope;
            this.key = key;
        }

        private static LockHandle noop() {
            return new LockHandle(null, null, null);
        }

        @Override
        public void close() {
            if (connection == null) {
                return;
            }
            try (PreparedStatement statement = connection.prepareStatement(UNLOCK_SQL)) {
                statement.setString(1, scope);
                statement.setString(2, key);
                statement.executeQuery();
            } catch (Exception ignored) {
                // Closing the connection also releases a session advisory lock.
            } finally {
                try {
                    connection.close();
                } catch (Exception ignored) {
                    // The pool/driver owns the final cleanup if close already happened.
                }
            }
        }
    }
}
