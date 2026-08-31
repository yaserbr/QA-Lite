package com.mobily.qalite.admin;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.mobily.qalite.security.SecretCipherService;
import com.mobily.qalite.targetdb.TargetDatabaseType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class AdminService {

    private static final int MAX_NAME_LENGTH = 100;
    private static final int MAX_ENV_DESCRIPTION_LENGTH = 500;
    private static final int MAX_JDBC_URL_LENGTH = 1000;
    private static final int MAX_DB_USERNAME_LENGTH = 200;
    private static final int MAX_SQL_NAME_LENGTH = 200;
    private static final int MAX_SQL_DESCRIPTION_LENGTH = 1000;
    private static final int MAX_SQL_TEXT_LENGTH = 20_000;
    private static final int MAX_PERMISSION_IDS = 500;

    private final JdbcTemplate jdbcTemplate;
    private final SecretCipherService secretCipherService;

    public AdminService(JdbcTemplate jdbcTemplate, SecretCipherService secretCipherService) {
        this.jdbcTemplate = jdbcTemplate;
        this.secretCipherService = secretCipherService;
    }

    public AdminView getAdminView() {
        List<AdminEnvironment> environments = loadEnvironments();
        List<AdminSqlDefinition> sqlDefinitions = loadSqlDefinitions();
        List<AdminUser> users = loadUsers();

        return new AdminView(users, environments, sqlDefinitions, TargetDatabaseType.supportedTypes());
    }

    @Transactional
    public void createEnvironment(
            String name,
            String description,
            String dbType,
            String jdbcUrl,
            String dbUsername,
            String dbPasswordValue
    ) {
        TargetDatabaseType targetDatabaseType = TargetDatabaseType.from(dbType);
        String normalizedJdbcUrl = required(jdbcUrl, "JDBC URL", MAX_JDBC_URL_LENGTH);
        targetDatabaseType.validateJdbcUrl(normalizedJdbcUrl);
        validateNoEmbeddedCredentials(normalizedJdbcUrl);

        jdbcTemplate.update("""
                insert into environments (name, description, db_type, jdbc_url, db_username, db_password_enc)
                values (?, ?, ?, ?, ?, ?)
                """,
                required(name, "Environment name", MAX_NAME_LENGTH),
                optional(description, MAX_ENV_DESCRIPTION_LENGTH),
                targetDatabaseType.name(),
                normalizedJdbcUrl,
                required(dbUsername, "Database username", MAX_DB_USERNAME_LENGTH),
                secretCipherService.encrypt(required(dbPasswordValue, "Database password value"))
        );
    }

    @Transactional
    public void createSqlDefinition(String sqlName, String sqlDescription, String sqlText) {
        String normalizedSqlText = required(sqlText, "SQL text", MAX_SQL_TEXT_LENGTH);

        jdbcTemplate.update("""
                insert into sql_definitions (sql_name, sql_description, sql_text)
                values (?, ?, ?)
                """,
                required(sqlName, "SQL command name", MAX_SQL_NAME_LENGTH),
                optional(sqlDescription, MAX_SQL_DESCRIPTION_LENGTH),
                normalizedSqlText
        );
    }

    @Transactional
    public void deleteEnvironment(long envId) {
        jdbcTemplate.update("delete from execution_history where env_id = ?", envId);

        int deletedRows = jdbcTemplate.update("delete from environments where env_id = ?", envId);
        if (deletedRows == 0) {
            throw new IllegalArgumentException("Environment does not exist");
        }
    }

    @Transactional
    public void deleteSqlDefinition(long sqlId) {
        jdbcTemplate.update("delete from execution_history where sql_id = ?", sqlId);

        int deletedRows = jdbcTemplate.update("delete from sql_definitions where sql_id = ?", sqlId);
        if (deletedRows == 0) {
            throw new IllegalArgumentException("SQL command does not exist");
        }
    }

    /**
     * Deletes any user account, including other admins - guarded so an admin can't delete their own
     * account (avoids a stray click locking them out) or the last remaining admin (avoids locking
     * everyone out of /admin with no way back in short of a direct database change).
     */
    @Transactional
    public void deleteUser(long userId, String actingUsername) {
        String username = jdbcTemplate.queryForList("select username from users where user_id = ?", String.class, userId)
                .stream()
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("User does not exist"));

        if (username.equals(actingUsername)) {
            throw new IllegalArgumentException("You cannot delete your own account");
        }

        if ("ADMIN".equals(getUserRole(userId))) {
            Integer adminCount = jdbcTemplate.queryForObject("select count(*) from users where role = 'ADMIN'", Integer.class);
            if (adminCount == null || adminCount <= 1) {
                throw new IllegalArgumentException("Cannot delete the last remaining admin account");
            }
        }

        jdbcTemplate.update("delete from execution_history where user_id = ?", userId);
        jdbcTemplate.update("delete from users where user_id = ?", userId);
    }

    @Transactional
    public void updateUserPermissions(long userId, List<Long> environmentIds, List<Long> sqlIds) {
        String role = getUserRole(userId);
        if (role == null) {
            throw new IllegalArgumentException("User does not exist");
        }
        if ("ADMIN".equals(role)) {
            throw new IllegalArgumentException("Admin users automatically have full access");
        }
        validatePermissionIdCount(environmentIds, "Environment permissions");
        validatePermissionIdCount(sqlIds, "SQL permissions");

        replaceAllowedIds(
                "delete from user_allowed_env where user_id = ?",
                "insert into user_allowed_env (user_id, env_id) values (?, ?)",
                userId,
                uniqueIds(environmentIds)
        );
        replaceAllowedIds(
                "delete from user_allowed_sql where user_id = ?",
                "insert into user_allowed_sql (user_id, sql_id) values (?, ?)",
                userId,
                uniqueIds(sqlIds)
        );
    }

    private List<AdminUser> loadUsers() {
        Map<Long, Set<Long>> allowedEnvironmentIds = loadAllowedIds("select user_id, env_id from user_allowed_env", "env_id");
        Map<Long, Set<Long>> allowedSqlIds = loadAllowedIds("select user_id, sql_id from user_allowed_sql", "sql_id");

        return jdbcTemplate.query("""
                select user_id, username, role
                from users
                where role <> 'ADMIN'
                order by username
                """, (resultSet, rowNumber) -> {
            long userId = resultSet.getLong("user_id");
            return new AdminUser(
                    userId,
                    resultSet.getString("username"),
                    resultSet.getString("role"),
                    allowedEnvironmentIds.getOrDefault(userId, Set.of()),
                    allowedSqlIds.getOrDefault(userId, Set.of())
            );
        });
    }

    private List<AdminEnvironment> loadEnvironments() {
        return jdbcTemplate.query("""
                select env_id, name, description, db_type, jdbc_url, db_username
                from environments
                order by name
                """, (resultSet, rowNumber) -> new AdminEnvironment(
                resultSet.getLong("env_id"),
                resultSet.getString("name"),
                resultSet.getString("description"),
                resultSet.getString("db_type"),
                resultSet.getString("jdbc_url"),
                resultSet.getString("db_username")
        ));
    }

    private List<AdminSqlDefinition> loadSqlDefinitions() {
        return jdbcTemplate.query("""
                select sql_id, sql_name, sql_description, sql_text
                from sql_definitions
                order by sql_name
                """, (resultSet, rowNumber) -> new AdminSqlDefinition(
                resultSet.getLong("sql_id"),
                resultSet.getString("sql_name"),
                resultSet.getString("sql_description"),
                resultSet.getString("sql_text")
        ));
    }

    private Map<Long, Set<Long>> loadAllowedIds(String sql, String targetColumn) {
        Map<Long, Set<Long>> allowedIds = new HashMap<>();

        jdbcTemplate.query(sql, resultSet -> {
            long userId = resultSet.getLong("user_id");
            long targetId = resultSet.getLong(targetColumn);
            allowedIds.computeIfAbsent(userId, ignored -> new LinkedHashSet<>()).add(targetId);
        });

        return allowedIds;
    }

    private String getUserRole(long userId) {
        List<String> roles = jdbcTemplate.queryForList(
                "select role from users where user_id = ?",
                String.class,
                userId
        );
        if (roles.isEmpty()) {
            return null;
        }
        return roles.getFirst();
    }

    private void replaceAllowedIds(String deleteSql, String insertSql, long userId, Collection<Long> targetIds) {
        jdbcTemplate.update(deleteSql, userId);

        if (targetIds.isEmpty()) {
            return;
        }

        jdbcTemplate.batchUpdate(insertSql, targetIds, targetIds.size(), (statement, targetId) -> {
            statement.setLong(1, userId);
            statement.setLong(2, targetId);
        });
    }

    private static Set<Long> uniqueIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Set.of();
        }
        return new LinkedHashSet<>(ids);
    }

    private static void validatePermissionIdCount(List<Long> ids, String fieldName) {
        if (ids != null && ids.size() > MAX_PERMISSION_IDS) {
            throw new IllegalArgumentException(fieldName + " exceeded the allowed limit");
        }
    }

    private static void validateNoEmbeddedCredentials(String jdbcUrl) {
        String normalizedUrl = jdbcUrl.toLowerCase();
        if (normalizedUrl.matches(".*[?&;](user|username|password)=.*")) {
            throw new IllegalArgumentException("Put database credentials in the username and password fields, not in the JDBC URL");
        }

        if (normalizedUrl.startsWith("jdbc:mongodb://")) {
            String authority = jdbcUrl.substring("jdbc:mongodb://".length()).split("/", 2)[0];
            if (authority.contains("@")) {
                throw new IllegalArgumentException("Put MongoDB Atlas credentials in the username and password fields, not in the JDBC URL");
            }
        }
    }

    private static String required(String value, String fieldName) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        return value.trim();
    }

    private static String required(String value, String fieldName, int maxLength) {
        String normalizedValue = required(value, fieldName);
        if (normalizedValue.length() > maxLength) {
            throw new IllegalArgumentException(fieldName + " is too long");
        }
        return normalizedValue;
    }

    private static String optional(String value, int maxLength) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String normalizedValue = value.trim();
        if (normalizedValue.length() > maxLength) {
            throw new IllegalArgumentException("Value is too long");
        }
        return normalizedValue;
    }

    public record AdminView(
            List<AdminUser> users,
            List<AdminEnvironment> environments,
            List<AdminSqlDefinition> sqlDefinitions,
            List<TargetDatabaseType> databaseTypes
    ) {
    }

    public record AdminUser(
            long userId,
            String username,
            String role,
            Set<Long> environmentIds,
            Set<Long> sqlIds
    ) {
    }

    public record AdminEnvironment(
            long envId,
            String name,
            String description,
            String dbType,
            String jdbcUrl,
            String dbUsername
    ) {
    }

    public record AdminSqlDefinition(
            long sqlId,
            String sqlName,
            String sqlDescription,
            String sqlText
    ) {
    }
}
