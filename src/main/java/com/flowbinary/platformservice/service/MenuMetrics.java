package com.flowbinary.platformservice.service;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.function.Supplier;

/**
 * One place for menu metrics and spans. {@link #observe} records a timer and a
 * trace span under the same name; {@link #count} bumps a counter. Falls back to
 * an in-memory registry when none is configured, so callers never null-check.
 *
 * <p>Names: {@code menu.resolve}, {@code menu.catalogue.compile},
 * {@code menu.catalogue.version.changed}, {@code menu.catalogue.invalidate},
 * {@code menu.publish}, {@code menu.temporary.transition}; cache hit/miss/size
 * come from {@code menu.catalogue} (Caffeine binder).
 */
@Component
public class MenuMetrics {

    private final MeterRegistry registry;
    private final ObservationRegistry observations;

    public MenuMetrics(ObjectProvider<MeterRegistry> registry, ObjectProvider<ObservationRegistry> observations) {
        this.registry = registry.getIfAvailable(SimpleMeterRegistry::new);
        this.observations = observations.getIfAvailable(() -> ObservationRegistry.NOOP);
    }

    public MeterRegistry registry() {
        return registry;
    }

    public <T> T observe(String name, Supplier<T> work, String... tagKeyValues) {
        Observation o = Observation.createNotStarted(name, observations);
        for (int i = 0; i + 1 < tagKeyValues.length; i += 2) o.lowCardinalityKeyValue(tagKeyValues[i], tagKeyValues[i + 1]);
        return o.observe(work);
    }

    private final java.util.concurrent.ConcurrentHashMap<String, java.util.concurrent.atomic.AtomicReference<Double>> gauges =
            new java.util.concurrent.ConcurrentHashMap<>();

    /** Sets a gauge, creating it on first use. */
    public void gauge(String name, double value, String... tagKeyValues) {
        String key = name + java.util.Arrays.toString(tagKeyValues);
        java.util.concurrent.atomic.AtomicReference<Double> ref = gauges.computeIfAbsent(key, k -> {
            java.util.concurrent.atomic.AtomicReference<Double> r = new java.util.concurrent.atomic.AtomicReference<>(value);
            io.micrometer.core.instrument.Gauge.builder(name, r, a -> a.get()).tags(tagKeyValues).register(registry);
            return r;
        });
        ref.set(value);
    }

    public void count(String name, String... tagKeyValues) {
        registry.counter(name, tagKeyValues).increment();
    }
}
