package com.henheang.securityapi.config;

import com.henheang.securityapi.security.supabase.SupabaseAuthUser;
import com.henheang.securityapi.security.supabase.SupabaseAuthUserReader;
import com.henheang.securityapi.security.supabase.SupabaseUserImportReport;
import com.henheang.securityapi.service.SupabaseUserImportService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.stereotype.Component;

/**
 * One-shot command that imports H-Space's Supabase accounts, then exits. Only exists when started
 * with {@code --supabase.import.enabled=true}; dry-run unless {@code
 * --supabase.import.dry-run=false}. See docs/supabase-user-import.md.
 *
 * <p>The Supabase connection is built here, not as a bean, so it never competes with the
 * application's primary DataSource.
 */
@Component
@ConditionalOnProperty(name = "supabase.import.enabled", havingValue = "true")
@RequiredArgsConstructor
public class SupabaseUserImportRunner implements ApplicationRunner {

    private static final Logger logger = LoggerFactory.getLogger(SupabaseUserImportRunner.class);

    private final SupabaseUserImportService importService;
    private final ConfigurableApplicationContext context;

    @Value("${supabase.import.db-url:}")
    private String dbUrl;

    @Value("${supabase.import.db-username:}")
    private String dbUsername;

    @Value("${supabase.import.db-password:}")
    private String dbPassword;

    @Value("${supabase.import.dry-run:true}")
    private boolean dryRun;

    @Override
    public void run(ApplicationArguments args) {
        int exitCode = 0;
        try {
            if (dbUrl.isBlank()) {
                throw new IllegalStateException(
                        "Set SUPABASE_DB_URL (and SUPABASE_DB_USERNAME/SUPABASE_DB_PASSWORD) to"
                                + " the Supabase project's Postgres connection.");
            }
            List<SupabaseAuthUser> users;
            SingleConnectionDataSource source =
                    new SingleConnectionDataSource(dbUrl, dbUsername, dbPassword, true);
            try {
                users = new SupabaseAuthUserReader(new JdbcTemplate(source)).readHSpaceUsers();
            } finally {
                source.destroy();
            }
            logger.info("Read {} H-Space account(s) from Supabase", users.size());

            SupabaseUserImportReport report = importService.importUsers(users, dryRun);
            logger.info("Supabase user import: {}", report.summary());
            if (dryRun) {
                logger.info(
                        "Dry run - nothing was written. Re-run with"
                                + " --supabase.import.dry-run=false to import.");
            }
        } catch (RuntimeException e) {
            logger.error("Supabase user import failed: {}", e.getMessage(), e);
            exitCode = 1;
        }
        int code = exitCode;
        System.exit(SpringApplication.exit(context, () -> code));
    }
}
