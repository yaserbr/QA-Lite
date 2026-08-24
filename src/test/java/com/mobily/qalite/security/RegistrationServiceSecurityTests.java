package com.mobily.qalite.security;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RegistrationServiceSecurityTests {

    private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
    private final RegistrationService registrationService = new RegistrationService(jdbcTemplate, passwordEncoder);

    @Test
    void registrationAlwaysCreatesStandardUserRole() {
        when(passwordEncoder.encode("StrongPass123")).thenReturn("HASHED");

        registrationService.register("new_user", "StrongPass123");

        verify(jdbcTemplate).update(anyString(), eq("new_user"), eq("HASHED"), eq("QA_USER"));
    }

    @Test
    void weakPasswordIsRejectedBeforeDatabaseWrite() {
        assertThrows(IllegalArgumentException.class, () -> registrationService.register("new_user", "short"));

        verify(jdbcTemplate, never()).update(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void malformedUsernameIsRejectedBeforeDatabaseWrite() {
        assertThrows(IllegalArgumentException.class, () -> registrationService.register("bad user", "StrongPass123"));

        verify(jdbcTemplate, never()).update(anyString(), anyString(), anyString(), anyString());
    }
}
