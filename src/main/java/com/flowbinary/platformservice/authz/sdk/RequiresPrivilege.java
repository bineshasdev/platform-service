package com.flowbinary.platformservice.authz.sdk;

import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.annotation.*;

/**
 * Guards a method with one privilege, checked by the {@code authz} bean
 * through every implemented layer of the decision pipeline:
 *
 * <pre>{@code
 * @RequiresPrivilege("platform.role.manage")
 * public RoleResponse create(...) { ... }
 * }</pre>
 *
 * The {@code {value}} placeholder is filled in by Spring Security's annotation
 * templates ({@link AuthzSdkConfig}).
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@PreAuthorize("@authz.has(authentication, '{value}')")
public @interface RequiresPrivilege {

    /** The privilege code, {@code <service>.<resource>.<action>}. */
    String value();
}
