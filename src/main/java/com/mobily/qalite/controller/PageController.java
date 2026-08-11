package com.mobily.qalite.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class PageController {

    @GetMapping("/login")
    String login() {
        return "login";
    }

    @GetMapping("/")
    String dashboard() {
        return "dashboard";
    }
}
