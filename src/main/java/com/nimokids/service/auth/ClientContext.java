package com.nimokids.service.auth;

import java.net.InetAddress;
import java.util.UUID;

/**
 * What the server knows about the caller of an authentication request: the IP resolved through the trusted-proxy rule,
 * the parsed user agent and the hashed device id. {@code newDeviceCookie} is the value to set when the browser had no
 * device cookie yet.
 */
public record ClientContext(
        InetAddress ip,
        String userAgent,
        String browser,
        String os,
        String deviceType,
        byte[] deviceIdHash,
        UUID requestId) {

    public static ClientContext unknown() {
        return new ClientContext(null, null, null, null, null, null, null);
    }
}
