package com.mobily.qalite.dashboard;

import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class DashboardService {

    private static final List<String> TONES = List.of("is-blue", "is-green", "is-amber");

    private final JdbcTemplate jdbcTemplate;

    public DashboardService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public DashboardView getDashboardView(String username, boolean admin) {
        List<DashboardEnvironment> environments = admin ? loadAllEnvironments() : loadAllowedEnvironments(username);
        List<DashboardSqlCommand> sqlCommands = admin ? loadAllSqlCommands() : loadAllowedSqlCommands(username);

        return new DashboardView(
                environments,
                sqlCommands,
                environments.isEmpty() ? null : environments.getFirst(),
                sqlCommands.isEmpty() ? null : sqlCommands.getFirst()
        );
    }

    private List<DashboardEnvironment> loadAllEnvironments() {
        return jdbcTemplate.query("""
                select env_id, name, description, db_type
                from environments
                order by name
                """, (resultSet, rowNumber) -> new DashboardEnvironment(
                resultSet.getLong("env_id"),
                resultSet.getString("name"),
                note(resultSet.getString("description"), resultSet.getString("db_type"))
        ));
    }

    private List<DashboardEnvironment> loadAllowedEnvironments(String username) {
        return jdbcTemplate.query("""
                select e.env_id, e.name, e.description, e.db_type
                from environments e
                join user_allowed_env ua on ua.env_id = e.env_id
                join users u on u.user_id = ua.user_id
                where u.username = ?
                order by e.name
                """, (resultSet, rowNumber) -> new DashboardEnvironment(
                resultSet.getLong("env_id"),
                resultSet.getString("name"),
                note(resultSet.getString("description"), resultSet.getString("db_type"))
        ), username);
    }

    private List<DashboardSqlCommand> loadAllSqlCommands() {
        return jdbcTemplate.query("""
                select sql_id, sql_name, sql_description
                from sql_definitions
                order by sql_name
                """, (resultSet, rowNumber) -> mapSqlCommand(
                resultSet.getLong("sql_id"),
                resultSet.getString("sql_name"),
                resultSet.getString("sql_description"),
                rowNumber
        ));
    }

    private List<DashboardSqlCommand> loadAllowedSqlCommands(String username) {
        return jdbcTemplate.query("""
                select s.sql_id, s.sql_name, s.sql_description
                from sql_definitions s
                join user_allowed_sql ua on ua.sql_id = s.sql_id
                join users u on u.user_id = ua.user_id
                where u.username = ?
                order by s.sql_name
                """, (resultSet, rowNumber) -> mapSqlCommand(
                resultSet.getLong("sql_id"),
                resultSet.getString("sql_name"),
                resultSet.getString("sql_description"),
                rowNumber
        ), username);
    }

    private static DashboardSqlCommand mapSqlCommand(long sqlId, String sqlName, String sqlDescription, int rowNumber) {
        return new DashboardSqlCommand(
                sqlId,
                sqlName,
                sqlDescription,
                TONES.get(rowNumber % TONES.size()),
                fieldLabel(sqlName),
                fieldValue(sqlName)
        );
    }

    private static String note(String description, String dbType) {
        if (StringUtils.hasText(description)) {
            return description.trim();
        }
        return dbType;
    }

    private static String fieldLabel(String sqlName) {
        String normalizedName = sqlName.toLowerCase();

        if (normalizedName.contains("customer")) {
            return "Customer ID";
        }
        if (normalizedName.contains("order")) {
            return "Order Status";
        }
        if (normalizedName.contains("history") || normalizedName.contains("user")) {
            return "Username";
        }
        return null;
    }

    private static String fieldValue(String sqlName) {
        String normalizedName = sqlName.toLowerCase();

        if (normalizedName.contains("customer")) {
            return "101";
        }
        if (normalizedName.contains("order")) {
            return "ALL";
        }
        if (normalizedName.contains("history") || normalizedName.contains("user")) {
            return "qa_user";
        }
        return null;
    }

    public record DashboardView(
            List<DashboardEnvironment> environments,
            List<DashboardSqlCommand> sqlCommands,
            DashboardEnvironment activeEnvironment,
            DashboardSqlCommand activeCommand
    ) {
    }

    public record DashboardEnvironment(
            long envId,
            String name,
            String note
    ) {
    }

    public record DashboardSqlCommand(
            long sqlId,
            String sqlName,
            String sqlDescription,
            String tone,
            String fieldLabel,
            String fieldValue
    ) {
        public String commandId() {
            return "sql-" + sqlId;
        }
    }
}
