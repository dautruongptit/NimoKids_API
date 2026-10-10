package com.nimokids.service.auth;

import com.nimokids.util.RequestContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Builds the {@link ClientContext} of an authentication request. */
@Component
@RequiredArgsConstructor
public class ClientContextResolver {

    private static final int MAX_USER_AGENT = 300;

    private final ClientIpResolver ipResolver;
    private final DeviceIdService deviceIdService;

    public ClientContext resolve(HttpServletRequest request, HttpServletResponse response) {
        String userAgent = request.getHeader("User-Agent");
        if (userAgent != null && userAgent.length() > MAX_USER_AGENT) {
            userAgent = userAgent.substring(0, MAX_USER_AGENT);
        }
        UserAgentParser.Parsed parsed = UserAgentParser.parse(userAgent);
        return new ClientContext(
                ipResolver.resolve(request), userAgent, parsed.browser(), parsed.os(), parsed.deviceType(),
                deviceIdService.resolve(request, response), requestId());
    }

    private static UUID requestId() {
        try {
            return UUID.fromString(RequestContext.currentRequestId());
        } catch (RuntimeException ex) {
            return null;
        }
    }
}
