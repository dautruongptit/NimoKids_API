package com.nimokids.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimokids.entity.AnswerItem;
import com.nimokids.entity.GameQuestion;
import com.nimokids.entity.MediaAsset;
import com.nimokids.entity.SnapshotOption;
import com.nimokids.exception.InsufficientDistractorsException;
import com.nimokids.exception.InvalidDistractorRulesException;
import com.nimokids.repository.AnswerItemRepository;
import com.nimokids.service.OptionGenerator;
import com.nimokids.service.generation.DistractorRule;
import com.nimokids.service.generation.DistractorRules;
import com.nimokids.service.generation.TagMatcher;
import com.nimokids.util.GameConstants;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Dynamic answer generation with explicit state matching (project_master_context.md, section 4).
 * Wrong answers come ONLY from positive tag matches ({@code metadata @> match}); the correct item and items already
 * picked are excluded by id, and names are kept distinct so a child never sees two identical words.
 */
@Service
@RequiredArgsConstructor
public class OptionGeneratorImpl implements OptionGenerator {

    /** Candidates fetched per wanted distractor, so duplicates by name can be skipped without a second query. */
    private static final int CANDIDATES_PER_PICK = 8;

    private final AnswerItemRepository answerItemRepository;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional(readOnly = true)
    public List<SnapshotOption> generate(GameQuestion question) {
        AnswerItem correct = question.getCorrectAnswerItem();
        List<DistractorRule> rules = DistractorRules.parse(question.getMetadata(), GameConstants.DISTRACTORS_PER_QUESTION);

        Map<String, Object> correctMatch = DistractorRules.parseCorrectMatch(question.getMetadata());
        if (!TagMatcher.satisfies(correct.getMetadata(), correctMatch)) {
            throw new InvalidDistractorRulesException("Question " + question.getId() + ": the correct answer '"
                    + correct.getName() + "' does not satisfy correct_match " + correctMatch);
        }

        Set<UUID> excludedIds = new LinkedHashSet<>();
        excludedIds.add(correct.getId());
        Set<String> usedNames = new HashSet<>();
        usedNames.add(normalized(correct.getName()));

        List<AnswerItem> distractors = new ArrayList<>();
        for (DistractorRule rule : rules) {
            List<AnswerItem> candidates = answerItemRepository.findRandomMatching(
                    toJson(rule), List.copyOf(excludedIds), rule.count() * CANDIDATES_PER_PICK);
            if (!candidates.isEmpty()) {
                answerItemRepository.findAllWithMedia(candidates.stream().map(AnswerItem::getId).toList());
            }
            int picked = 0;
            for (AnswerItem candidate : candidates) {
                if (picked == rule.count()) {
                    break;
                }
                // Safety net: an item that could also be right is never shown as a wrong answer.
                if (TagMatcher.satisfies(candidate.getMetadata(), correctMatch)) {
                    continue;
                }
                if (usedNames.add(normalized(candidate.getName()))) {
                    distractors.add(candidate);
                    excludedIds.add(candidate.getId());
                    picked++;
                }
            }
            if (picked < rule.count()) {
                throw new InsufficientDistractorsException("Question " + question.getId() + ": rule " + rule.match()
                        + " found " + picked + " usable items but needs " + rule.count());
            }
        }

        List<AnswerItem> all = new ArrayList<>(distractors);
        all.add(correct);
        Collections.shuffle(all, ThreadLocalRandom.current());

        List<SnapshotOption> options = new ArrayList<>(all.size());
        for (int i = 0; i < all.size(); i++) {
            AnswerItem item = all.get(i);
            options.add(new SnapshotOption(
                    UUID.randomUUID(),
                    item.getId(),
                    item.getName(),
                    url(item.getImage()),
                    url(item.getVoice()),
                    i + 1,
                    item.getId().equals(correct.getId())));
        }
        return options;
    }

    private String toJson(DistractorRule rule) {
        try {
            return objectMapper.writeValueAsString(rule.match());
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Cannot serialize distractor rule", ex);
        }
    }

    private static String normalized(String name) {
        return name.trim().toLowerCase(Locale.ROOT);
    }

    private static String url(MediaAsset asset) {
        return asset == null ? null : asset.getStorageUrl();
    }
}
