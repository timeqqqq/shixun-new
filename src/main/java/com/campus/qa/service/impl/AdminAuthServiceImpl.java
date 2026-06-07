package com.campus.qa.service.impl;

import com.campus.qa.service.AdminAuthService;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class AdminAuthServiceImpl implements AdminAuthService {

    private final String configuredUsername;
    private final String configuredPassword;
    private final Duration tokenTtl;
    private final Map<String, Instant> tokenStore = new ConcurrentHashMap<>();

    public AdminAuthServiceImpl(
            @Value("${app.admin.username:admin}") String configuredUsername,
            @Value("${app.admin.password:123456}") String configuredPassword,
            @Value("${app.admin.token-ttl-hours:8}") long tokenTtlHours) {
        this.configuredUsername = configuredUsername;
        this.configuredPassword = configuredPassword;
        this.tokenTtl = Duration.ofHours(Math.max(1, tokenTtlHours));
    }

    @Override
    public String login(String username, String password) {
        if (!configuredUsername.equals(username) || !configuredPassword.equals(password)) {
            throw new IllegalArgumentException("invalid admin credentials");
        }
        String token = UUID.randomUUID().toString().replace("-", "");
        tokenStore.put(token, Instant.now().plus(tokenTtl));
        return token;
    }

    @Override
    public void logout(String token) {
        if (token != null) {
            tokenStore.remove(token);
        }
    }

    @Override
    public boolean isValid(String token) {
        if (token == null || token.isBlank()) {
            return false;
        }
        Instant expiresAt = tokenStore.get(token);
        if (expiresAt == null) {
            return false;
        }
        if (expiresAt.isBefore(Instant.now())) {
            tokenStore.remove(token);
            return false;
        }
        return true;
    }
}
