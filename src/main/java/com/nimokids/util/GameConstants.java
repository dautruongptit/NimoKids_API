package com.nimokids.util;

import java.time.Duration;

/** Business constants of the current MVP version (business-rules.md section 44). */
public final class GameConstants {

    public static final int QUESTIONS_PER_SESSION = 5;
    public static final int MIN_PLAYABLE_QUESTIONS_PER_TOPIC = 5;
    public static final int DEFAULT_TIME_LIMIT_SECONDS = 8;
    public static final int OPTIONS_PER_QUESTION = 4;
    public static final int DISTRACTORS_PER_QUESTION = OPTIONS_PER_QUESTION - 1;

    /** Pause after the feedback audio before the next question appears (frontend behavior, kept for reference). */
    public static final long FEEDBACK_DELAY_MS = 800;

    /**
     * Tolerance added to the server-side deadline to absorb network latency and clock skew between the child's
     * device and the server. The client countdown is only a visual aid.
     */
    public static final long ANSWER_GRACE_MS = 1000;

    /**
     * The countdown starts when the question audio ends, which only the client sees. The server therefore never
     * trusts the reported start blindly: it cannot be later than "question presented + audio length + this tolerance".
     */
    public static final long TIMER_START_TOLERANCE_MS = 3000;

    /**
     * Questions after the first are handed to the client together with the previous answer, then the client plays
     * the feedback audio and waits {@link #FEEDBACK_DELAY_MS} before showing them. This is the time we allow for that.
     */
    public static final long FEEDBACK_ALLOWANCE_MS = 4000;

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
