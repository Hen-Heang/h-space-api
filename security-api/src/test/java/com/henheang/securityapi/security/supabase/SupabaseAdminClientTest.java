package com.henheang.securityapi.security.supabase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.headerDoesNotExist;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.henheang.commonapi.components.common.api.StatusCode;
import com.henheang.securityapi.exception.BusinessException;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class SupabaseAdminClientTest {

    private static final String URL = "https://project.supabase.co";
    private static final UUID USER_ID = UUID.fromString("5b3f2c1e-8a4d-4f6b-9c2e-1d7a0e9b3c45");

    private MockRestServiceServer server;

    private SupabaseAdminClient client(String serviceRoleKey) {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        // Trailing slash on the configured URL must not produce "//auth".
        return new SupabaseAdminClient(builder, URL + "/", serviceRoleKey);
    }

    @Test
    void userExists_trueOn200_withLegacyKeyInBothHeaders() {
        SupabaseAdminClient client = client("legacy.service.role.jwt");
        server.expect(requestTo(URL + "/auth/v1/admin/users/" + USER_ID))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("apikey", "legacy.service.role.jwt"))
                .andExpect(header("Authorization", "Bearer legacy.service.role.jwt"))
                .andRespond(withSuccess());

        assertThat(client.userExists(USER_ID)).isTrue();
        server.verify();
    }

    @Test
    void userExists_falseOn404_withSecretKeyOnlyInApikey() {
        SupabaseAdminClient client = client("sb_secret_abc");
        server.expect(requestTo(URL + "/auth/v1/admin/users/" + USER_ID))
                .andExpect(header("apikey", "sb_secret_abc"))
                .andExpect(headerDoesNotExist("Authorization"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThat(client.userExists(USER_ID)).isFalse();
        server.verify();
    }

    @Test
    void userExists_otherErrorsBecomeBadGateway() {
        SupabaseAdminClient client = client("key");
        server.expect(requestTo(URL + "/auth/v1/admin/users/" + USER_ID))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        assertThatThrownBy(() -> client.userExists(USER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(StatusCode.BAD_GATEWAY);
    }

    @Test
    void createUser_postsTheSpringIdAsAConfirmedUser() {
        SupabaseAdminClient client = client("key");
        server.expect(requestTo(URL + "/auth/v1/admin/users"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(
                        content()
                                .json(
                                        "{\"id\":\""
                                                + USER_ID
                                                + "\",\"email\":\"jane@example.com\","
                                                + "\"email_confirm\":true}"))
                .andRespond(withSuccess());

        client.createUser(USER_ID, "jane@example.com");
        server.verify();
    }

    @Test
    void createUser_emailTakenByAnotherIdIsAConflict() {
        SupabaseAdminClient client = client("key");
        server.expect(requestTo(URL + "/auth/v1/admin/users"))
                .andRespond(withStatus(HttpStatus.UNPROCESSABLE_ENTITY));

        assertThatThrownBy(() -> client.createUser(USER_ID, "jane@example.com"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(StatusCode.CONFLICT);
    }
}
