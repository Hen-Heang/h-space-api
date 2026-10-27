package com.henheang.securityapi.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.henheang.securityapi.domain.AuthProvider;
import com.henheang.securityapi.domain.User;
import com.henheang.securityapi.repository.UserRepository;
import com.henheang.securityapi.security.JwtTokenProvider;
import com.henheang.securityapi.security.supabase.SupabaseAuthUser;
import com.henheang.securityapi.security.supabase.SupabaseAuthUserReader;
import com.henheang.securityapi.security.supabase.SupabaseUserImportReport;
import com.henheang.securityapi.service.SupabaseUserImportService;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * Runs the real reader SQL and import against the test Postgres. A minimal stand-in for Supabase's
 * {@code auth} schema (only the columns the reader uses) is created inside the test transaction, so
 * Postgres's transactional DDL rolls it back with everything else.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class SupabaseUserImportIntegrationTest {

    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private SupabaseUserImportService importService;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JwtTokenProvider jwtTokenProvider;
    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    private SupabaseAuthUserReader reader;

    @BeforeEach
    void createSupabaseStandIn() {
        jdbcTemplate.execute("create schema auth");
        jdbcTemplate.execute(
                "create table auth.users (id uuid primary key, email text, encrypted_password"
                        + " text, email_confirmed_at timestamptz, raw_user_meta_data jsonb,"
                        + " created_at timestamptz, deleted_at timestamptz, is_anonymous boolean)");
        jdbcTemplate.execute(
                "create table auth.identities (user_id uuid, provider text, provider_id text)");
        jdbcTemplate.execute("create table public.kori_profiles (id uuid primary key)");
        reader = new SupabaseAuthUserReader(jdbcTemplate);
    }

    private UUID supabaseUser(
            String email, String passwordHash, boolean confirmed, String metadata, boolean hSpace) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                "insert into auth.users (id, email, encrypted_password, email_confirmed_at,"
                        + " raw_user_meta_data, created_at, is_anonymous) values (?, ?, ?, case"
                        + " when ? then now() end, ?::jsonb, now(), false)",
                id,
                email,
                passwordHash,
                confirmed,
                metadata);
        if (hSpace) {
            jdbcTemplate.update("insert into public.kori_profiles (id) values (?)", id);
        }
        return id;
    }

    private String uniqueEmail() {
        return "supabase-" + UUID.randomUUID() + "@example.com";
    }

    @Test
    void reader_returnsOnlyLiveHSpaceAccounts_withNameAndGoogleSubject() {
        String email = uniqueEmail();
        UUID hSpaceUser = supabaseUser(email, null, true, "{\"full_name\":\"Jane Doe\"}", true);
        jdbcTemplate.update(
                "insert into auth.identities values (?, 'google', 'google-sub-123')", hSpaceUser);
        supabaseUser(uniqueEmail(), null, true, "{}", false); // another app's user
        UUID deleted = supabaseUser(uniqueEmail(), null, true, "{}", true);
        jdbcTemplate.update("update auth.users set deleted_at = now() where id = ?", deleted);
        UUID anonymous = supabaseUser(uniqueEmail(), null, false, "{}", true);
        jdbcTemplate.update("update auth.users set is_anonymous = true where id = ?", anonymous);

        List<SupabaseAuthUser> users = reader.readHSpaceUsers();

        assertThat(users).extracting(SupabaseAuthUser::id).containsExactly(hSpaceUser);
        SupabaseAuthUser user = users.get(0);
        assertThat(user.email()).isEqualTo(email);
        assertThat(user.name()).isEqualTo("Jane Doe");
        assertThat(user.googleSubject()).isEqualTo("google-sub-123");
        assertThat(user.emailConfirmed()).isTrue();
    }

    @Test
    void import_keepsTheUuidAndTheOriginalPasswordStillSignsIn() throws Exception {
        String email = uniqueEmail();
        UUID id =
                supabaseUser(
                        email,
                        passwordEncoder.encode("Original-Pass-1"),
                        true,
                        "{\"display_name\":\"Heang\"}",
                        true);

        SupabaseUserImportReport report =
                importService.importUsers(reader.readHSpaceUsers(), false);

        assertThat(report.imported()).containsExactly(email);
        User user = userRepository.findById(id).orElseThrow();
        assertThat(user.getEmail()).isEqualTo(email);
        assertThat(user.getName()).isEqualTo("Heang");
        assertThat(user.getProvider()).isEqualTo(AuthProvider.LOCAL);
        assertThat(user.getEmailVerified()).isTrue();
        assertThat(user.getRoles()).extracting("name").containsExactly("ROLE_USER");

        String body =
                mockMvc.perform(
                                post("/api/auth/login")
                                        .with(
                                                r -> {
                                                    r.setRemoteAddr("10.20.30.1");
                                                    return r;
                                                })
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                objectMapper.writeValueAsString(
                                                        Map.of(
                                                                "email",
                                                                email,
                                                                "password",
                                                                "Original-Pass-1"))))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        @SuppressWarnings("unchecked")
        Map<String, Object> data =
                (Map<String, Object>) objectMapper.readValue(body, Map.class).get("data");
        assertThat(jwtTokenProvider.getUserIdFromToken((String) data.get("accessToken")))
                .isEqualTo(id);
    }

    @Test
    void import_withLinkedGoogle_makesAGoogleAccountWithoutPassword() {
        String email = uniqueEmail();
        UUID id = supabaseUser(email, "", true, "{}", true);
        jdbcTemplate.update("insert into auth.identities values (?, 'google', 'g-42')", id);

        SupabaseUserImportReport report =
                importService.importUsers(reader.readHSpaceUsers(), false);

        assertThat(report.withoutPassword()).containsExactly(email);
        User user = userRepository.findById(id).orElseThrow();
        assertThat(user.getProvider()).isEqualTo(AuthProvider.GOOGLE);
        assertThat(user.getProviderId()).isEqualTo("g-42");
        assertThat(user.getPassword()).isNull();
    }

    @Test
    void import_isIdempotent() {
        String email = uniqueEmail();
        supabaseUser(email, null, true, "{}", true);

        importService.importUsers(reader.readHSpaceUsers(), false);
        SupabaseUserImportReport second =
                importService.importUsers(reader.readHSpaceUsers(), false);

        assertThat(second.imported()).isEmpty();
        assertThat(second.alreadyImported()).containsExactly(email);
    }

    @Test
    void import_leavesAnEmailConflictUntouched() {
        String email = uniqueEmail();
        User existing = new User();
        existing.setEmail(email.toUpperCase(java.util.Locale.ROOT));
        existing.setName("Spring test account");
        existing.setProvider(AuthProvider.LOCAL);
        existing = userRepository.saveAndFlush(existing);
        UUID supabaseId = supabaseUser(email, null, true, "{}", true);

        SupabaseUserImportReport report =
                importService.importUsers(reader.readHSpaceUsers(), false);

        assertThat(report.conflicts()).containsExactly(email);
        assertThat(userRepository.findById(supabaseId)).isEmpty();
        assertThat(userRepository.findById(existing.getId()).orElseThrow().getName())
                .isEqualTo("Spring test account");
    }

    @Test
    void dryRun_writesNothing() {
        String email = uniqueEmail();
        UUID id = supabaseUser(email, null, true, "{}", true);

        SupabaseUserImportReport report = importService.importUsers(reader.readHSpaceUsers(), true);

        assertThat(report.dryRun()).isTrue();
        assertThat(report.imported()).containsExactly(email);
        assertThat(userRepository.findById(id)).isEmpty();
    }

    @Test
    void bcryptHashOrNull_keepsOnlyRealBcryptHashes() {
        assertThat(SupabaseUserImportServiceImpl.bcryptHashOrNull(null)).isNull();
        assertThat(SupabaseUserImportServiceImpl.bcryptHashOrNull("")).isNull();
        assertThat(SupabaseUserImportServiceImpl.bcryptHashOrNull("plain-text")).isNull();
        String hash = passwordEncoder.encode("x");
        assertThat(SupabaseUserImportServiceImpl.bcryptHashOrNull(hash)).isEqualTo(hash);
    }
}
