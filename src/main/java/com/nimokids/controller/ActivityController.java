package com.nimokids.controller;

import com.nimokids.dto.request.ActivityBatchRequest;
import com.nimokids.dto.response.ApiResponse;
import com.nimokids.service.ActivityLogService;
import com.nimokids.validation.AnonymousId;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/activities")
@RequiredArgsConstructor
public class ActivityController {

    private final ActivityLogService activityLogService;

    @PostMapping("/batch")
    public ApiResponse<Void> recordBatch(
            @AnonymousId UUID anonymousId, @Valid @RequestBody ActivityBatchRequest request) {
        activityLogService.recordBatch(anonymousId, request);
        return ApiResponse.success("Activities recorded", null);
    }
}
