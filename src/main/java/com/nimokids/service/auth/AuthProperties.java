package com.nimokids.service.auth;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Settings of the session system that depend on the deployment (everything about the policy itself is in
 * auth_settings).
 *
 * @param cookieSecure   the Secure flag (and the __Secure- name prefix) of the cookies. true in production; false for
 *                       plain http during local development
 * @param allowedOrigins origins allowed to call the cookie endpoints (refresh / logout); empty = same origin only
 * @param webUrl         public address of the website; the Google callback redirects the browser back to it
 */
@ConfigurationProperties(prefix = "app.auth")
public record AuthProperties(
        @DefaultValue("true") boolean cookieSecure,
        @DefaultValue({}) List<String> allowedOrigins,
        @DefaultValue("") String webUrl) {

    public String refreshCookieName() {
        return cookieSecure ? "__Secure-nk_rt" : "nk_rt";
    }
}
