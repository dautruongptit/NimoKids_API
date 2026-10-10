package com.nimokids.service.auth;

import jakarta.servlet.http.HttpServletRequest;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.web.util.matcher.IpAddressMatcher;
import org.springframework.stereotype.Component;

/**
 * The only place that decides which IP is "the client". The socket address is the truth; the forwarding header
 * (CF-Connecting-IP by default) is believed ONLY when the socket belongs to a trusted proxy (loopback and the private
 * ranges of the docker network by default). A client that reaches the API directly cannot choose its own IP, and
 * X-Forwarded-For is never read.
 */
@Component
public class ClientIpResolver {

    private static final Pattern IP_LITERAL = Pattern.compile("^[0-9a-fA-F:.]{2,45}$");

    private final List<IpAddressMatcher> trusted;
    private final String header;

    public ClientIpResolver(
            @Value("${app.auth.trusted-proxies:127.0.0.0/8,::1/128,10.0.0.0/8,172.16.0.0/12,192.168.0.0/16}") List<String> trustedProxies,
            @Value("${app.api-log.client-ip-header:CF-Connecting-IP}") String header) {
        this.trusted = trustedProxies.stream().map(String::trim).filter(s -> !s.isEmpty()).map(IpAddressMatcher::new).toList();
        this.header = header == null ? "" : header.trim();
    }

    public InetAddress resolve(HttpServletRequest request) {
        String socket = request.getRemoteAddr();
        if (!header.isEmpty() && isTrusted(socket)) {
            String forwarded = request.getHeader(header);
            if (forwarded != null && !forwarded.isBlank()) {
                InetAddress fromHeader = parse(forwarded.split(",")[0].trim());
                if (fromHeader != null) {
                    return fromHeader;
                }
            }
        }
        return parse(socket);
    }

    boolean isTrusted(String address) {
        if (address == null || !IP_LITERAL.matcher(address).matches()) {
            return false;
        }
        for (IpAddressMatcher matcher : trusted) {
            try {
                if (matcher.matches(address)) {
                    return true;
                }
            } catch (IllegalArgumentException ex) {
                // not an address of the same family as the rule: keep looking
            }
        }
        return false;
    }

    static InetAddress parse(String value) {
        if (value == null || !IP_LITERAL.matcher(value).matches()) {
            return null;
        }
        try {
            return InetAddress.getByName(value);   // a literal: no DNS lookup
        } catch (UnknownHostException ex) {
            return null;
        }
    }
}
