package com.nimokids.service.language;

import com.nimokids.entity.SnapshotOption;
import com.nimokids.entity.enums.LanguageMode;
import java.util.List;

/**
 * A question in the language of the session: what is stored in {@code session_questions} (options and snapshot) and
 * later shown to the child.
 *
 * @param questionText        the question / instruction in {@code instructionLanguage}
 * @param questionTextVi      Vietnamese text of the question, shown as a subtitle in VI_EN only (else null)
 * @param questionAudioUrl    its audio, or null (the browser voice is used)
 * @param options             the 4 options with text and audio in {@code answerLanguage}
 * @param instructionLanguage "vi" or "en": language of the template (question, feedback)
 * @param answerLanguage      "vi" or "en": language of the answer items
 */
public record ResolvedQuestion(
        String questionText,
        String questionTextVi,
        String questionAudioUrl,
        List<SnapshotOption> options,
        LanguageMode languageMode,
        String instructionLanguage,
        String answerLanguage) {
}
