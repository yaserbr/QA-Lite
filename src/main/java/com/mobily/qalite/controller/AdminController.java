package com.mobily.qalite.controller;

import java.util.List;

import com.mobily.qalite.admin.AdminService;
import com.mobily.qalite.targetdb.TargetDatabaseConnectionService;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin")
public class AdminController {

    private final AdminService adminService;
    private final TargetDatabaseConnectionService targetDatabaseConnectionService;

    public AdminController(AdminService adminService, TargetDatabaseConnectionService targetDatabaseConnectionService) {
        this.adminService = adminService;
        this.targetDatabaseConnectionService = targetDatabaseConnectionService;
    }

    @GetMapping
    String admin(Model model, Authentication authentication) {
        AdminService.AdminView view = adminService.getAdminView();

        model.addAttribute("users", view.users());
        model.addAttribute("allUsers", view.allUsers());
        model.addAttribute("environments", view.environments());
        model.addAttribute("sqlDefinitions", view.sqlDefinitions());
        model.addAttribute("databaseTypes", view.databaseTypes());
        model.addAttribute("currentUsername", authentication.getName());

        return "admin";
    }

    @PostMapping("/environments")
    String createEnvironment(
            @RequestParam String name,
            @RequestParam(required = false) String description,
            @RequestParam String dbType,
            @RequestParam String jdbcUrl,
            @RequestParam String dbUsername,
            @RequestParam String dbPasswordValue,
            RedirectAttributes redirectAttributes
    ) {
        return runAdminAction(
                () -> adminService.createEnvironment(name, description, dbType, jdbcUrl, dbUsername, dbPasswordValue),
                "Environment added.",
                redirectAttributes
        );
    }

    @PostMapping("/sql")
    String createSqlDefinition(
            @RequestParam String sqlName,
            @RequestParam(required = false) String sqlDescription,
            @RequestParam String sqlText,
            RedirectAttributes redirectAttributes
    ) {
        return runAdminAction(
                () -> adminService.createSqlDefinition(sqlName, sqlDescription, sqlText),
                "SQL command added.",
                redirectAttributes
        );
    }

    @PostMapping("/environments/{envId}/delete")
    String deleteEnvironment(@PathVariable long envId, RedirectAttributes redirectAttributes) {
        return runAdminAction(
                () -> adminService.deleteEnvironment(envId),
                "Environment deleted.",
                redirectAttributes
        );
    }

    @PostMapping("/sql/{sqlId}/delete")
    String deleteSqlDefinition(@PathVariable long sqlId, RedirectAttributes redirectAttributes) {
        return runAdminAction(
                () -> adminService.deleteSqlDefinition(sqlId),
                "SQL command deleted.",
                redirectAttributes
        );
    }

    @PostMapping("/environments/{envId}/test")
    String testEnvironmentConnection(@PathVariable long envId, RedirectAttributes redirectAttributes) {
        try {
            boolean reachable = targetDatabaseConnectionService.testConnection(envId);
            redirectAttributes.addFlashAttribute(
                    reachable ? "successMessage" : "errorMessage",
                    reachable ? "Connection succeeded." : "Connection failed. Check the JDBC URL and credentials."
            );
        } catch (IllegalArgumentException exception) {
            redirectAttributes.addFlashAttribute("errorMessage", exception.getMessage());
        }

        return "redirect:/admin";
    }

    @PostMapping("/users/{userId}/delete")
    String deleteUser(@PathVariable long userId, Authentication authentication, RedirectAttributes redirectAttributes) {
        return runAdminAction(
                () -> adminService.deleteUser(userId, authentication.getName()),
                "User deleted.",
                redirectAttributes
        );
    }

    @PostMapping("/users/{userId}/permissions")
    String updatePermissions(
            @PathVariable long userId,
            @RequestParam(required = false) List<Long> environmentIds,
            @RequestParam(required = false) List<Long> sqlIds,
            RedirectAttributes redirectAttributes
    ) {
        return runAdminAction(
                () -> adminService.updateUserPermissions(userId, environmentIds, sqlIds),
                "User access updated.",
                redirectAttributes
        );
    }

    private String runAdminAction(
            Runnable action,
            String successMessage,
            RedirectAttributes redirectAttributes
    ) {
        try {
            action.run();
            redirectAttributes.addFlashAttribute("successMessage", successMessage);
        } catch (DuplicateKeyException exception) {
            redirectAttributes.addFlashAttribute("errorMessage", "A record with the same name already exists.");
        } catch (DataIntegrityViolationException exception) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Invalid selection, or this record is still referenced by other data (e.g. execution history).");
        } catch (IllegalArgumentException exception) {
            redirectAttributes.addFlashAttribute("errorMessage", exception.getMessage());
        } catch (IllegalStateException exception) {
            redirectAttributes.addFlashAttribute("errorMessage", exception.getMessage());
        } catch (DataAccessException exception) {
            redirectAttributes.addFlashAttribute("errorMessage", "Database operation failed.");
        }

        return "redirect:/admin";
    }
}
