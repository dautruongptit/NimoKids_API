package com.nimokids.service.auth.google;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Google OAuth 2.0 / OpenID Connect client (docs 06-authentication). Everything secret comes from the environment:
 * {@code GOOGLE_CLIENT_ID}, {@code GOOGLE_CLIENT_SECRET}, {@code GOOGLE_REDIRECT_URI}. With no client id the Google login
 * is simply switched off. The endpoint addresses are properties only so that tests can point them at a local stub.
 */
@ConfigurationProperties(prefix = "app.google")
public record GoogleOidcProperties(
        @DefaultValue("") String clientId,
        @DefaultValue("") String clientSecret,
        @DefaultValue("") String redirectUri,
        @DefaultValue("https://accounts.google.com/o/oauth2/v2/auth") String authorizationEndpoint,
        @DefaultValue("https://oauth2.googleapis.com/token") String tokenEndpoint,
        @DefaultValue("https://www.googleapis.com/oauth2/v3/certs") String jwksUri,
        @DefaultValue("5") int httpTimeoutSeconds) {

    public boolean enabled() {
        return !clientId.isBlank() && !clientSecret.isBlank() && !redirectUri.isBlank();
    }
}
