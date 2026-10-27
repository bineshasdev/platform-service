package com.flowbinary.platformservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Pulls due modules on the slow schedule. Runs on every instance; a module is
 * <em>leased</em> by pushing its {@code next_attempt_at} forward in the same
 * statement that selects it ({@code FOR UPDATE SKIP LOCKED}), so two instances
 * never pull the same module at once. The attempt then sets the real next time
 * (the pull interval, or a backoff after a failure).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MenuModulePullScheduler {

    static final Duration LEASE = Duration.ofMinutes(5);
    static final int BATCH = 10;

    private final JdbcTemplate jdbc;
    private final MenuModulePuller puller;

    @Scheduled(fixedDelayString = "${app.menu.modules.scan-interval-ms:60000}",
               initialDelayString = "${app.menu.modules.scan-initial-delay-ms:30000}")
    public void tick() {
        try {
            for (String code : claimDue(Instant.now())) {
                try {
                    puller.pull(code);
                } catch (RuntimeException e) {
                    log.warn("Pull of module {} failed: {}", code, e.getMessage());
                }
            }
        } catch (RuntimeException e) {
            log.warn("Module pull scan failed: {}", e.getMessage(), e);
        }
    }

    /** Leases up to {@link #BATCH} due modules and returns their codes. */
    List<String> claimDue(Instant now) {
        return jdbc.queryForList("""
                UPDATE menu_modules SET next_attempt_at = ?
                WHERE id IN (SELECT id FROM menu_modules
                             WHERE status = 'ACTIVE' AND manifest_url IS NOT NULL
                               AND (next_attempt_at IS NULL OR next_attempt_at <= ?)
                             ORDER BY next_attempt_at NULLS FIRST
                             LIMIT ? FOR UPDATE SKIP LOCKED)
                RETURNING code
                """, String.class, Timestamp.from(now.plus(LEASE)), Timestamp.from(now), BATCH);
    }
}
