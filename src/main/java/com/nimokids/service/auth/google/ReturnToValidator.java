package com.nimokids.service.auth.google;

import java.util.regex.Pattern;

/**
 * "Where to go after signing in" must be a path of THIS site. Anything with a scheme, a host, a protocol-relative
 * "//", a backslash or a control character is refused, so the login can never become an open redirect.
 */
public final class ReturnToValidator {

    private static final Pattern SAFE_PATH = Pattern.compile("^/[A-Za-z0-9/_\\-.~%?=&+,:@!$*()']{0,198}$");

    private ReturnToValidator() {
    }

    /** True for null/blank (the default "/" is used) and for a safe relative path. */
    public static boolean isValid(String returnTo) {
        if (returnTo == null || returnTo.isBlank()) {
            return true;
        }
        if (returnTo.startsWith("//") || returnTo.contains("\\") || returnTo.contains("..") || returnTo.contains("://")) {
            return false;
        }
        String decoded = returnTo.replace("%2f", "/").replace("%2F", "/").replace("%5c", "\\").replace("%5C", "\\");
        if (decoded.startsWith("//") || decoded.contains("\\")) {
            return false;
        }
        return SAFE_PATH.matcher(returnTo).matches();
    }

    public static String orDefault(String returnTo) {
        return returnTo == null || returnTo.isBlank() ? "/" : returnTo;
    }
}
