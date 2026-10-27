package com.flowbinary.platformservice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowbinary.platformservice.config.AppProperties;
import com.flowbinary.platformservice.model.ModuleHealth;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class MenuModuleSyncHelpersTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void sameContentHashesTheSameWhateverTheFieldOrder() throws Exception {
        var a = mapper.readTree("{\"schemaVersion\":1,\"module\":\"b\",\"items\":[{\"key\":\"x\",\"label\":\"X\"}],\"version\":\"1\"}");
        var b = mapper.readTree("{\"version\":\"1\",\"items\":[{\"label\":\"X\",\"key\":\"x\"}],\"module\":\"b\",\"schemaVersion\":1}");
        assertThat(MenuModuleSyncService.sha256(MenuModuleSyncService.canonical(a)))
                .isEqualTo(MenuModuleSyncService.sha256(MenuModuleSyncService.canonical(b)));
    }

    @Test
    void differentContentOrItemOrderHashesDifferently() throws Exception {
        var a = mapper.readTree("{\"items\":[{\"key\":\"x\"},{\"key\":\"y\"}]}");
        var b = mapper.readTree("{\"items\":[{\"key\":\"y\"},{\"key\":\"x\"}]}");   // array order is meaningful
        var c = mapper.readTree("{\"items\":[{\"key\":\"x\"},{\"key\":\"z\"}]}");
        String ha = MenuModuleSyncService.sha256(MenuModuleSyncService.canonical(a));
        assertThat(ha).isNotEqualTo(MenuModuleSyncService.sha256(MenuModuleSyncService.canonical(b)));
        assertThat(ha).isNotEqualTo(MenuModuleSyncService.sha256(MenuModuleSyncService.canonical(c)));
    }

    @Test
    void backoffDoublesAndIsCappedAtThePullInterval() {
        AppProperties.MenuProperties.ModuleProperties cfg = new AppProperties.MenuProperties.ModuleProperties();
        cfg.setBackoffBase(Duration.ofMinutes(1));
        cfg.setPullInterval(Duration.ofMinutes(30));
        assertThat(MenuModuleSyncService.backoff(1, cfg)).isEqualTo(Duration.ofMinutes(1));
        assertThat(MenuModuleSyncService.backoff(2, cfg)).isEqualTo(Duration.ofMinutes(2));
        assertThat(MenuModuleSyncService.backoff(4, cfg)).isEqualTo(Duration.ofMinutes(8));
        assertThat(MenuModuleSyncService.backoff(10, cfg)).isEqualTo(Duration.ofMinutes(30));
        assertThat(MenuModuleSyncService.backoff(500, cfg)).isEqualTo(Duration.ofMinutes(30));
    }

    @Test
    void healthGaugeOrdersUpAboveStaleAboveDown() {
        assertThat(MenuModuleSyncService.healthValue(ModuleHealth.UP))
                .isGreaterThan(MenuModuleSyncService.healthValue(ModuleHealth.STALE));
        assertThat(MenuModuleSyncService.healthValue(ModuleHealth.STALE))
                .isGreaterThan(MenuModuleSyncService.healthValue(ModuleHealth.DOWN));
    }
}
