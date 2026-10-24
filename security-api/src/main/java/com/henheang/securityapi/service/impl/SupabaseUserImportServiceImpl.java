package com.henheang.securityapi.service.impl;

import com.henheang.securityapi.domain.AuthProvider;
import com.henheang.securityapi.domain.Role;
import com.henheang.securityapi.security.supabase.SupabaseAuthUser;
import com.henheang.securityapi.security.supabase.SupabaseUserImportReport;
import com.henheang.securityapi.service.RoleService;
import com.henheang.securityapi.service.SupabaseUserImportService;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Writes with plain SQL rather than through the User entity: its id is {@code @GeneratedValue}, and
 * keeping Supabase's UUID is the whole point of the import.
 */
@Service
@RequiredArgsConstructor
public class SupabaseUserImportServiceImpl implements SupabaseUserImportService {

    // Supabase Auth (Go's bcrypt) writes $2a$; BCryptPasswordEncoder accepts $2a$/$2b$/$2y$.
    private static final Pattern BCRYPT_HASH =
            Pattern.compile("^\\$2[aby]?\\$\\d\\d\\$[./0-9A-Za-z]{53}$");

    private final JdbcTemplate jdbcTemplate;
    private final RoleService roleService;

    // All-or-nothing: a failure part-way leaves no half-imported batch, and
    // since the run is idempotent it can simply be repeated.
    @Override
    @Transactional
    public SupabaseUserImportReport importUsers(List<SupabaseAuthUser> users, boolean dryRun) {
        List<String> imported = new ArrayList<>();
        List<String> alreadyImported = new ArrayList<>();
        List<String> conflicts = new ArrayList<>();
        List<String> withoutPassword = new ArrayList<>();
        Role userRole = dryRun ? null : roleService.getOrCreateRole("ROLE_USER");

        for (SupabaseAuthUser user : users) {
            if (idExists(user.id())) {
                alreadyImported.add(user.email());
                continue;
            }
            if (emailTakenByAnotherAccount(user.email())) {
                conflicts.add(user.email());
                continue;
            }
            String password = bcryptHashOrNull(user.encryptedPassword());
            if (password == null) {
                withoutPassword.add(user.email());
            }
            if (!dryRun) {
                insertUser(user, password, userRole);
            }
            imported.add(user.email());
        }
        return new SupabaseUserImportReport(
                dryRun, imported, alreadyImported, conflicts, withoutPassword);
    }

    // Counts soft-deleted rows too: the id is the primary key either way.
    private boolean idExists(UUID id) {
        Integer count =
                jdbcTemplate.queryForObject(
                        "select count(*) from users where id = ?", Integer.class, id);
        return count != null && count > 0;
    }

    // Only live rows: the unique email index ignores soft-deleted accounts.
    private boolean emailTakenByAnotherAccount(String email) {
        Integer count =
                jdbcTemplate.queryForObject(
                        "select count(*) from users where lower(email) = lower(?) and deleted_at is"
                                + " null",
                        Integer.class,
                        email);
        return count != null && count > 0;
    }

    static String bcryptHashOrNull(String encryptedPassword) {
        return StringUtils.hasText(encryptedPassword)
                        && BCRYPT_HASH.matcher(encryptedPassword).matches()
                ? encryptedPassword
                : null;
    }

    // A linked Google identity makes the account GOOGLE so Google sign-in
    // (which refuses to take over non-Google accounts) keeps working; the
    // bcrypt hash is kept either way, so a password set on Supabase still works.
    private void insertUser(SupabaseAuthUser user, String password, Role userRole) {
        boolean google = StringUtils.hasText(user.googleSubject());
        Timestamp createdAt =
                Timestamp.from(user.createdAt() != null ? user.createdAt() : Instant.now());
        Timestamp now = Timestamp.from(Instant.now());

        jdbcTemplate.update(
                "insert into users (id, email, password, name, image_url, email_verified,"
                        + " provider, provider_id, failed_login_attempts, mfa_enabled, created_at,"
                        + " updated_at, version) values (?, ?, ?, ?, ?, ?, ?, ?, 0, false, ?, ?,"
                        + " 0)",
                user.id(),
                user.email(),
                password,
                truncate(user.name(), 255),
                truncate(user.avatarUrl(), 1024),
                user.emailConfirmed(),
                (google ? AuthProvider.GOOGLE : AuthProvider.LOCAL).name(),
                google ? user.googleSubject() : null,
                createdAt,
                now);
        jdbcTemplate.update(
                "insert into user_roles (id, user_id, role_id, created_at, updated_at, version)"
                        + " values (?, ?, ?, ?, ?, 0)",
                UUID.randomUUID(),
                user.id(),
                userRole.getId(),
                now,
                now);
    }

    private static String truncate(String value, int max) {
        return value != null && value.length() > max ? value.substring(0, max) : value;
    }
}
