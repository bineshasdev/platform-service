package com.flowbinary.platformservice.service;

import com.flowbinary.platformservice.model.MenuItem;
import com.flowbinary.platformservice.model.MenuLifecycle;
import com.flowbinary.platformservice.repository.MenuItemRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Keeps {@code lifecycle_status} truthful and writes the audit trail for
 * activation and expiry. Correctness never depends on it: what users see is
 * decided from the time window at request time.
 *
 * <p>Safe to run on every instance at once. Rows are claimed with
 * {@code FOR UPDATE SKIP LOCKED}, so an instance never waits on, or repeats,
 * a transition another instance is making, and each transition is audited once.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TemporaryMenuLifecycleService {

    static final int BATCH = 200;

    private final MenuItemRepository items;
    private final AuditService       audit;
    private final MenuCatalogEvents  events;
    private final MenuMetrics        metrics;

    /** @return the number of transitions this call made */
    @Transactional
    public int transitionDue(Instant now) {
        int made = 0;
        // Expire first: a window that opened and closed unseen goes straight to EXPIRED.
        List<MenuItem> expiring = items.lockDueToExpire(now, PageRequest.of(0, BATCH));
        for (MenuItem m : expiring) {
            move(m, MenuLifecycle.EXPIRED, "MENU_TEMP_EXPIRED");
            made++;
        }
        List<MenuItem> activating = items.lockDueToActivate(now, PageRequest.of(0, BATCH));
        for (MenuItem m : activating) {
            move(m, MenuLifecycle.ACTIVE, "MENU_TEMP_ACTIVATED");
            made++;
        }
        if (made > 0) log.info("Temporary menus: {} expired, {} activated", expiring.size(), activating.size());
        // The triggers already moved the version counters; this just refreshes this and other instances now.
        java.util.stream.Stream.concat(expiring.stream(), activating.stream())
                .map(m -> m.getApplication().getCode() + "|" + (m.getTenant() == null ? "" : m.getTenant().getId()))
                .distinct()
                .forEach(k -> {
                    String[] p = k.split("\\|", -1);
                    events.changed(p[0], p[1].isEmpty() ? null : java.util.UUID.fromString(p[1]));
                });
        return made;
    }

    private void move(MenuItem m, MenuLifecycle to, String action) {
        MenuLifecycle from = m.getLifecycleStatus();
        m.setLifecycleStatus(to);
        metrics.count("menu.temporary.transition", "to", to.name());
        audit.record(new AuditService.Entry(m.getTenant() == null ? null : m.getTenant().getId(), null, action,
                "MENU_TEMPORARY", m.getCode(), state(from, m), state(to, m), null, "SCHEDULER"));
    }

    private static Map<String, Object> state(MenuLifecycle status, MenuItem m) {
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("status", status.name());
        s.put("startsAt", m.getStartsAt().toString());
        s.put("endsAt", m.getEndsAt().toString());
        return s;
    }
}
