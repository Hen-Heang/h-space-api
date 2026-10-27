package com.henheang.securityapi.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.henheang.securityapi.domain.User;
import com.henheang.securityapi.repository.SignupInviteRepository;
import com.henheang.securityapi.repository.UserRepository;
import com.henheang.securityapi.service.EmailService;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Invite-only sign-up and the owner-admin rule, end to end. Runs with sign-up invite-only (the test
 * profile otherwise opens it) and a fixed owner email. Not transactional: the email-code step runs
 * in its own transaction and must see committed rows, so every test uses fresh addresses instead of
 * rolling back.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@TestPropertySource(
        properties = {
            "app.signup.mode=invite-only",
            "app.owner-email=" + InviteOnlySignupIntegrationTest.OWNER
        })
class InviteOnlySignupIntegrationTest {

    static final String OWNER = "owner-invite-test@example.com";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private SignupInviteRepository signupInviteRepository;

    @MockitoBean private EmailService emailService;

    private static final AtomicInteger IP_SUFFIX = new AtomicInteger(1);
    private final AtomicReference<String> lastLoginCode = new AtomicReference<>();

    @BeforeEach
    void stubEmailService() {
        when(emailService.sendVerificationEmail(any(), any(), any())).thenReturn(true);
        when(emailService.sendInviteEmail(any(), any())).thenReturn(true);
        when(emailService.sendLoginCodeEmail(any(), any(), anyLong()))
                .thenAnswer(
                        invocation -> {
                            lastLoginCode.set(invocation.getArgument(1));
                            return true;
                        });
    }

    private RequestPostProcessor uniqueClientIp() {
        String ip = "10.21.22." + IP_SUFFIX.getAndIncrement();
        return request -> {
            request.setRemoteAddr(ip);
            return request;
        };
    }

    private MockHttpServletRequestBuilder jsonPost(String uri, Object body, RequestPostProcessor ip)
            throws Exception {
        return post(uri)
                .with(ip)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body));
    }

    private String uniqueEmail() {
        return "invitee-" + UUID.randomUUID() + "@example.com";
    }

    private Map<String, String> signUp(String email) {
        return Map.of(
                "name",
                "Jane Doe",
                "email",
                email,
                "password",
                "Password123!",
                "username",
                "u" + UUID.randomUUID().toString().replace("-", "").substring(0, 16));
    }

    // Signs in with an emailed code and returns the access token.
    @SuppressWarnings("unchecked")
    private String signInWithEmailCode(String email, RequestPostProcessor ip) throws Exception {
        mockMvc.perform(jsonPost("/api/auth/email-code/request", Map.of("email", email), ip))
                .andExpect(status().isOk());
        String body =
                mockMvc.perform(
                                jsonPost(
                                        "/api/auth/email-code/verify",
                                        Map.of("email", email, "code", lastLoginCode.get()),
                                        ip))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.data.accessToken", notNullValue()))
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        Map<String, Object> data =
                (Map<String, Object>) objectMapper.readValue(body, Map.class).get("data");
        return (String) data.get("accessToken");
    }

    @Test
    void signup_withoutAnInvite_isRefused() throws Exception {
        mockMvc.perform(jsonPost("/api/auth/signup", signUp(uniqueEmail()), uniqueClientIp()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status.code").value(1104));
    }

    @Test
    void emailCode_forAnUninvitedAddress_sendsNothing() throws Exception {
        String email = uniqueEmail();

        mockMvc.perform(
                        jsonPost(
                                "/api/auth/email-code/request",
                                Map.of("email", email),
                                uniqueClientIp()))
                .andExpect(status().isOk());

        verify(emailService, never()).sendLoginCodeEmail(any(), any(), anyLong());
        assertThat(userRepository.findByEmail(email)).isEmpty();
    }

    @Test
    void owner_becomesAdmin_invitesSomeone_whoCanThenSignUpOnce() throws Exception {
        RequestPostProcessor ip = uniqueClientIp();

        // The owner needs no invite, and is admin as soon as they're signed in.
        String ownerToken = signInWithEmailCode(OWNER, ip);
        User owner = userRepository.findByEmail(OWNER).orElseThrow();
        assertThat(owner.getRoles()).anyMatch(role -> role.getName().equals("ROLE_ADMIN"));
        // ...and that's what a client sees, to decide whether to show admin screens.
        mockMvc.perform(
                        get("/api/auth/user")
                                .with(ip)
                                .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.roles", hasItem("ROLE_ADMIN")));

        String invitee = uniqueEmail();
        mockMvc.perform(
                        jsonPost("/api/admin/invites", Map.of("email", invitee.toUpperCase()), ip)
                                .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value(invitee))
                .andExpect(jsonPath("$.data.pending").value(true))
                .andExpect(jsonPath("$.data.emailSent").value(true));
        verify(emailService).sendInviteEmail(any(), any());

        mockMvc.perform(
                        get("/api/admin/invites")
                                .with(ip)
                                .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk());

        // The invite lets exactly one account be created.
        mockMvc.perform(jsonPost("/api/auth/signup", signUp(invitee), ip))
                .andExpect(status().isOk());
        assertThat(signupInviteRepository.findFirstByEmailAndUsedAtIsNull(invitee)).isEmpty();
    }

    @Test
    void invites_areAdminOnly() throws Exception {
        RequestPostProcessor ip = uniqueClientIp();
        String ownerToken = signInWithEmailCode(OWNER, ip);
        String invitee = uniqueEmail();
        mockMvc.perform(
                        jsonPost("/api/admin/invites", Map.of("email", invitee), ip)
                                .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk());

        // A regular account (created from that invite) can't send invites.
        String userToken = signInWithEmailCode(invitee, ip);
        mockMvc.perform(
                        jsonPost("/api/admin/invites", Map.of("email", uniqueEmail()), ip)
                                .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status.code").value(1006));
    }
}
