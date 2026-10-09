package com.nimokids.entity.enums;

/**
 * How a child plays, chosen on the website and sent with POST /game-sessions.
 *
 * <ul>
 *   <li>{@link #EN}    - full English (UI, questions, answers). Not offered in the UI yet, kept for later.</li>
 *   <li>{@link #VI}    - full Vietnamese: questions, answers, topic names and feedback are served in Vietnamese.</li>
 *   <li>{@link #VI_EN} - Vietnamese interface, but the questions and answers stay English (the recommended
 *                        "learn English" mode). The content is identical to {@link #EN}; only the website
 *                        interface differs, so the server serves English text.</li>
 * </ul>
 */
public enum LanguageMode {

    EN,
    VI,
    VI_EN;

    /** True when the content (questions, answers, topics, feedback) must be served in Vietnamese. */
    public boolean servesVietnamese() {
        return this == VI;
    }
}
