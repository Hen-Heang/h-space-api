package com.henheang.securityapi.service;

import org.springframework.stereotype.Service;

@Service
public interface EmailService {

    boolean sendPasswordResetEmail(String email, String name, String resetUrl);

    boolean sendVerificationEmail(String email, String name, String verificationUrl);

    boolean sendAccountLockedEmail(String email, String name, String unlockUrl);

    boolean sendAccountUnlockedEmail(String email, String name);
}
