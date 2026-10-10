package com.nimokids.repository;

import com.nimokids.entity.AuthSettings;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuthSettingsRepository extends JpaRepository<AuthSettings, Short> {
}
