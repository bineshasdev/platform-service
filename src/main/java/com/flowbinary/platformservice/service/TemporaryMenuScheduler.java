package com.flowbinary.platformservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

/** Periodic trigger for {@link TemporaryMenuLifecycleService}. Runs on every instance. */
@Slf4j
@Component
@RequiredArgsConstructor
public class TemporaryMenuScheduler {

    private final TemporaryMenuLifecycleService lifecycle;

    @Scheduled(fixedDelayString = "${app.menu.scheduler-interval-ms:30000}",
               initialDelayString = "${app.menu.scheduler-initial-delay-ms:15000}")
    public void tick() {
        try {
            lifecycle.transitionDue(Instant.now());
        } catch (RuntimeException e) {
            // Visibility does not depend on this job, so a failure is logged, not fatal.
            log.warn("Temporary menu lifecycle scan failed: {}", e.getMessage(), e);
        }
    }
}
