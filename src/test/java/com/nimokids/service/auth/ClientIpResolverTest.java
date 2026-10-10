package com.nimokids.service.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class ClientIpResolverTest {

    private final ClientIpResolver resolver =
            new ClientIpResolver(List.of("127.0.0.0/8", "::1/128", "10.0.0.0/8", "172.16.0.0/12", "192.168.0.0/16"), "CF-Connecting-IP");

    private static MockHttpServletRequest request(String socket, String header, String value) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr(socket);
        if (header != null) {
            request.addHeader(header, value);
        }
        return request;
    }

    @Test
    void aTrustedProxyMaySayWhoTheClientIs() {
        assertThat(resolver.resolve(request("172.18.0.5", "CF-Connecting-IP", "113.22.10.4")).getHostAddress()).isEqualTo("113.22.10.4");
        assertThat(resolver.resolve(request("127.0.0.1", "CF-Connecting-IP", "2403:6200:88a0::1")).getHostAddress()).startsWith("2403:6200:88a0");
    }

    @Test
    void anUntrustedSocketCannotChooseItsOwnIp() {
        assertThat(resolver.resolve(request("203.0.113.9", "CF-Connecting-IP", "1.2.3.4")).getHostAddress()).isEqualTo("203.0.113.9");
    }

    @Test
    void xForwardedForIsNeverRead() {
        assertThat(resolver.resolve(request("203.0.113.9", "X-Forwarded-For", "1.2.3.4")).getHostAddress()).isEqualTo("203.0.113.9");
        assertThat(resolver.resolve(request("127.0.0.1", "X-Forwarded-For", "1.2.3.4")).getHostAddress()).isEqualTo("127.0.0.1");
    }

    @Test
    void aGarbageHeaderFromATrustedProxyFallsBackToTheSocketAndNeverTriggersADnsLookup() {
        assertThat(resolver.resolve(request("10.1.2.3", "CF-Connecting-IP", "evil.example.com")).getHostAddress()).isEqualTo("10.1.2.3");
        assertThat(resolver.resolve(request("10.1.2.3", "CF-Connecting-IP", "<script>")).getHostAddress()).isEqualTo("10.1.2.3");
        assertThat(resolver.resolve(request("10.1.2.3", "CF-Connecting-IP", "")).getHostAddress()).isEqualTo("10.1.2.3");
    }

    @Test
    void theFirstOfSeveralValuesIsUsed() {
        assertThat(resolver.resolve(request("10.1.2.3", "CF-Connecting-IP", "113.22.10.4, 10.0.0.1")).getHostAddress()).isEqualTo("113.22.10.4");
    }

    @Test
    void withoutAConfiguredHeaderOnlyTheSocketCounts() {
        ClientIpResolver plain = new ClientIpResolver(List.of("10.0.0.0/8"), "");

        assertThat(plain.resolve(request("10.1.2.3", "CF-Connecting-IP", "1.2.3.4")).getHostAddress()).isEqualTo("10.1.2.3");
    }
}
