package com.flowbinary.platformservice.authz.sdk;

/**
 * The outcome of one check. A denial names the layer that refused (FR-020),
 * so logs, audit and support tooling can say why.
 */
public record Decision(boolean allowed, Layer layer, String privilege, String reason) {

    /** The decision pipeline's layers (design §8). Phase 1 evaluates CONTEXT and PRIVILEGE. */
    public enum Layer {
        CONTEXT,
        PRIVILEGE,
        SCOPE,
        CARE_RELATIONSHIP,
        CREDENTIAL,
        CONSENT
    }

    public static Decision allow(String privilege) {
        return new Decision(true, null, privilege, null);
    }

    public static Decision deny(Layer layer, String privilege, String reason) {
        return new Decision(false, layer, privilege, reason);
    }
}
