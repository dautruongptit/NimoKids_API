package com.nimokids.service;

import com.nimokids.entity.enums.LanguageMode;
import com.nimokids.service.language.QuestionInstance;
import com.nimokids.service.language.ResolvedQuestion;

/**
 * The ONLY place where languages are chosen. The question generator produces a language-neutral
 * {@link QuestionInstance}; this service runs as the last step before the session question is saved and maps it:
 *
 * <ul>
 *   <li>{@code VI}    - template and answer items from {@code i18n.vi}</li>
 *   <li>{@code EN}    - template and answer items from {@code i18n.en}</li>
 *   <li>{@code VI_EN} - template (question / instruction) from {@code i18n.vi}, answer items from {@code i18n.en}</li>
 * </ul>
 *
 * A missing translation falls back to English, then to the canonical name / text stored on the row, so a child never
 * sees an empty button.
 */
public interface LanguageResolverService {

    ResolvedQuestion resolve(LanguageMode languageMode, QuestionInstance instance);
}
