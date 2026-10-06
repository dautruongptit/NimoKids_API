package com.nimokids.controller.admin;

import com.nimokids.dto.response.ApiResponse;
import com.nimokids.security.AdminPrincipal;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * DRAFT controller: it only shows how every admin API is protected.
 *
 * <ul>
 *   <li>The URL rule (SecurityConfig) already makes /api/v1/admin/** require a valid JWT (401 otherwise).</li>
 *   <li>The role needed for each API is declared on the method with {@code @PreAuthorize} (403 if the role is not enough).</li>
 *   <li>Every handler of an admin controller MUST carry a @PreAuthorize rule; AdminControllersSecurityTest fails if one does not.</li>
 * </ul>
 * The rule may later become {@code hasAuthority('topic:write')} when roles turn into dynamic RBAC: only the
 * annotation changes, not the controller logic.
 */
@RestController
@RequestMapping("/api/v1/admin/topics")
public class AdminTopicController {

    public record DraftResponse(String note, String callerId, String callerRole, List<String> topics) {
    }

    @GetMapping
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ApiResponse<DraftResponse> listTopics(@AuthenticationPrincipal AdminPrincipal admin) {
        return ApiResponse.success(new DraftResponse(
                "Draft endpoint: real admin topic management comes later",
                admin.userId(),
                admin.role(),
                List.of()));
    }
}
