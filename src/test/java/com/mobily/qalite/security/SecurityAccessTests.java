package com.mobily.qalite.security;

import java.util.List;

import com.mobily.qalite.admin.AdminService;
import com.mobily.qalite.config.SecurityConfig;
import com.mobily.qalite.controller.AdminController;
import com.mobily.qalite.controller.PageController;
import com.mobily.qalite.controller.RegistrationController;
import com.mobily.qalite.dashboard.DashboardService;
import com.mobily.qalite.targetdb.TargetDatabaseConnectionService;
import com.mobily.qalite.targetdb.TargetDatabaseType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {
        PageController.class,
        AdminController.class,
        RegistrationController.class
})
@Import({
        SecurityConfig.class,
        SecurityHeadersFilter.class,
        AuthenticationRateLimitFilter.class
})
@ImportAutoConfiguration({
        SecurityAutoConfiguration.class,
        SecurityFilterAutoConfiguration.class,
        ServletWebSecurityAutoConfiguration.class
})
@AutoConfigureMockMvc
class SecurityAccessTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DashboardService dashboardService;

    @MockitoBean
    private AdminService adminService;

    @MockitoBean
    private RegistrationService registrationService;

    @MockitoBean
    private TargetDatabaseConnectionService targetDatabaseConnectionService;

    @BeforeEach
    void setUp() {
        when(dashboardService.getDashboardView(anyString(), anyBoolean()))
                .thenReturn(new DashboardService.DashboardView(List.of(), List.of(), null, null));
        when(adminService.getAdminView())
                .thenReturn(new AdminService.AdminView(List.of(), List.of(), List.of(), List.of(), TargetDatabaseType.supportedTypes()));
    }

    @Test
    void unauthenticatedDashboardRequestIsDenied() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    void regularUserCannotOpenAdminPage() throws Exception {
        mockMvc.perform(get("/admin")
                        .with(user("qa_user").roles("QA_USER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void regularUserCannotCallAdminPostEndpointDirectly() throws Exception {
        mockMvc.perform(post("/admin/sql")
                        .with(user("qa_user").roles("QA_USER"))
                        .with(csrf())
                        .param("sqlName", "Safe SQL")
                        .param("sqlDescription", "Read only")
                        .param("sqlText", "select 1"))
                .andExpect(status().isForbidden());

        verify(adminService, never()).createSqlDefinition(anyString(), anyString(), anyString());
    }

    @Test
    void adminPostWithoutCsrfIsDenied() throws Exception {
        mockMvc.perform(post("/admin/sql")
                        .with(user("admin").roles("ADMIN"))
                        .param("sqlName", "Safe SQL")
                        .param("sqlDescription", "Read only")
                        .param("sqlText", "select 1"))
                .andExpect(status().isForbidden());

        verify(adminService, never()).createSqlDefinition(anyString(), anyString(), anyString());
    }

    @Test
    void adminCanCallAdminPostEndpointWithCsrf() throws Exception {
        mockMvc.perform(post("/admin/sql")
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf())
                        .param("sqlName", "Safe SQL")
                        .param("sqlDescription", "Read only")
                        .param("sqlText", "select 1"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin"));

        verify(adminService).createSqlDefinition("Safe SQL", "Read only", "select 1");
    }

    @Test
    void registerIgnoresSubmittedRole() throws Exception {
        mockMvc.perform(post("/register")
                        .with(csrf())
                        .param("username", "new_user")
                        .param("password", "StrongPass123")
                        .param("role", "ADMIN"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?registered"));

        verify(registrationService).register("new_user", "StrongPass123");
    }

    @Test
    void securityHeadersAreAppliedToPublicPages() throws Exception {
        mockMvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().exists("Content-Security-Policy"));
    }
}
