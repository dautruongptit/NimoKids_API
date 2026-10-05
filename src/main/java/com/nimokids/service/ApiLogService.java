package com.nimokids.service;

import com.nimokids.logging.ApiLogEvent;

public interface ApiLogService {

    /**
     * Persists one API log row asynchronously, so the request thread never waits for the database.
     * Never throws: a logging failure must not affect the API response.
     */
    void record(ApiLogEvent event);
}
