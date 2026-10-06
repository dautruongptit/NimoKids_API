package com.nimokids.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
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
 * One entry of the answer vocabulary. Every option shown to a child is generated from these rows.
 *
 * {@code metadata} holds EXPLICIT positive tags such as {"category":"animal","can_fly":false}. Wrong answers are
 * found with the JSONB containment operator (@>) over these tags, never by negation, so an item with an incomplete
 * tag set simply never matches a rule instead of leaking into the wrong pool.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "answer_items")
public class AnswerItem extends BaseEntity {

    @Column(name = "code", nullable = false, unique = true, length = 100)
    private String code;

    /** The word shown and spoken, e.g. "Bird". */
    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "image_id")
    private MediaAsset image;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "voice_id")
    private MediaAsset voice;

    @Builder.Default
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> metadata = new HashMap<>();

    @Builder.Default
    @Column(name = "is_active", nullable = false)
    private boolean active = true;
}
