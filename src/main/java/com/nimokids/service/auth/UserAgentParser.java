package com.nimokids.service.auth;

/** A small, dependency-free estimate of browser / OS / device type from the User-Agent (it is an estimate only). */
public final class UserAgentParser {

    public record Parsed(String browser, String os, String deviceType) {
    }

    private UserAgentParser() {
    }

    public static Parsed parse(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) {
            return new Parsed(null, null, null);
        }
        String ua = userAgent;
        String browser;
        if (ua.contains("Edg/") || ua.contains("EdgA/") || ua.contains("EdgiOS/")) {
            browser = "Edge";
        } else if (ua.contains("OPR/") || ua.contains("Opera")) {
            browser = "Opera";
        } else if (ua.contains("SamsungBrowser")) {
            browser = "Samsung Internet";
        } else if (ua.contains("Firefox/") || ua.contains("FxiOS/")) {
            browser = "Firefox";
        } else if (ua.contains("Chrome/") || ua.contains("CriOS/")) {
            browser = "Chrome";
        } else if (ua.contains("Safari/")) {
            browser = "Safari";
        } else {
            browser = "Other";
        }
        String os;
        if (ua.contains("Windows")) {
            os = "Windows";
        } else if (ua.contains("Android")) {
            os = "Android";
        } else if (ua.contains("iPhone") || ua.contains("iPad") || ua.contains("iPod")) {
            os = "iOS";
        } else if (ua.contains("Mac OS X") || ua.contains("Macintosh")) {
            os = "macOS";
        } else if (ua.contains("CrOS")) {
            os = "ChromeOS";
        } else if (ua.contains("Linux")) {
            os = "Linux";
        } else {
            os = "Other";
        }
        String device;
        if (ua.contains("iPad") || ua.contains("Tablet") || (ua.contains("Android") && !ua.contains("Mobile"))) {
            device = "TABLET";
        } else if (ua.contains("Mobile") || ua.contains("iPhone") || ua.contains("Android")) {
            device = "MOBILE";
        } else {
            device = "DESKTOP";
        }
        return new Parsed(browser, os, device);
    }
}
