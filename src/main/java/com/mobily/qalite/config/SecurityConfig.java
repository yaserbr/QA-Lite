package com.mobily.qalite.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/css/**", "/images/**", "/js/**", "/login").permitAll()
                        .anyRequest().authenticated()
                )
                .formLogin(form -> form
                        .loginPage("/login")
                        .successHandler((request, response, authentication) -> {
                            if (isAsyncRequest(request)) {
                                response.setStatus(HttpServletResponse.SC_NO_CONTENT);
                                return;
                            }
                            response.sendRedirect(request.getContextPath() + "/");
                        })
                        .failureHandler((request, response, exception) -> {
                            if (isAsyncRequest(request)) {
                                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                                return;
                            }
                            response.sendRedirect(request.getContextPath() + "/login?error");
                        })
                        .permitAll()
                )
                .logout(logout -> logout
                        .logoutSuccessHandler((request, response, authentication) -> {
                            if (isAsyncRequest(request)) {
                                response.setStatus(HttpServletResponse.SC_NO_CONTENT);
                                return;
                            }
                            response.sendRedirect(request.getContextPath() + "/login?logout");
                        })
                        .permitAll()
                )
                .build();
    }

    private static boolean isAsyncRequest(HttpServletRequest request) {
        return "XMLHttpRequest".equals(request.getHeader("X-Requested-With"));
    }
}
