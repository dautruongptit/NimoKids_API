package com.nimokids.service.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.InetAddress;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AuthHelpersTest {

    @Test
    void ipsAreMaskedForLists() throws Exception {
        assertThat(IpMasker.mask(InetAddress.getByName("113.22.10.4"))).isEqualTo("113.22.x.x");
        assertThat(IpMasker.mask(InetAddress.getByName("2403:6200:88a0:1::7"))).isEqualTo("2403:6200::x");
        assertThat(IpMasker.mask(null)).isNull();
    }

    @Test
    void emailsAreMaskedButStillRecognisable() {
        assertThat(IpMasker.maskEmail("john.doe@gmail.com")).isEqualTo("jo***@gmail.com");
        assertThat(IpMasker.maskEmail("a@x.io")).isEqualTo("a***@x.io");
        assertThat(IpMasker.maskEmail("not-an-email")).isEqualTo("***");
        assertThat(IpMasker.maskEmail(" ")).isNull();
    }

    @Test
    void secretsNeverReachTheAuditMetadata() {
        Map<String, Object> raw = new HashMap<>();
        raw.put("refreshToken", "abc");
        raw.put("Authorization", "Bearer x");
        raw.put("password", "p");
        raw.put("code_verifier", "v");
        raw.put("note", "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxMjM0NTY3ODkwIn0.dBjftJeZ4CVPmB92K27uhbUJU1p1r_wW1gFWFOEjXk");
        raw.put("opaque", "A".repeat(43));
        raw.put("reason", "stolen laptop");
        raw.put("count", 3);
        raw.put("nested", Map.of("cookie", "c", "kept", "yes"));

        Map<String, Object> clean = MetadataSanitizer.clean(raw);

        assertThat(clean).containsOnlyKeys("reason", "count", "nested");
        assertThat(clean.get("nested")).isEqualTo(Map.of("kept", "yes"));
    }

    @Test
    void refreshTokensAreRandomLongEnoughAndOnlyTheirHashIsComparable() {
        String a = TokenHasher.newRawToken();
        String b = TokenHasher.newRawToken();

        assertThat(a).hasSize(43).isNotEqualTo(b);
        assertThat(TokenHasher.looksValid(a)).isTrue();
        assertThat(TokenHasher.looksValid("short")).isFalse();
        assertThat(TokenHasher.looksValid(null)).isFalse();
        assertThat(TokenHasher.looksValid(a + "!")).isFalse();
        assertThat(TokenHasher.sha256(a)).hasSize(32).isEqualTo(TokenHasher.sha256(a)).isNotEqualTo(TokenHasher.sha256(b));
    }

    @Test
    void userAgentsAreParsedApproximately() {
        UserAgentParser.Parsed chrome = UserAgentParser.parse("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/120.0 Safari/537.36");
        UserAgentParser.Parsed iphone = UserAgentParser.parse("Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 Version/17.0 Mobile/15E148 Safari/604.1");
        UserAgentParser.Parsed edge = UserAgentParser.parse("Mozilla/5.0 (Windows NT 10.0) Chrome/120.0 Safari/537.36 Edg/120.0");
        UserAgentParser.Parsed android = UserAgentParser.parse("Mozilla/5.0 (Linux; Android 14; Pixel 8) Chrome/120.0 Mobile Safari/537.36");

        assertThat(chrome).isEqualTo(new UserAgentParser.Parsed("Chrome", "Windows", "DESKTOP"));
        assertThat(iphone).isEqualTo(new UserAgentParser.Parsed("Safari", "iOS", "MOBILE"));
        assertThat(edge.browser()).isEqualTo("Edge");
        assertThat(android).isEqualTo(new UserAgentParser.Parsed("Chrome", "Android", "MOBILE"));
        assertThat(UserAgentParser.parse(null)).isEqualTo(new UserAgentParser.Parsed(null, null, null));
        assertThat(UserAgentParser.parse("???").browser()).isEqualTo("Other");
    }
}
