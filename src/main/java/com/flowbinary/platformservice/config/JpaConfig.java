package com.flowbinary.platformservice.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * Activates Spring Data JPA auditing.
 *
 * <p>{@code BaseEntity} has always carried {@code @CreatedDate},
 * {@code @LastModifiedDate} and {@code @EntityListeners(AuditingEntityListener.class)},
 * but without this the auditing infrastructure is never bootstrapped and those
 * fields are left null — entities that needed timestamps had to set them by hand.
 * The RBAC and menu tables declare {@code created_at} / {@code updated_at} as
 * NOT NULL, so auditing has to actually run.
 *
 * <p>No {@code AuditorAware} bean is needed: no entity uses {@code @CreatedBy}
 * or {@code @LastModifiedBy} — the {@code created_by} / {@code updated_by}
 * columns are set explicitly by the services that write them.
 */
@Configuration
@EnableJpaAuditing
public class JpaConfig {
}
