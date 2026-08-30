package com.mobily.qalite.targetdb;

import java.sql.Connection;
import java.sql.SQLException;

import com.mobily.qalite.security.SecretCipherService;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class TargetDatabaseConnectionService {

    private final JdbcTemplate jdbcTemplate;
    private final SecretCipherService secretCipherService;

    public TargetDatabaseConnectionService(JdbcTemplate jdbcTemplate, SecretCipherService secretCipherService) {
        this.jdbcTemplate = jdbcTemplate;
        this.secretCipherService = secretCipherService;
    }

    public TargetDatabaseConnection getEnvironmentConnection(long environmentId) {
        return jdbcTemplate.query("""
                select env_id, name, db_type, jdbc_url, db_username, db_password_enc
                from environments
                where env_id = ?
                """, resultSet -> {
            if (!resultSet.next()) {
                throw new IllegalArgumentException("Environment does not exist");
            }

            TargetDatabaseType databaseType = TargetDatabaseType.from(resultSet.getString("db_type"));
            return new TargetDatabaseConnection(
                    resultSet.getLong("env_id"),
                    resultSet.getString("name"),
                    databaseType,
                    resultSet.getString("jdbc_url"),
                    resultSet.getString("db_username"),
                    secretCipherService.decrypt(resultSet.getString("db_password_enc"))
            );
        }, environmentId);
    }

    /**
     * Opens an isolated, short-lived connection pool for the environment and immediately validates
     * and closes it. Independent of the app's own datasource and of any other environment's pool -
     * used by the admin "Test Connection" action to confirm credentials/reachability without side effects.
     */
    public boolean testConnection(long environmentId) {
        TargetDatabaseConnection connection = getEnvironmentConnection(environmentId);

        try (HikariDataSource dataSource = createDataSource(connection);
             Connection sqlConnection = dataSource.getConnection()) {
            return sqlConnection.isValid(5);
        } catch (SQLException | RuntimeException exception) {
            return false;
        }
    }

    public HikariDataSource createDataSource(TargetDatabaseConnection connection) {
        HikariConfig config = new HikariConfig();
        config.setPoolName("qalite-target-" + connection.environmentId());
        config.setJdbcUrl(connection.jdbcUrl());
        config.setUsername(connection.username());
        config.setPassword(connection.passwordSecret());
        config.setDriverClassName(connection.databaseType().getDriverClassName());
        config.setMinimumIdle(0);
        config.setMaximumPoolSize(2);
        config.setConnectionTimeout(10_000);

        if (connection.databaseType().requiresDatabaseProperty()) {
            config.addDataSourceProperty(
                    "database",
                    connection.databaseType().databaseNameFromJdbcUrl(connection.jdbcUrl())
            );
        }

        return new HikariDataSource(config);
    }

    public record TargetDatabaseConnection(
            long environmentId,
            String environmentName,
            TargetDatabaseType databaseType,
            String jdbcUrl,
            String username,
            String passwordSecret
    ) {
    }
}
