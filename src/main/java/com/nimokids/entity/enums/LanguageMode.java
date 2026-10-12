package com.nimokids.entity.enums;

/**
 * How a child plays, chosen on the website and sent with POST /game-sessions.
 *
 * <ul>
 *   <li>{@link #EN}    - full English (UI, questions, answers). Not offered in the UI yet, kept for later.</li>
 *   <li>{@link #VI}    - full Vietnamese: questions, answers, topic names and feedback are served in Vietnamese.</li>
 *   <li>{@link #VI_EN} - "learn English" (recommended): Vietnamese interface, English question and English
 *                        answers; the question also carries its Vietnamese text as a subtitle
 *                        ({@code questionTextVi}). Resolved by LanguageResolverService.</li>
 * </ul>
 */
public enum LanguageMode {

    EN,
    VI,
    VI_EN;

    /** Language of the question template and the feedback: Vietnamese for VI only; EN and VI_EN ask in English. */
    public String instructionLanguage() {
        return this == VI ? "vi" : "en";
    }

    /** Language of the answer items: Vietnamese for VI only; VI_EN keeps English answers (learn English). */
    public String answerLanguage() {
        return this == VI ? "vi" : "en";
    }

    /**
     * True when the mode only uses basic English vocabulary ({@code AnswerItem.basicEnglish}): VI_EN, where a child
     * hears Vietnamese and picks English, is kept simple on purpose. Which topics a mode offers is data, see
     * {@code Topic.supportedModes}.
     */
    public boolean basicEnglishOnly() {
        return this == VI_EN;
    }

    /** True when topics (which are not part of the question/answer i18n) are served in Vietnamese. */
    public boolean servesVietnamese() {
        return this == VI;
    }
}
