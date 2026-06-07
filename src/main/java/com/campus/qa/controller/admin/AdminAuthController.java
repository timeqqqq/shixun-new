package com.campus.qa.controller.admin;

import com.campus.qa.dto.admin.AdminLoginRequest;
import com.campus.qa.interceptor.AdminAuthInterceptor;
import com.campus.qa.service.AdminAuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/auth")
public class AdminAuthController {

    private final AdminAuthService adminAuthService;

    public AdminAuthController(AdminAuthService adminAuthService) {
        this.adminAuthService = adminAuthService;
    }

    @PostMapping("/login")
    public Map<String, String> login(@Valid @RequestBody AdminLoginRequest request) {
        String token = adminAuthService.login(request.getUsername(), request.getPassword());
        return Map.of("token", token);
    }

    @PostMapping("/logout")
    public Map<String, String> logout(
            @RequestHeader(value = AdminAuthInterceptor.ADMIN_TOKEN_HEADER, required = false) String token,
            HttpServletRequest request) {
        adminAuthService.logout(token);
        return Map.of("message", "logged out");
    }
}
