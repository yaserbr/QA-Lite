package com.mobily.qalite.controller;

import com.mobily.qalite.execution.ExecutionService;
import com.mobily.qalite.execution.ExecutionService.ExecutionResult;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ExecutionController {

    private final ExecutionService executionService;

    public ExecutionController(ExecutionService executionService) {
        this.executionService = executionService;
    }

    @PostMapping("/execute")
    ResponseEntity<?> execute(@RequestBody ExecuteRequest request, Authentication authentication, HttpServletRequest servletRequest) {
        try {
            ExecutionResult result = executionService.execute(
                    authentication.getName(),
                    isAdmin(authentication),
                    request.environmentId(),
                    request.sqlId(),
                    servletRequest.getRemoteAddr()
            );
            return ResponseEntity.ok(result);
        } catch (AccessDeniedException exception) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ErrorResponse(exception.getMessage()));
        } catch (IllegalArgumentException | IllegalStateException exception) {
            return ResponseEntity.badRequest().body(new ErrorResponse(exception.getMessage()));
        }
    }

    private static boolean isAdmin(Authentication authentication) {
        return authentication != null && authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()));
    }

    record ExecuteRequest(long environmentId, long sqlId) {
    }

    record ErrorResponse(String message) {
    }
}
