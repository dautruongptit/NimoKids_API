package com.nimokids.service.auth;

import java.net.Inet4Address;
import java.net.InetAddress;

/** Shortened IP for lists and for users: IPv4 a.b.x.x, IPv6 first two groups. The full value is for detail views only. */
public final class IpMasker {

    private IpMasker() {
    }

    public static String mask(InetAddress ip) {
        if (ip == null) {
            return null;
        }
        if (ip instanceof Inet4Address) {
            String[] parts = ip.getHostAddress().split("\\.");
            return parts.length == 4 ? parts[0] + "." + parts[1] + ".x.x" : "x.x.x.x";
        }
        String[] groups = ip.getHostAddress().split(":");
        return groups.length >= 2 ? groups[0] + ":" + groups[1] + "::x" : "x::x";
    }

    /** jo***@gmail.com (never the full local part). */
    public static String maskEmail(String email) {
        if (email == null || email.isBlank()) {
            return null;
        }
        int at = email.indexOf('@');
        if (at <= 0) {
            return "***";
        }
        String local = email.substring(0, at);
        String visible = local.length() <= 2 ? local.substring(0, 1) : local.substring(0, 2);
        return visible + "***" + email.substring(at);
    }
}
