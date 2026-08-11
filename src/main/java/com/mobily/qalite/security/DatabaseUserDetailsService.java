package com.mobily.qalite.security;

import java.sql.ResultSet;
import java.sql.SQLException;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class DatabaseUserDetailsService implements UserDetailsService {

    private final JdbcTemplate jdbcTemplate;

    public DatabaseUserDetailsService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public UserDetails loadUserByUsername(String username) {
        return jdbcTemplate.query("""
                select username, password_hash, role
                from users
                where username = ?
                """, resultSet -> {
                    if (!resultSet.next()) {
                        throw new UsernameNotFoundException("Invalid username or password");
                    }

                    return mapUser(resultSet);
                }, username);
    }

    private static UserDetails mapUser(ResultSet resultSet) throws SQLException {
        String username = resultSet.getString("username");
        String passwordHash = resultSet.getString("password_hash");
        String authority = toAuthority(resultSet.getString("role"));

        return User.withUsername(username)
                .password(passwordHash)
                .authorities(authority)
                .build();
    }

    private static String toAuthority(String role) {
        if (role.startsWith("ROLE_")) {
            return role;
        }
        return "ROLE_" + role;
    }
}
