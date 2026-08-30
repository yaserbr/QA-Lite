package com.mobily.qalite.admin;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import com.mobily.qalite.security.SecretCipherService;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminServiceSecurityTests {

    private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    private final SecretCipherService secretCipherService = new SecretCipherService("security-test-key");
    private final AdminService adminService = new AdminService(jdbcTemplate, secretCipherService);

    @Test
    void adminUserPermissionsCannotBeModifiedDirectly() {
        when(jdbcTemplate.queryForList("select role from users where user_id = ?", String.class, 1L))
                .thenReturn(List.of("ADMIN"));

        assertThrows(IllegalArgumentException.class, () -> adminService.updateUserPermissions(1L, List.of(1L), List.of(1L)));

        verify(jdbcTemplate, never()).update("delete from user_allowed_env where user_id = ?", 1L);
        verify(jdbcTemplate, never()).update("delete from user_allowed_sql where user_id = ?", 1L);
    }

    @Test
    void destructiveSqlDefinitionIsAccepted() {
        adminService.createSqlDefinition("Cleanup Stale Rows", "Admin-authored write", "delete from stale_rows");

        verify(jdbcTemplate).update(anyString(), eq("Cleanup Stale Rows"), eq("Admin-authored write"), eq("delete from stale_rows"));
    }

    @Test
    void environmentPasswordIsStoredEncrypted() {
        adminService.createEnvironment(
                "SIT",
                "Integration",
                "POSTGRESQL",
                "jdbc:postgresql://host:5432/db",
                "qa_readonly",
                "secret-pass"
        );

        verify(jdbcTemplate).update(
                anyString(),
                eq("SIT"),
                eq("Integration"),
                eq("POSTGRESQL"),
                eq("jdbc:postgresql://host:5432/db"),
                eq("qa_readonly"),
                argThat(value -> value instanceof String stored
                        && stored.startsWith("enc:v1:")
                        && !stored.contains("secret-pass"))
        );
    }

    @Test
    void mongodbAtlasSqlEnvironmentIsAccepted() {
        adminService.createEnvironment(
                "Atlas",
                "MongoDB reporting",
                "MONGODB_ATLAS_SQL",
                "jdbc:mongodb://cluster.a.query.mongodb.net/sample_mflix",
                "qa_reader",
                "secret-pass"
        );

        verify(jdbcTemplate).update(
                anyString(),
                eq("Atlas"),
                eq("MongoDB reporting"),
                eq("MONGODB_ATLAS_SQL"),
                eq("jdbc:mongodb://cluster.a.query.mongodb.net/sample_mflix"),
                eq("qa_reader"),
                argThat(value -> value instanceof String stored && stored.startsWith("enc:v1:"))
        );
    }

    @Test
    void jdbcUrlCredentialsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> adminService.createEnvironment(
                "Unsafe",
                "Bad URL",
                "MONGODB_ATLAS_SQL",
                "jdbc:mongodb://qa_reader:secret@cluster.a.query.mongodb.net/sample_mflix",
                "qa_reader",
                "secret-pass"
        ));

        verify(jdbcTemplate, never()).update(anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), anyString());
    }
}
