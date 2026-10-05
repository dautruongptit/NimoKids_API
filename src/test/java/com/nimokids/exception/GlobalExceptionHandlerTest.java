package com.nimokids.exception;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nimokids.dto.request.AdminQuestionRequest;
import com.nimokids.dto.request.SubmitAnswerRequest;
import jakarta.validation.Valid;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

class GlobalExceptionHandlerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ProbeController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void businessExceptionUsesStableCodeAndStatus() throws Exception {
        mockMvc.perform(get("/probe/already-answered"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value("ERROR"))
                .andExpect(jsonPath("$.data").doesNotExist())
                .andExpect(jsonPath("$.error.code").value("QUESTION_ALREADY_ANSWERED"))
                .andExpect(jsonPath("$.requestId").isNotEmpty())
                .andExpect(jsonPath("$.timestamp").isNotEmpty());
    }

    @Test
    void sessionNotFoundIsReturnedAs404() throws Exception {
        mockMvc.perform(get("/probe/session-not-found"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("SESSION_NOT_FOUND"));
    }

    @Test
    void bodyValidationListsFieldErrors() throws Exception {
        mockMvc.perform(post("/probe/answer").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value("Validation failed"))
                .andExpect(jsonPath("$.error.details[?(@.field=='selectedOptionId')].message").value("must not be null"))
                .andExpect(jsonPath("$.error.details[?(@.field=='questionId')]").exists());
    }

    @Test
    void clientCannotSendScoreOrCorrectness() throws Exception {
        // Extra fields are ignored by the DTO (it has no isCorrect/score), validation still applies.
        mockMvc.perform(post("/probe/answer").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"isCorrect\":true,\"score\":5}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    void invalidUuidInBodyIsValidationError() throws Exception {
        mockMvc.perform(post("/probe/answer").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"questionId\":\"not-a-uuid\",\"selectedOptionId\":\"x\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value("Malformed request body"));
    }

    @Test
    void invalidUuidInPathIsValidationError() throws Exception {
        mockMvc.perform(get("/probe/sessions/not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.details[0].field").value("sessionId"));
    }

    @Test
    void missingAnonymousIdHeaderMapsToAnonymousPlayerRequired() throws Exception {
        mockMvc.perform(get("/probe/needs-anonymous-id"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("ANONYMOUS_PLAYER_REQUIRED"));
    }

    @Test
    void unknownRouteIsResourceNotFound() throws Exception {
        mockMvc.perform(get("/probe/does-not-exist"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void wrongHttpMethodIsClientError() throws Exception {
        mockMvc.perform(post("/probe/already-answered"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.status").value("ERROR"));
    }

    @Test
    void unexpectedErrorDoesNotLeakInternals() throws Exception {
        mockMvc.perform(get("/probe/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error.code").value("INTERNAL_SERVER_ERROR"))
                .andExpect(jsonPath("$.message").value("An unexpected error occurred"))
                .andExpect(jsonPath("$.error.details").doesNotExist());
    }

    @Test
    void adminQuestionRequiresExactlyOneCorrectOption() throws Exception {
        String twoCorrect = """
                {"topicId":"6f0c1d2e-0000-4000-8000-000000000001","gameModeId":"6f0c1d2e-0000-4000-8000-000000000002",
                 "questionText":"Which animal says Meow?","difficulty":1,"minAge":1,"maxAge":5,
                 "options":[{"text":"Cat","isCorrect":true,"displayOrder":1},
                            {"text":"Dog","isCorrect":true,"displayOrder":2}]}""";
        mockMvc.perform(post("/probe/admin-question").contentType(MediaType.APPLICATION_JSON).content(twoCorrect))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.details[?(@.field=='singleCorrectOption')]").exists());
    }

    @Test
    void adminQuestionValidPayloadPasses() throws Exception {
        String valid = """
                {"topicId":"6f0c1d2e-0000-4000-8000-000000000001","gameModeId":"6f0c1d2e-0000-4000-8000-000000000002",
                 "questionText":"Which animal says Meow?","difficulty":1,"minAge":1,"maxAge":5,
                 "options":[{"text":"Cat","isCorrect":true,"displayOrder":1},
                            {"text":"Dog","isCorrect":false,"displayOrder":2}]}""";
        mockMvc.perform(post("/probe/admin-question").contentType(MediaType.APPLICATION_JSON).content(valid))
                .andExpect(status().isOk());
    }

    @RestController
    static class ProbeController {

        @GetMapping("/probe/already-answered")
        void alreadyAnswered() {
            throw new QuestionAlreadyAnsweredException();
        }

        @GetMapping("/probe/session-not-found")
        void sessionNotFound() {
            throw new SessionNotFoundException();
        }

        @PostMapping("/probe/answer")
        void answer(@Valid @RequestBody SubmitAnswerRequest request) {
        }

        @GetMapping("/probe/sessions/{sessionId}")
        void session(@org.springframework.web.bind.annotation.PathVariable java.util.UUID sessionId) {
        }

        @GetMapping("/probe/needs-anonymous-id")
        void needsAnonymousId(@RequestHeader("X-Anonymous-Id") String anonymousId) {
        }

        @GetMapping("/probe/boom")
        void boom() {
            throw new IllegalStateException("jdbc:postgresql://secret-host/db password=hunter2");
        }

        @PostMapping("/probe/admin-question")
        void adminQuestion(@Valid @RequestBody AdminQuestionRequest request) {
        }
    }
}
