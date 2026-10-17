package com.flowbinary.platformservice.storage;

import com.flowbinary.platformservice.model.StorageBackend;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Holds all registered {@link StorageProvider} beans and dispatches to the
 * correct one based on the tenant's configured {@link StorageBackend}.
 *
 * <p>Each provider declares which backend it implements via {@link StorageProvider#backend()}.
 * Adding a new backend only requires implementing {@code StorageProvider} and registering
 * it as a Spring bean — no registry change needed.
 */
@Slf4j
@Component
public class StorageProviderRegistry {

    private final Map<StorageBackend, StorageProvider> providers;

    public StorageProviderRegistry(List<StorageProvider> providerList) {
        providers = new EnumMap<>(StorageBackend.class);
        for (StorageProvider p : providerList) {
            providers.put(p.backend(), p);
            log.info("Registered storage provider: {} → {}", p.backend(), p.getClass().getSimpleName());
        }
    }

    /**
     * Returns the provider for the given backend.
     *
     * @throws IllegalStateException if no provider is registered for the backend
     */
    public StorageProvider get(StorageBackend backend) {
        StorageProvider provider = providers.get(backend);
        if (provider == null) {
            throw new IllegalStateException(
                "No StorageProvider registered for backend: " + backend +
                ". Available: " + providers.keySet());
        }
        return provider;
    }
}
