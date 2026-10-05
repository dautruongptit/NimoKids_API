package com.nimokids.util;

import java.time.Duration;

/** Business constants of the current MVP version (business-rules.md section 44). */
public final class GameConstants {

    public static final int QUESTIONS_PER_SESSION = 5;
    public static final int MIN_PLAYABLE_QUESTIONS_PER_TOPIC = 5;
    public static final int DEFAULT_TIME_LIMIT_SECONDS = 5;

    /** Time the client shows feedback before the next question appears. */
    public static final long FEEDBACK_DELAY_MS = 800;

    /**
     * Tolerance added to the server-side deadline to absorb network latency and clock skew between
     * the child's device and the server. The client countdown is only a visual aid.
     */
    public static final long ANSWER_GRACE_MS = 1000;

    /** STARTED sessions without activity for this long become ABANDONED (master 5.11). */
    public static final Duration SESSION_INACTIVITY_TIMEOUT = Duration.ofMinutes(30);

    /** Questions of this game mode must have an object sound to be playable (business-rules section 32). */
    public static final String ANIMAL_SOUND_MODE_CODE = "ANIMAL_SOUND";

    public static final String STICKER_FIRST_GAME = "FIRST_GAME";
    public static final String STICKER_PERFECT_SCORE = "PERFECT_SCORE";

    public static final String FEEDBACK_CORRECT = "Great job!";
    public static final String FEEDBACK_WRONG = "Try again!";
    public static final String FEEDBACK_TIMEOUT = "Oops! Time's Up!";

    private GameConstants() {
    }
}
