package com.mobily.qalite.execution;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import com.mobily.qalite.targetdb.TargetDatabaseConnectionService;
import com.mobily.qalite.targetdb.TargetDatabaseConnectionService.TargetDatabaseConnection;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

@Service
public class ExecutionService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ExecutionService.class);
    private static final int MAX_ROWS = 500;
    private static final int QUERY_TIMEOUT_SECONDS = 15;
    private static final int MAX_ERROR_MESSAGE_LENGTH = 2000;

    private final JdbcTemplate jdbcTemplate;
    private final TargetDatabaseConnectionService targetDatabaseConnectionService;

    public ExecutionService(JdbcTemplate jdbcTemplate, TargetDatabaseConnectionService targetDatabaseConnectionService) {
        this.jdbcTemplate = jdbcTemplate;
        this.targetDatabaseConnectionService = targetDatabaseConnectionService;
    }

    public ExecutionResult execute(String username, boolean admin, long environmentId, long sqlId, String clientIp) {
        if (!admin && !isEnvironmentAllowed(username, environmentId)) {
            throw new AccessDeniedException("This environment is not allowed for your account");
        }
        if (!admin && !isSqlAllowed(username, sqlId)) {
            throw new AccessDeniedException("This SQL command is not allowed for your account");
        }

        long userId = requireUserId(username);
        String sqlText = loadSqlText(sqlId);
        TargetDatabaseConnection connection = targetDatabaseConnectionService.getEnvironmentConnection(environmentId);

        try (HikariDataSource dataSource = targetDatabaseConnectionService.createDataSource(connection)) {
            JdbcTemplate targetJdbcTemplate = new JdbcTemplate(dataSource);
            StatementOutcome outcome = targetJdbcTemplate.execute((ConnectionCallback<StatementOutcome>)
                    targetConnection -> runStatement(targetConnection, sqlText));

            recordHistory(userId, environmentId, sqlId, "SUCCESS", outcome.recordsReturned(), outcome.rowsAffected(), null, clientIp);
            return new ExecutionResult(
                    true,
                    connection.environmentName(),
                    outcome.columns(),
                    outcome.rows(),
                    outcome.rowsAffected(),
                    null
            );
        } catch (RuntimeException exception) {
            LOGGER.warn("Execution failed for environment {} / sql {}", environmentId, sqlId, exception);
            recordHistory(userId, environmentId, sqlId, "FAILED", null, null, exception.getMessage(), clientIp);
            return new ExecutionResult(
                    false,
                    connection.environmentName(),
                    List.of(),
                    List.of(),
                    null,
                    admin
                            ? describeFailure(exception)
                            : "The command failed against the target database. Please check with an administrator."
            );
        }
    }

    /**
     * Admin-defined SQL is no longer restricted to SELECT (see AdminService.createSqlDefinition), so this
     * mirrors java.sql.Statement.execute() semantics: a statement either produces a ResultSet (queries) or
     * an update count (INSERT/UPDATE/DELETE/DDL) - both are reported back to the caller.
     *
     * <p>sqlText may contain several ';'-separated statements (e.g. a DDL + seed-data script). Each one is
     * split out by {@link SqlScriptSplitter} and sent to the driver individually: MySQL's driver otherwise
     * rejects a multi-statement string outright, while PostgreSQL's would silently run all of them but only
     * expose the first one's outcome. Running them one at a time works the same way for every supported
     * database. The final outcome reflects the last statement if it was a query (its columns/rows), otherwise
     * the total rows affected across every non-query statement in the script.
     */
    private StatementOutcome runStatement(Connection connection, String sqlText) throws SQLException {
        List<String> statements = SqlScriptSplitter.split(sqlText);

        try (Statement statement = connection.createStatement()) {
            statement.setMaxRows(MAX_ROWS);
            statement.setQueryTimeout(QUERY_TIMEOUT_SECONDS);

            List<String> columns = List.of();
            List<List<String>> rows = List.of();
            Integer recordsReturned = null;
            int rowsAffected = 0;
            boolean lastStatementWasQuery = false;

            for (String singleStatement : statements) {
                boolean hasResultSet = statement.execute(singleStatement);

                if (hasResultSet) {
                    try (ResultSet resultSet = statement.getResultSet()) {
                        List<String> resultColumns = new ArrayList<>();
                        ResultSetMetaData metaData = resultSet.getMetaData();
                        for (int i = 1; i <= metaData.getColumnCount(); i++) {
                            resultColumns.add(metaData.getColumnLabel(i));
                        }

                        List<List<String>> resultRows = new ArrayList<>();
                        while (resultSet.next()) {
                            List<String> row = new ArrayList<>(resultColumns.size());
                            for (int i = 1; i <= resultColumns.size(); i++) {
                                Object value = resultSet.getObject(i);
                                row.add(value == null ? null : value.toString());
                            }
                            resultRows.add(row);
                        }

                        columns = resultColumns;
                        rows = resultRows;
                        recordsReturned = resultRows.size();
                        lastStatementWasQuery = true;
                    }
                } else {
                    rowsAffected += Math.max(statement.getUpdateCount(), 0);
                    lastStatementWasQuery = false;
                }
            }

            return lastStatementWasQuery
                    ? new StatementOutcome(columns, rows, recordsReturned, null)
                    : new StatementOutcome(List.of(), List.of(), null, rowsAffected);
        }
    }

    /**
     * Admins are fully trusted (see AdminService), so on failure they get the real driver/SQL error
     * instead of the generic message shown to QA_USER - this is what makes a bad JDBC URL, a missing
     * grant, or a MySQL auth/SSL handshake problem (all indistinguishable from the outside) diagnosable.
     */
    private static String describeFailure(Throwable exception) {
        Throwable deepest = exception;
        while (deepest.getCause() != null && deepest.getCause() != deepest) {
            deepest = deepest.getCause();
        }

        String message = deepest.getMessage();
        String description = message != null && !message.isBlank()
                ? deepest.getClass().getSimpleName() + ": " + message
                : deepest.getClass().getSimpleName();

        return truncate(description, MAX_ERROR_MESSAGE_LENGTH);
    }

    private boolean isEnvironmentAllowed(String username, long environmentId) {
        List<Long> matches = jdbcTemplate.queryForList("""
                select e.env_id
                from environments e
                join user_allowed_env ua on ua.env_id = e.env_id
                join users u on u.user_id = ua.user_id
                where u.username = ? and e.env_id = ?
                """, Long.class, username, environmentId);
        return !matches.isEmpty();
    }

    private boolean isSqlAllowed(String username, long sqlId) {
        List<Long> matches = jdbcTemplate.queryForList("""
                select s.sql_id
                from sql_definitions s
                join user_allowed_sql ua on ua.sql_id = s.sql_id
                join users u on u.user_id = ua.user_id
                where u.username = ? and s.sql_id = ?
                """, Long.class, username, sqlId);
        return !matches.isEmpty();
    }

    private long requireUserId(String username) {
        List<Long> ids = jdbcTemplate.queryForList("select user_id from users where username = ?", Long.class, username);
        if (ids.isEmpty()) {
            throw new IllegalStateException("Authenticated user does not exist");
        }
        return ids.getFirst();
    }

    private String loadSqlText(long sqlId) {
        List<String> texts = jdbcTemplate.queryForList("select sql_text from sql_definitions where sql_id = ?", String.class, sqlId);
        if (texts.isEmpty()) {
            throw new IllegalArgumentException("SQL command does not exist");
        }
        return texts.getFirst();
    }

    private void recordHistory(
            long userId,
            long environmentId,
            long sqlId,
            String status,
            Integer recordsReturned,
            Integer rowsAffected,
            String errorMessage,
            String clientIp
    ) {
        jdbcTemplate.update("""
                insert into execution_history (user_id, env_id, sql_id, status, records_returned, rows_affected, error_message, client_ip)
                values (?, ?, ?, ?, ?, ?, ?, ?)
                """, userId, environmentId, sqlId, status, recordsReturned, rowsAffected,
                truncate(errorMessage, MAX_ERROR_MESSAGE_LENGTH), clientIp);
    }

    private static String truncate(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    private record StatementOutcome(
            List<String> columns,
            List<List<String>> rows,
            Integer recordsReturned,
            Integer rowsAffected
    ) {
    }

    public record ExecutionResult(
            boolean success,
            String environmentName,
            List<String> columns,
            List<List<String>> rows,
            Integer rowsAffected,
            String errorMessage
    ) {
    }
}
