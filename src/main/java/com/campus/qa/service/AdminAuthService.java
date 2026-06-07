package com.campus.qa.service;

public interface AdminAuthService {
    String login(String username, String password);

    void logout(String token);

    boolean isValid(String token);
}
