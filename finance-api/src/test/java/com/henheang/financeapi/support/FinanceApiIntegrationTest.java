package com.henheang.financeapi.support;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.henheang.securityapi.domain.AuthProvider;
import com.henheang.securityapi.domain.User;
import com.henheang.securityapi.repository.UserRepository;
import com.henheang.securityapi.security.JwtTokenProvider;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

/**
 * Base for finance HTTP tests against the real Flyway schema (test profile, schema
 * finance_api_test). Each test gets two fresh synthetic users, Alice and Bob, with verified emails
 * and JWTs from security-api's JwtTokenProvider; everything rolls back after the test. Ownership is
 * what replaces Luyra's missing RLS, so most tests check that Bob can never see or change Alice's
 * rows.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
public abstract class FinanceApiIntegrationTest {

    @Autowired protected MockMvc mockMvc;
    @Autowired protected ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private JwtTokenProvider jwtTokenProvider;

    protected String aliceToken;
    protected String bobToken;

    @BeforeEach
    void createUsers() {
        aliceToken = jwtTokenProvider.generateToken(saveUser());
        bobToken = jwtTokenProvider.generateToken(saveUser());
    }

    private User saveUser() {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        User user = new User();
        user.setEmail("finance-" + suffix + "@example.com");
        user.setUsername("f" + suffix);
        user.setName("Finance Tester");
        user.setProvider(AuthProvider.LOCAL);
        user.setEmailVerified(true);
        return userRepository.saveAndFlush(user);
    }

    protected ResultActions send(MockHttpServletRequestBuilder request, String token, Object body)
            throws Exception {
        request.header("Authorization", "Bearer " + token);
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON)
                    .content(
                            body instanceof String json
                                    ? json
                                    : objectMapper.writeValueAsString(body));
        }
        return mockMvc.perform(request);
    }

    /** Sends the request, expects 200 and returns the envelope's data node. */
    protected JsonNode okData(MockHttpServletRequestBuilder request, String token, Object body)
            throws Exception {
        String json =
                send(request, token, body)
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        return objectMapper.readTree(json).path("data");
    }
}
