package com.mobily.qalite.targetdb;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TargetDatabaseTypeTests {

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
