package com.nimokids.entity.enums;

/**
 * Static roles of the MVP (admin_users.role is a VARCHAR guarded by a CHECK constraint).
 * The name is what goes into the JWT "role" claim; Spring authorities are built as ROLE_ + name.
 * Moving to dynamic RBAC later replaces this enum by tables, while controllers keep their @PreAuthorize rules.
 */
public enum AdminRole {
    SUPER_ADMIN,
    ADMIN
}
