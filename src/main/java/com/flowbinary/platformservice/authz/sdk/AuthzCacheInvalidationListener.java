package com.flowbinary.platformservice.authz.sdk;

import com.flowbinary.platformservice.authz.service.AuthzEvents;
import com.flowbinary.platformservice.event.AuthzChangedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

/**
 * Receives "access changed" on <em>every</em> instance. The consumer group is
 * unique per instance and starts at the latest offset: a broadcast, not shared
 * work. A missed message is harmless because cached sets expire within the TTL.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuthzCacheInvalidationListener {

    private final AuthzEvents events;

    @KafkaListener(
            topics = "${app.kafka.topics.authz-events}",
            groupId = "${spring.application.name}-authz-cache-${random.uuid}",
            properties = "auto.offset.reset=latest",
            containerFactory = "kafkaListenerContainerFactory")
    public void onChanged(@Payload AuthzChangedEvent event, Acknowledgment ack) {
        log.debug("Authz changed: tenant={} user={} reason={}", event.getTenantId(), event.getUserSub(), event.getReason());
        events.apply(event);
        ack.acknowledge();
    }
}
