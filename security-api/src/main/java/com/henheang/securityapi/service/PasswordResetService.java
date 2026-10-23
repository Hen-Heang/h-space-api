package com.henheang.securityapi.service;

public interface PasswordResetService {

    void createPasswordResetTokenEmail(String email);

    boolean validatePasswordResetToken(String token);

    void resetPassword(String token, String newPassword);
}
