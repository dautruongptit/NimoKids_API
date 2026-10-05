package com.nimokids.repository;

import com.nimokids.entity.SessionQuestion;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SessionQuestionRepository extends JpaRepository<SessionQuestion, UUID> {
}
