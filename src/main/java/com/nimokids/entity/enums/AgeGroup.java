package com.nimokids.entity.enums;

import java.util.List;

/**
 * Age groups defined in startGame.md and Rules Creat question.md.
 * AGE_4_5 inherits AGE_1_3 questions (40 % easy from AGE_1_3 + 60 % hard from AGE_4_5).
 */
public enum AgeGroup {

    AGE_1_3,
    AGE_4_5;

    /**
     * The groups whose questions are available to a player in this group.
     * AGE_1_3 sees only its own; AGE_4_5 sees both (inheritance).
     */
    public List<AgeGroup> inheritedGroups() {
        return switch (this) {
            case AGE_1_3 -> List.of(AGE_1_3);
            case AGE_4_5 -> List.of(AGE_1_3, AGE_4_5);
        };
    }

    /** The groups whose names are passed to a native SQL query (PostgreSQL enum literals). */
    public List<String> inheritedGroupNames() {
        return inheritedGroups().stream().map(Enum::name).toList();
    }
}
