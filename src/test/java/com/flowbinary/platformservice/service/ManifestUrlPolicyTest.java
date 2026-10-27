package com.flowbinary.platformservice.service;

import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

class ManifestUrlPolicyTest {

    private static Function<String, InetAddress[]> resolvesTo(String ip) {
        return host -> {
            try {
                return new InetAddress[]{InetAddress.getByName(ip)};
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        };
    }

    private static String refusal(String url, String ip) {
        return ManifestUrlPolicy.check(url, false, false, resolvesTo(ip)).orElse(null);
    }

    @Test
    void publicHttpsHostIsAllowed() {
        assertThat(refusal("https://billing.example.org/menu", "93.184.216.34")).isNull();
    }

    @Test
    void privateAndLocalAddressesAreRefused() {
        for (String ip : new String[]{"127.0.0.1", "10.1.2.3", "172.16.0.9", "192.168.1.1", "169.254.169.254", "0.0.0.0", "::1", "fd00::1", "fe80::1"}) {
            assertThat(refusal("https://svc.example.org/m", ip)).as(ip).contains("private or local");
        }
    }

    @Test
    void oneBadAddressAmongPublicOnesRefusesTheHost() {
        Function<String, InetAddress[]> mixed = h -> {
            try {
                return new InetAddress[]{InetAddress.getByName("93.184.216.34"), InetAddress.getByName("10.0.0.1")};
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        };
        assertThat(ManifestUrlPolicy.check("https://x.example.org", false, false, mixed)).isPresent();
    }

    @Test
    void schemeAndStructureAreChecked() {
        assertThat(refusal("http://svc.example.org/m", "93.184.216.34")).contains("https");
        assertThat(refusal("ftp://svc.example.org/m", "93.184.216.34")).contains("https");
        assertThat(refusal("https://user:pw@svc.example.org/m", "93.184.216.34")).contains("credentials");
        assertThat(refusal("not a url", "93.184.216.34")).isNotNull();
        assertThat(refusal("https:///nohost", "93.184.216.34")).isNotNull();
    }

    @Test
    void unresolvableHostIsRefused() {
        Function<String, InetAddress[]> boom = h -> {
            throw new IllegalArgumentException("nxdomain");
        };
        assertThat(ManifestUrlPolicy.check("https://nope.example.org", false, false, boom)).isPresent();
    }

    @Test
    void devFlagsRelaxOnlyWhatTheyName() {
        assertThat(ManifestUrlPolicy.check("http://localhost:9000/m", true, true, resolvesTo("127.0.0.1"))).isEmpty();
        assertThat(ManifestUrlPolicy.check("http://localhost:9000/m", true, false, resolvesTo("127.0.0.1"))).isPresent();   // still needs https
        assertThat(ManifestUrlPolicy.check("https://localhost:9000/m", false, true, resolvesTo("127.0.0.1"))).isPresent();  // still private
    }
}
