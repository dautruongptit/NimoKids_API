package com.nimokids.entity;

import com.nimokids.entity.enums.AgeGroup;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.util.HashMap;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * The wording of a question, independent of topic and answer: "What is this?", "What color is it?" ...
 * All languages live in {@code i18n}: {@code {"vi": {"text": "...", "audio": "..."}, "en": {...}}}.
 *
 * {@code kind} TEMPLATE is a reusable short template; CURATED wraps one hand-written question so every question has a
 * single text source. The language to use is decided only by LanguageResolverService.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "question_templates")
public class QuestionTemplate extends BaseEntity {

    @Column(name = "code", nullable = false, unique = true, length = 100)
    private String code;

    @Builder.Default
    @Column(name = "kind", nullable = false, length = 10)
    private String kind = "TEMPLATE";

    @Column(name = "question_type", length = 30)
    private String questionType;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "age_group", nullable = false, columnDefinition = "age_group")
    private AgeGroup ageGroup = AgeGroup.AGE_1_3;

    @Builder.Default
    @Column(name = "difficulty", nullable = false)
    private Short difficulty = 1;

    @Builder.Default
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "i18n", nullable = false, columnDefinition = "jsonb")
    private Map<String, Map<String, String>> i18n = new HashMap<>();

    @Builder.Default
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "generation", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> generation = new HashMap<>();

    @Builder.Default
    @Column(name = "is_active", nullable = false)
    private boolean active = true;
}
