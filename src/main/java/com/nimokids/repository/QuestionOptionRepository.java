package com.nimokids.repository;

import com.nimokids.entity.QuestionOption;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QuestionOptionRepository extends JpaRepository<QuestionOption, UUID> {
}
