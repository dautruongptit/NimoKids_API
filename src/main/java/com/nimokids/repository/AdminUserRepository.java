package com.nimokids.repository;

import com.nimokids.entity.AdminUser;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface AdminUserRepository extends JpaRepository<AdminUser, UUID> {

    /** Case-insensitive lookup that matches the unique index on lower(email). */
    @Query("select u from AdminUser u where lower(u.email) = lower(:email)")
    Optional<AdminUser> findByEmailIgnoreCase(String email);
}
