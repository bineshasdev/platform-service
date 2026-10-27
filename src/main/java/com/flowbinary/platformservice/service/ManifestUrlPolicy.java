package com.flowbinary.platformservice.service;

import java.net.InetAddress;
import java.net.URI;
import java.util.Optional;
import java.util.function.Function;

/**
 * Decides whether the platform may fetch a module's manifest from a URL. An
 * operator registers the URL, but the server still must not become a way to
 * reach internal services: by default only https URLs whose host resolves
 * exclusively to public addresses are allowed.
 *
 * <p>Known limit: the check resolves the host and the HTTP client resolves it
 * again, so a hostile DNS server could answer differently (rebinding). The
 * mitigation is that only operators can set the URL and redirects are refused.
 */
public final class ManifestUrlPolicy {

    private ManifestUrlPolicy() {
    }

    /** @return the reason the URL is refused, or empty when it is allowed */
    public static Optional<String> check(String url, boolean allowPrivateHosts, boolean allowInsecureHttp,
                                         Function<String, InetAddress[]> resolver) {
        URI uri;
        try {
            uri = URI.create(url);
        } catch (IllegalArgumentException e) {
            return Optional.of("manifestUrl is not a valid URL");
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase();
        if (!scheme.equals("https") && !(scheme.equals("http") && allowInsecureHttp)) {
            return Optional.of("manifestUrl must use https");
        }
        if (uri.getHost() == null || uri.getUserInfo() != null) {
            return Optional.of("manifestUrl must have a host and no credentials");
        }
        if (allowPrivateHosts) return Optional.empty();

        InetAddress[] addresses;
        try {
            addresses = resolver.apply(uri.getHost());
        } catch (RuntimeException e) {
            return Optional.of("manifestUrl host cannot be resolved");
        }
        if (addresses == null || addresses.length == 0) return Optional.of("manifestUrl host cannot be resolved");
        for (InetAddress a : addresses) {
            if (a.isLoopbackAddress() || a.isAnyLocalAddress() || a.isLinkLocalAddress()
                    || a.isSiteLocalAddress() || a.isMulticastAddress() || isUniqueLocalV6(a)) {
                return Optional.of("manifestUrl resolves to a private or local address");
            }
        }
        return Optional.empty();
    }

    /** fc00::/7 — IPv6 unique local addresses, which {@code isSiteLocalAddress} does not cover. */
    private static boolean isUniqueLocalV6(InetAddress a) {
        byte[] b = a.getAddress();
        return b.length == 16 && (b[0] & 0xFE) == 0xFC;
    }
}
