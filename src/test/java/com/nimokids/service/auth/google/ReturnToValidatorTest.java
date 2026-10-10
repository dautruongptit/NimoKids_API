package com.nimokids.service.auth.google;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ReturnToValidatorTest {

    @Test
    void relativePathsOfTheSiteAreAccepted() {
        for (String ok : new String[]{null, "", "  ", "/", "/topics", "/age", "/quiz?topic=3&x=1", "/auth/complete", "/a-b_c.d/e~f"}) {
            assertThat(ReturnToValidator.isValid(ok)).as(String.valueOf(ok)).isTrue();
        }
    }

    @Test
    void anythingThatCouldLeaveTheSiteIsRefused() {
        String[] bad = {"//evil.com", "///evil.com", "https://evil.com", "http://evil.com/x", "javascript:alert(1)", "evil.com",
                "/\\evil.com", "\\\\evil.com", "/%2F%2Fevil.com", "/..%2f..", "/../etc/passwd", "/a\nb", "/a b", "/<script>",
                "/" + "a".repeat(250), "/x://y"};
        for (String value : bad) {
            assertThat(ReturnToValidator.isValid(value)).as(value).isFalse();
        }
    }

    @Test
    void theDefaultIsTheHomePage() {
        assertThat(ReturnToValidator.orDefault(null)).isEqualTo("/");
        assertThat(ReturnToValidator.orDefault(" ")).isEqualTo("/");
        assertThat(ReturnToValidator.orDefault("/topics")).isEqualTo("/topics");
    }

    @Test
    void pkceChallengeFollowsRfc7636() {
        // the worked example of RFC 7636 appendix B
        assertThat(Pkce.challenge("dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk")).isEqualTo("E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM");
        assertThat(Pkce.newVerifier()).hasSizeBetween(43, 128);
    }
}
