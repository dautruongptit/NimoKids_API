package com.nimokids.service.auth;

import com.nimokids.security.JwtProperties;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.regex.Pattern;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Service;

/**
 * Recognises a browser across logins with a random cookie (nk_did). Only an HMAC of its value is stored, so the
 * database never holds something that could be replayed as a cookie. A device id is NOT an authentication factor: it
 * only tells "seen before / new device".
 */
@Service
public class DeviceIdService {

    public static final String COOKIE_NAME = "nk_did";
    private static final Pattern VALID_VALUE = Pattern.compile("^[A-Za-z0-9_-]{22}$");
    private static final int ONE_YEAR_SECONDS = 365 * 24 * 3600;

    private final SecureRandom random = new SecureRandom();
    private final SecretKeySpec hmacKey;
    private final AuthProperties properties;

    public DeviceIdService(JwtProperties jwtProperties, AuthProperties properties) {
        // A key derived from the JWT secret, so no extra secret has to be configured.
        byte[] secret = Base64.getDecoder().decode(jwtProperties.secret());
        this.hmacKey = new SecretKeySpec(hmac(secret, "nimokids-device-id"), "HmacSHA256");
        this.properties = properties;
    }

    /** Returns the hash of the browser's device id, creating the cookie on the response when there is none. */
    public byte[] resolve(HttpServletRequest request, HttpServletResponse response) {
        String value = null;
        if (request.getCookies() != null) {
            for (Cookie cookie : request.getCookies()) {
                if (COOKIE_NAME.equals(cookie.getName()) && VALID_VALUE.matcher(cookie.getValue()).matches()) {
                    value = cookie.getValue();
                    break;
                }
            }
        }
        if (value == null) {
            byte[] bytes = new byte[16];
            random.nextBytes(bytes);
            value = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
            if (response != null) {
                response.addHeader("Set-Cookie", COOKIE_NAME + "=" + value + "; Max-Age=" + ONE_YEAR_SECONDS
                        + "; Path=/; HttpOnly; SameSite=Lax" + (properties.cookieSecure() ? "; Secure" : ""));
            }
        }
        return hash(value);
    }

    byte[] hash(String value) {
        return hmac(hmacKey.getEncoded(), value);
    }

    private static byte[] hmac(byte[] key, String message) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(message.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("HmacSHA256 is not available", ex);
        }
    }
}
