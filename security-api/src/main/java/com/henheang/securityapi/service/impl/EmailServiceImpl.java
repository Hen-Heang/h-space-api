package com.henheang.securityapi.service.impl;

import com.henheang.securityapi.service.EmailService;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailServiceImpl implements EmailService {

    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;

    @Value("${spring.mail.username}")
    private String fromEmail;

    @Value("${app.email.from-name}")
    private String fromName;

    @Override
    public boolean sendPasswordResetEmail(String email, String name, String resetUrl) {
        Context context = new Context();
        context.setVariable("name", name != null ? name : "User");
        context.setVariable("resetUrl", resetUrl);
        return sendHtmlEmail(email, "Reset Your AuthHub Password", "password-reset-email", context);
    }

    @Override
    public boolean sendVerificationEmail(String email, String name, String verificationUrl) {
        Context context = new Context();
        context.setVariable("name", name != null ? name : "User");
        context.setVariable("verificationUrl", verificationUrl);
        return sendHtmlEmail(
                email, "Verify Your AuthHub Email Address", "email-verification", context);
    }

    @Override
    public boolean sendAccountLockedEmail(String email, String name, String unlockUrl) {
        Context context = new Context();
        context.setVariable("name", name != null ? name : "User");
        context.setVariable("unlockUrl", unlockUrl);
        return sendHtmlEmail(
                email, "Your AuthHub Account Has Been Locked", "account-locked", context);
    }

    @Override
    public boolean sendAccountUnlockedEmail(String email, String name) {
        Context context = new Context();
        context.setVariable("name", name != null ? name : "User");
        return sendHtmlEmail(
                email, "Your AuthHub Account Has Been Unlocked", "account-unlocked", context);
    }

    private boolean sendHtmlEmail(String email, String subject, String template, Context context) {
        try {
            log.info("Preparing to send '{}' email to: {}", template, email);

            if (email == null || email.trim().isEmpty()) {
                log.error("Email address is null or empty");
                return false;
            }

            if (fromEmail == null || fromEmail.trim().isEmpty()) {
                log.error(
                        "From email is not configured. Please set MAIL_USERNAME environment variable");
                return false;
            }

            String htmlContent;
            try {
                htmlContent = templateEngine.process(template, context);
                log.debug("Email template '{}' processed successfully", template);
            } catch (Exception e) {
                log.error("Failed to process email template '{}': {}", template, e.getMessage(), e);
                return false;
            }

            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(fromEmail, fromName);
            helper.setTo(email);
            helper.setSubject(subject);
            helper.setText(htmlContent, true);

            mailSender.send(message);

            log.info("Email '{}' sent successfully to: {}", template, email);
            return true;

        } catch (MessagingException e) {
            log.error(
                    "Failed to send '{}' email to {} - MessagingException: {}",
                    template,
                    email,
                    e.getMessage(),
                    e);
            if (e.getCause() != null) {
                log.error("Cause: {}", e.getCause().getMessage());
            }
            return false;
        } catch (Exception e) {
            log.error(
                    "Unexpected error while sending '{}' email to {}: {}",
                    template,
                    email,
                    e.getMessage(),
                    e);
            return false;
        }
    }
}
