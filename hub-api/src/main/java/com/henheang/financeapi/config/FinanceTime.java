package com.henheang.financeapi.config;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import org.springframework.stereotype.Component;

/**
 * Luyra's app time zone (lib/finance-cron-time.ts): "today" and "this month" are always Asia/Seoul,
 * whatever zone the server or the database session runs in. Finance code asks this component
 * instead of calling LocalDate.now() or relying on SQL current_date.
 *
 * <p>Deliberately not a java.time.Clock bean: security-api's services build their own UTC clock,
 * and a context-wide Clock bean could change how they are wired.
 */
@Component
public class FinanceTime {

    public static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

    private final Clock clock = Clock.system(ZONE);

    public LocalDate today() {
        return LocalDate.now(clock);
    }
}
