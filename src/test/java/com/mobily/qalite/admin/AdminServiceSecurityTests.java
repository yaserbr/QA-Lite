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
    void adminCannotDeleteTheirOwnAccount() {
        when(jdbcTemplate.queryForList("select username from users where user_id = ?", String.class, 1L))
                .thenReturn(List.of("admin"));

        assertThrows(IllegalArgumentException.class, () -> adminService.deleteUser(1L, "admin"));

        verify(jdbcTemplate, never()).update("delete from users where user_id = ?", 1L);
    }

    @Test
    void lastRemainingAdminCannotBeDeleted() {
        when(jdbcTemplate.queryForList("select username from users where user_id = ?", String.class, 2L))
                .thenReturn(List.of("second_admin"));
        when(jdbcTemplate.queryForList("select role from users where user_id = ?", String.class, 2L))
                .thenReturn(List.of("ADMIN"));
        when(jdbcTemplate.queryForObject("select count(*) from users where role = 'ADMIN'", Integer.class))
                .thenReturn(1);

        assertThrows(IllegalArgumentException.class, () -> adminService.deleteUser(2L, "admin"));

        verify(jdbcTemplate, never()).update("delete from users where user_id = ?", 2L);
    }

    @Test
    void anotherAdminCanBeDeletedWhenMoreThanOneAdminExists() {
        when(jdbcTemplate.queryForList("select username from users where user_id = ?", String.class, 2L))
                .thenReturn(List.of("second_admin"));
        when(jdbcTemplate.queryForList("select role from users where user_id = ?", String.class, 2L))
                .thenReturn(List.of("ADMIN"));
        when(jdbcTemplate.queryForObject("select count(*) from users where role = 'ADMIN'", Integer.class))
                .thenReturn(2);

        adminService.deleteUser(2L, "admin");

        verify(jdbcTemplate).update("delete from execution_history where user_id = ?", 2L);
        verify(jdbcTemplate).update("delete from users where user_id = ?", 2L);
    }

    @Test
    void ordinaryUserCanBeDeletedByAdmin() {
        when(jdbcTemplate.queryForList("select username from users where user_id = ?", String.class, 3L))
                .thenReturn(List.of("qa_user"));
        when(jdbcTemplate.queryForList("select role from users where user_id = ?", String.class, 3L))
                .thenReturn(List.of("QA_USER"));

        adminService.deleteUser(3L, "admin");

        verify(jdbcTemplate).update("delete from execution_history where user_id = ?", 3L);
        verify(jdbcTemplate).update("delete from users where user_id = ?", 3L);
        verify(jdbcTemplate, never()).queryForObject("select count(*) from users where role = 'ADMIN'", Integer.class);
    }

    @Test
    void deletingANonExistentUserFails() {
        when(jdbcTemplate.queryForList("select username from users where user_id = ?", String.class, 99L))
                .thenReturn(List.of());

        assertThrows(IllegalArgumentException.class, () -> adminService.deleteUser(99L, "admin"));
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
