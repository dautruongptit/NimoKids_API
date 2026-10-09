package com.nimokids.entity;

import com.nimokids.entity.enums.LanguageMode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "topics")
public class Topic extends BaseEntity {

    @Column(name = "code", nullable = false, unique = true, length = 50)
    private String code;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "name_vi", length = 100)
    private String nameVi;

    @Column(name = "description_vi", columnDefinition = "TEXT")
    private String descriptionVi;

    public String nameFor(LanguageMode mode) {
        return mode.servesVietnamese() && nameVi != null && !nameVi.isBlank() ? nameVi : name;
    }

    public String descriptionFor(LanguageMode mode) {
        return mode.servesVietnamese() && descriptionVi != null && !descriptionVi.isBlank() ? descriptionVi : description;
    }

    @Column(name = "slug", nullable = false, unique = true, length = 100)
    private String slug;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "icon_url", length = 500)
    private String iconUrl;

    @Column(name = "cover_image_url", length = 500)
    private String coverImageUrl;

    @Column(name = "min_age", nullable = false)
    private Short minAge;

    @Column(name = "max_age", nullable = false)
    private Short maxAge;

    @Builder.Default
    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Builder.Default
    @Column(name = "display_order", nullable = false)
    private Integer displayOrder = 0;

    /** Parent topic (column parent_id), or null for a root. Choosing a root draws questions from its whole subtree. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private Topic parentTopic;

    /** Direct children, read-only side of the tree. Loaded lazily; never walk it to find questions (use the subtree query). */
    @Builder.Default
    @OneToMany(mappedBy = "parentTopic", fetch = FetchType.LAZY)
    @OrderBy("displayOrder ASC")
    private List<Topic> subTopics = new ArrayList<>();
}
