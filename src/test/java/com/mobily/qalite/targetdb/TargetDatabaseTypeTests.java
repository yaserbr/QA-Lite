package com.mobily.qalite.targetdb;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TargetDatabaseTypeTests {

    @Test
    void mysqlJdbcUrlIsAccepted() {
        TargetDatabaseType.MYSQL.validateJdbcUrl("jdbc:mysql://host:3306/database");
    }

    @Test
    void mysqlRejectsUrlFromAnotherDatabaseType() {
        assertThrows(
                IllegalArgumentException.class,
                () -> TargetDatabaseType.MYSQL.validateJdbcUrl("jdbc:postgresql://host:5432/database")
        );
    }

    @Test
    void mongodbAtlasSqlJdbcUrlMustIncludeDatabaseName() {
        assertThrows(
                IllegalArgumentException.class,
                () -> TargetDatabaseType.MONGODB_ATLAS_SQL.validateJdbcUrl("jdbc:mongodb://cluster.a.query.mongodb.net")
        );
    }

    @Test
    void mongodbAtlasSqlDatabaseNameIsReadFromJdbcUrl() {
        assertEquals(
                "sample_mflix",
                TargetDatabaseType.MONGODB_ATLAS_SQL.databaseNameFromJdbcUrl(
                        "jdbc:mongodb://cluster.a.query.mongodb.net/sample_mflix"
                )
        );
    }
}
