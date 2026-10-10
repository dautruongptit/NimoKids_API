package com.nimokids.service.auth.google;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** The back-channel code exchange: authorization code + PKCE verifier + client secret -> ID token. */
@Slf4j
@Component
public class GoogleTokenClient {

    /** Thrown when Google cannot be reached or refuses the code. The message never carries the code or any token. */
    public static class ExchangeFailedException extends Exception {
        public ExchangeFailedException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    private final GoogleOidcProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient http;

    public GoogleTokenClient(GoogleOidcProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(Math.max(1, properties.httpTimeoutSeconds()))).build();
    }

    public String exchange(String code, String codeVerifier) throws ExchangeFailedException {
        String form = "grant_type=authorization_code"
                + "&code=" + enc(code)
                + "&client_id=" + enc(properties.clientId())
                + "&client_secret=" + enc(properties.clientSecret())
                + "&redirect_uri=" + enc(properties.redirectUri())
                + "&code_verifier=" + enc(codeVerifier);
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(properties.tokenEndpoint()))
                    .timeout(Duration.ofSeconds(Math.max(1, properties.httpTimeoutSeconds())))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(form)).build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new ExchangeFailedException("Google token endpoint answered HTTP " + response.statusCode(), null);
            }
            JsonNode body = objectMapper.readTree(response.body());
            String idToken = body.path("id_token").asText("");
            if (idToken.isBlank()) {
                throw new ExchangeFailedException("Google answered without an id_token", null);
            }
            return idToken;
        } catch (IOException | RuntimeException ex) {
            throw new ExchangeFailedException("Could not reach Google", ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new ExchangeFailedException("Interrupted", ex);
        }
    }

    private static String enc(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
