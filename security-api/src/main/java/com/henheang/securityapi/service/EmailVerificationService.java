package com.henheang.securityapi.service;

import com.henheang.securityapi.domain.User;

public interface EmailVerificationService {

    void sendVerificationEmail(User user);

    void resendVerificationEmail(String email);

    void verifyEmail(String token);
}
