package com.henheang.securityapi.security.supabase;

import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Reads H-Space's users from the Supabase project's {@code auth} schema over a direct Postgres
 * connection - the admin API never returns password hashes, so this needs the database itself.
 *
 * <p>The Supabase project is shared with other apps (Orbit/Luyra), so only accounts with an H-Space
 * profile ({@code public.kori_profiles}) are read. Anonymous, deleted and email-less accounts are
 * skipped: none of them can sign in here.
 */
public class SupabaseAuthUserReader {

    static final String H_SPACE_USERS_SQL =
            """
            select u.id,
                   u.email,
                   u.encrypted_password,
                   u.email_confirmed_at is not null as email_confirmed,
                   coalesce(u.raw_user_meta_data ->> 'display_name',
                            u.raw_user_meta_data ->> 'full_name',
                            u.raw_user_meta_data ->> 'name') as name,
                   coalesce(u.raw_user_meta_data ->> 'avatar_url',
                            u.raw_user_meta_data ->> 'picture') as avatar_url,
                   (select i.provider_id
                      from auth.identities i
                     where i.user_id = u.id and i.provider = 'google'
                     limit 1) as google_subject,
                   u.created_at
              from auth.users u
             where u.deleted_at is null
               and coalesce(u.is_anonymous, false) = false
               and u.email is not null
               and exists (select 1 from public.kori_profiles p where p.id = u.id)
             order by u.created_at
            """;

    private final JdbcTemplate supabaseJdbc;

    public SupabaseAuthUserReader(JdbcTemplate supabaseJdbc) {
        this.supabaseJdbc = supabaseJdbc;
    }

    public List<SupabaseAuthUser> readHSpaceUsers() {
        return supabaseJdbc.query(
                H_SPACE_USERS_SQL,
                (rs, rowNum) -> {
                    Timestamp createdAt = rs.getTimestamp("created_at");
                    return new SupabaseAuthUser(
                            rs.getObject("id", UUID.class),
                            rs.getString("email"),
                            rs.getString("encrypted_password"),
                            rs.getBoolean("email_confirmed"),
                            rs.getString("name"),
                            rs.getString("avatar_url"),
                            rs.getString("google_subject"),
                            createdAt != null ? createdAt.toInstant() : null);
                });
    }
}
