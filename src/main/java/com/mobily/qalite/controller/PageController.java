package com.mobily.qalite.controller;

import com.mobily.qalite.dashboard.DashboardService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class PageController {

    private final DashboardService dashboardService;

    public PageController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/login")
    String login() {
        return "login";
    }

    @GetMapping("/")
    String dashboard(Model model, Authentication authentication) {
        boolean admin = isAdmin(authentication);
        DashboardService.DashboardView dashboardView = dashboardService.getDashboardView(authentication.getName(), admin);

        model.addAttribute("isAdmin", admin);
        model.addAttribute("environments", dashboardView.environments());
        model.addAttribute("sqlCommands", dashboardView.sqlCommands());
        model.addAttribute("activeEnvironment", dashboardView.activeEnvironment());
        model.addAttribute("activeCommand", dashboardView.activeCommand());

        return "dashboard";
    }

    private static boolean isAdmin(Authentication authentication) {
        return authentication != null && authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()));
    }
}
