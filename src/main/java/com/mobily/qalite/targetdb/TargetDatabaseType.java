package com.mobily.qalite.targetdb;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.List;

public enum TargetDatabaseType {

    POSTGRESQL(
            "PostgreSQL",
            "org.postgresql.Driver",
            "jdbc:postgresql:",
            "jdbc:postgresql://host:5432/database"
    ),
    ORACLE(
            "Oracle",
            "oracle.jdbc.OracleDriver",
            "jdbc:oracle:",
            "jdbc:oracle:thin:@//host:1521/serviceName"
    ),
    MYSQL(
            "MySQL",
            "com.mysql.cj.jdbc.Driver",
            "jdbc:mysql:",
            "jdbc:mysql://host:3306/database"
    ),
    MONGODB_ATLAS_SQL(
            "MongoDB Atlas SQL",
            "com.mongodb.jdbc.MongoDriver",
            "jdbc:mongodb:",
            "jdbc:mongodb://cluster.a.query.mongodb.net/database"
    );

    private final String label;
    private final String driverClassName;
    private final String jdbcPrefix;
    private final String jdbcExample;

    TargetDatabaseType(String label, String driverClassName, String jdbcPrefix, String jdbcExample) {
        this.label = label;
        this.driverClassName = driverClassName;
        this.jdbcPrefix = jdbcPrefix;
        this.jdbcExample = jdbcExample;
    }

    public static TargetDatabaseType from(String value) {
        return Arrays.stream(values())
                .filter(type -> type.name().equalsIgnoreCase(value))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unsupported database type"));
    }

    public static List<TargetDatabaseType> supportedTypes() {
        return List.of(values());
    }

    public void validateJdbcUrl(String jdbcUrl) {
        if (jdbcUrl == null || !jdbcUrl.trim().toLowerCase().startsWith(jdbcPrefix)) {
            throw new IllegalArgumentException(label + " JDBC URL must start with " + jdbcPrefix);
        }
        if (this == MONGODB_ATLAS_SQL && !hasMongoDatabaseName(jdbcUrl)) {
            throw new IllegalArgumentException("MongoDB Atlas SQL JDBC URL must include a database name");
        }
    }

    public String getValue() {
        return name();
    }

    public String getLabel() {
        return label;
    }

    public String getDriverClassName() {
        return driverClassName;
    }

    public String getJdbcExample() {
        return jdbcExample;
    }

    public boolean requiresDatabaseProperty() {
        return this == MONGODB_ATLAS_SQL;
    }

    public String databaseNameFromJdbcUrl(String jdbcUrl) {
        if (!requiresDatabaseProperty()) {
            return null;
        }
        return mongoDatabaseName(jdbcUrl);
    }

    private static boolean hasMongoDatabaseName(String jdbcUrl) {
        String databaseName = mongoDatabaseName(jdbcUrl);
        return databaseName != null && !databaseName.isBlank();
    }

    private static String mongoDatabaseName(String jdbcUrl) {
        try {
            URI uri = new URI(jdbcUrl.substring("jdbc:".length()));
            String path = uri.getPath();
            if (path == null || path.length() <= 1) {
                return null;
            }
            return path.substring(1);
        } catch (IllegalArgumentException | URISyntaxException exception) {
            throw new IllegalArgumentException("MongoDB Atlas SQL JDBC URL is invalid", exception);
        }
    }
}
