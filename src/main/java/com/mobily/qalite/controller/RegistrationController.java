package com.mobily.qalite.controller;

import com.mobily.qalite.security.RegistrationService;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class RegistrationController {

    private final RegistrationService registrationService;

    public RegistrationController(RegistrationService registrationService) {
        this.registrationService = registrationService;
    }

    @PostMapping("/register")
    String createAccount(
            @RequestParam String username,
            @RequestParam String password,
            RedirectAttributes redirectAttributes
    ) {
        try {
            registrationService.register(username, password);
            redirectAttributes.addFlashAttribute("registeredUsername", username.trim());
            return "redirect:/login?registered";
        } catch (IllegalArgumentException exception) {
            redirectAttributes.addFlashAttribute("errorMessage", exception.getMessage());
        } catch (DataAccessException exception) {
            redirectAttributes.addFlashAttribute("errorMessage", "Registration failed. Please try again.");
        }

        redirectAttributes.addFlashAttribute("username", username);
        return "redirect:/login";
    }
}
