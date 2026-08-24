package com.mobily.qalite.security;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class RegistrationService {

    private static final int MIN_USERNAME_LENGTH = 3;
    private static final int MAX_USERNAME_LENGTH = 100;
    private static final int MIN_PASSWORD_LENGTH = 8;
    private static final int MAX_PASSWORD_LENGTH = 128;
    private static final String USERNAME_PATTERN = "[A-Za-z0-9._-]+";
    private static final String DEFAULT_ROLE = "QA_USER";

    private final JdbcTemplate jdbcTemplate;
    private final PasswordEncoder passwordEncoder;

    public RegistrationService(JdbcTemplate jdbcTemplate, PasswordEncoder passwordEncoder) {
        this.jdbcTemplate = jdbcTemplate;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public void register(String username, String password) {
        String normalizedUsername = required(username, "Username");
        String rawPassword = required(password, "Password");

        validateUsername(normalizedUsername);
        validatePassword(rawPassword);

        try {
            jdbcTemplate.update("""
                    insert into users (username, password_hash, role)
                    values (?, ?, ?)
                    """,
                    normalizedUsername,
                    passwordEncoder.encode(rawPassword),
                    DEFAULT_ROLE
            );
        } catch (DuplicateKeyException exception) {
            throw new IllegalArgumentException("Username already exists", exception);
        }
    }

    private static void validateUsername(String username) {
        if (username.length() < MIN_USERNAME_LENGTH || username.length() > MAX_USERNAME_LENGTH) {
            throw new IllegalArgumentException("Username must be between 3 and 100 characters");
        }
        if (!username.matches(USERNAME_PATTERN)) {
            throw new IllegalArgumentException("Username can contain letters, numbers, dots, underscores, and hyphens only");
        }
    }

    private static void validatePassword(String password) {
        if (password.length() < MIN_PASSWORD_LENGTH || password.length() > MAX_PASSWORD_LENGTH) {
            throw new IllegalArgumentException("Password must be between 8 and 128 characters");
        }
    }

    private static String required(String value, String fieldName) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        return value.trim();
    }
}
