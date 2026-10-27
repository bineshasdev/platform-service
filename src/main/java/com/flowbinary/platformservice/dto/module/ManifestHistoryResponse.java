package com.flowbinary.platformservice.dto.module;

import com.fasterxml.jackson.databind.JsonNode;
import com.flowbinary.platformservice.model.MenuModuleManifest;

import java.time.Instant;

public record ManifestHistoryResponse(Instant receivedAt, String via, String clientId, String outcome, String version,
                                      String contentHash, JsonNode errors, JsonNode summary) {

    public static ManifestHistoryResponse of(MenuModuleManifest m) {
        return new ManifestHistoryResponse(m.getReceivedAt(), m.getReceivedVia(), m.getClientId(), m.getOutcome().name(),
                m.getVersion(), m.getContentHash(), m.getErrors(), m.getSummary());
    }
}
